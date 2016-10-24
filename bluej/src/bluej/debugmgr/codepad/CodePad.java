/*
 This file is part of the BlueJ program. 
 Copyright (C) 1999-2009,2010,2011,2012,2013,2016  Michael Kolling and John Rosenberg
 
 This program is free software; you can redistribute it and/or 
 modify it under the terms of the GNU General Public License 
 as published by the Free Software Foundation; either version 2 
 of the License, or (at your option) any later version. 
 
 This program is distributed in the hope that it will be useful, 
 but WITHOUT ANY WARRANTY; without even the implied warranty of 
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 GNU General Public License for more details. 
 
 You should have received a copy of the GNU General Public License 
 along with this program; if not, write to the Free Software 
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA. 
 
 This file is subject to the Classpath exception as provided in the  
 LICENSE.txt file that accompanied this code.
 */

package bluej.debugmgr.codepad;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Effect;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import bluej.BlueJEvent;
import bluej.Config;
import bluej.collect.DataCollector;
import bluej.debugger.DebuggerField;
import bluej.debugger.DebuggerObject;
import bluej.debugger.ExceptionDescription;
import bluej.debugger.gentype.JavaType;
import bluej.debugmgr.ExecutionEvent;
import bluej.debugmgr.IndexHistory;
import bluej.debugmgr.Invoker;
import bluej.debugmgr.NamedValue;
import bluej.debugmgr.ResultWatcher;
import bluej.debugmgr.ValueCollection;
import bluej.parser.TextAnalyzer;
import bluej.pkgmgr.PkgMgrFrame;
import bluej.pkgmgr.Project;
import bluej.testmgr.record.InvokerRecord;
import bluej.utility.Utility;
import bluej.utility.javafx.JavaFXUtil;
import threadchecker.OnThread;
import threadchecker.Tag;

/**
 * A code pad which can evaluate fragments of Java code.
 * 
 * In JavaFX this is built using a ListView.  ListViews have virtualised
 * cells, which means that only enough rows are created to display
 * the currently visible portion of the scroll pane, not every row in the list.
 * So if the user has a history 200 lines long, but only 8 lines are visible
 * in the codepad, then no matter where they scroll to, only 8 display lines will be
 * created, not 200.
 * 
 * ListViews allow editing of any individual cell, but we hack it here so that
 * (a) only the last row can be edited, and (b) the last row is *always* in an editing state.
 * This means the users see one text field at the bottom of the list, and rows
 * above that with the history (styled as we like).
 */
@OnThread(Tag.FX)
public class CodePad extends VBox
    implements ValueCollection, PkgMgrFrame.PkgMgrPane
{
    private final ListView<HistoryRow> historyView;

    /**
     * The edit field for input
     */
    private final TextField inputField;

    /**
     * A data item which backs a single row in the code pad.
     * This might be the currently edited row (the last row), or
     * a read-only item detailing a past command or command outcome;
     */
    private abstract static @OnThread(Tag.FX) class HistoryRow
    {
        // Text content of the row
        private final String text;

        // Different styles used for the rows
        @OnThread(Tag.Any)
        public static enum RowStyle
        {
            COMMAND_PARTIAL("bj-codepad-cmd-partial"),
            COMMAND_END("bj-codepad-cmd-end"),
            ERROR("bj-codepad-error"),
            OUTPUT("bj-codepad-output");

            private final String pseudo;

            public String getPseudoClass()
            {
                return pseudo;
            }

            private RowStyle(String pseudo)
            {
                this.pseudo = pseudo;
            }
        }

        public HistoryRow(String text)
        {
            this.text = text;
        }

        public final String getText() { return text; }

        @Override
        public String toString()
        {
            return getText();
        }

        public abstract Node getGraphic();
        /** Gets the graphical style that should be used for displaying this row. */
        public abstract RowStyle getStyle();
    }

    public static abstract class IndentedRow extends HistoryRow
    {
        // Crude way of making sure all lines are spaced the same as ones with an object image;
        // use an invisible rectangle as a spacer:
        protected Rectangle r;

        public IndentedRow(String text)
        {
            super(text);
            r  = new Rectangle(objectImage.getWidth(), objectImage.getHeight());
            r.setVisible(false);
        }

        /** Gets the graphic to display alongside the row */
        @Override
        public Node getGraphic() { return r; }
    }

    // Handy array with all the different row pseudo-class styles.
    private static final String[] allRowStyles;
    static {
        allRowStyles = new String[HistoryRow.RowStyle.values().length];
        for (int i = 0; i < HistoryRow.RowStyle.values().length; i++)
        {
            allRowStyles[i] = HistoryRow.RowStyle.values()[i].getPseudoClass();
        }
    }

    /**
     * A row with a previously entered command.  This may be a single
     * complete row (e.g. 1+2) or it may be part of a multi-row
     * command.  We have a different pseudo-class for the last row
     * of commands, but currently don't use it differently in the CSS file.
     */
    @OnThread(Tag.FX)
    private static class CommandRow extends HistoryRow
    {
        private final boolean isFinalLine;
        public CommandRow(String text, boolean isFinalLine)
        {
            super(text);
            this.isFinalLine = isFinalLine;
        }

        // No indent spacer on command rows in our current style:
        @Override
        public Node getGraphic()
        {
            return null;
        }

        @Override
        public RowStyle getStyle()
        {
            return isFinalLine ? RowStyle.COMMAND_END : RowStyle.COMMAND_PARTIAL;
        }
    }

    /**
     * The successful output of a previous command.  It may or may not
     * have an object as an output.
     */
    @OnThread(Tag.FX)
    private class OutputSuccessRow extends IndentedRow
    {
        private final ImageView graphic;

        public OutputSuccessRow(String text, ObjectInfo objInfo)
        {
            super(text);
            if (objInfo != null)
            {
                graphic = new ImageView(objectImage);
                graphic.setMouseTransparent(false);
                // It turns out that LabeledSkinBase contains this code:
                // // RT-19851 Only setMouseTransparent(true) for an ImageView.  This allows the button
                // // to be picked regardless of the changing images on top of it.
                // if (graphic instanceof ImageView) {
                //    graphic.setMouseTransparent(true);
                //}
                // Our graphic is an ImageView, so it gets its mouse transparent set to
                // true by that code.  To ward this off, we add a listener that always
                // sets it back to false whenever it has been turned true:
                JavaFXUtil.addChangeListener(graphic.mouseTransparentProperty(), b -> {
                    // Won't infinitely recurse, only change back to true once:
                    if (b.booleanValue())
                        graphic.setMouseTransparent(false);
                });
                graphic.setCursor(Cursor.HAND);
                graphic.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
                    // Don't really care about click count; double click can do it too, that's fine.
                    if (e.getButton() == MouseButton.PRIMARY)
                    {
                        Stage fxWindow = frame.getFXWindow();
                        SwingUtilities.invokeLater(() -> {
                            frame.getPackage().getEditor().raisePutOnBenchEvent(fxWindow, objInfo.obj, objInfo.obj.getGenType(), objInfo.ir);
                        });
                        e.consume();
                    }
                });
                graphic.setOnMouseEntered(e -> {
                    graphic.setImage(objectImageHighlight);
                });
                graphic.setOnMouseExited(e -> {
                    graphic.setImage(objectImage);
                });
            }
            else
                graphic = null;
        }

        // Graphic is an object icon if applicable, otherwise
        // we use the invisible spacer from the parent:
        @Override
        public Node getGraphic()
        {
            return graphic != null ? graphic : super.getGraphic();
        }

        @Override
        public RowStyle getStyle()
        {
            return RowStyle.OUTPUT;
        }
    }

    /**
     * A row with an error output of a previous command.
     */
    @OnThread(Tag.FX)
    private static class ErrorRow extends IndentedRow
    {
        public ErrorRow(String text)
        {
            super(text);
        }

        @Override
        public RowStyle getStyle()
        {
            return RowStyle.ERROR;
        }
    }

    private static final String nullLabel = "null";
    
    private static final String uninitializedWarning = Config.getString("pkgmgr.codepad.uninitialized");

    private static final Image objectImage =
            Config.getImageAsFXImage("image.eval.object.add");
    private static final Image objectImageHighlight =
            Config.getImageAsFXImage("image.eval.object.add.highlight");
    
    private final PkgMgrFrame frame;
    @OnThread(Tag.FX)
    private String currentCommand = "";
    @OnThread(Tag.FX)
    private IndexHistory history;
    @OnThread(Tag.Swing)
    private Invoker invoker = null;
    @OnThread(Tag.Swing)
    private TextAnalyzer textParser = null;
    
    // Keeping track of invocation
    @OnThread(Tag.Swing)
    private boolean firstTry;
    @OnThread(Tag.Swing)
    private boolean wrappedResult;
    @OnThread(Tag.Swing)
    private String errorMessage;

    @OnThread(Tag.Swing)
    private boolean busy = false;

    @OnThread(Tag.Swing)
    private List<CodepadVar> localVars = new ArrayList<CodepadVar>();
    @OnThread(Tag.Swing)
    private List<CodepadVar> newlyDeclareds;
    @OnThread(Tag.Swing)
    private List<String> autoInitializedVars;
    // The action which removes the hover state on the object icon
    private Runnable removeHover;

    public CodePad(PkgMgrFrame frame)
    {
        this.frame = frame;
        JavaFXUtil.addStyleClass(this, "codepad");
        setMinWidth(100.0);
        inputField = new TextField();
        inputField.setFocusTraversable(true);
        inputField.setEditable(true);
        historyView = new ListView<>();
        historyView.setFocusTraversable(false);
        historyView.setEditable(false);
        // We can't lookup the scroll bar until we're in the scene and showing.
        // But also, we don't care about showing the shadow until there are items in the history
        // to be scrolled.  So a neat solution is to add the effect the first time items
        // appear in the history (which can only happen once it's on screen).
        historyView.getItems().addListener(new ListChangeListener<HistoryRow>()
        {
            @Override
            public void onChanged(Change<? extends HistoryRow> c)
            {
                // When the codepad history is not at the very bottom, add a shadow to indicate
                // that there is more beneath.  Otherwise, if you scroll to just the right point,
                // it looks like you are looking at the most recent item when in fact you're scrolled up.
                ScrollBar scrollBar = (ScrollBar)historyView.lookup(".scroll-bar");
                inputField.effectProperty().bind(Bindings.when(scrollBar.valueProperty().isNotEqualTo(1.0, 0.01)).<Effect>then(new DropShadow(6.0, 0.0, -3.0, Color.GRAY)).otherwise((Effect)null));
                historyView.getItems().removeListener(this);
            }
        });

        JavaFXUtil.addStyleClass(historyView, "codepad-history");

        getChildren().setAll(historyView, inputField);
        VBox.setVgrow(historyView, Priority.ALWAYS);

        //defineKeymap();
        history = new IndexHistory(20);

        historyView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        
        // Add context menu with copy:
        historyView.setContextMenu(new ContextMenu(JavaFXUtil.makeMenuItem(Config.getString("editor.copyLabel"), () -> {
            copySelectedRows();
        }, null)));
        
        // Add keyboard shortcut ourselves:
        historyView.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.C && e.isShortcutDown())
            {
                copySelectedRows();
                e.consume();
            }
        });
        inputField.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.UP)
            {
                historyBack();
                e.consume();
            }
            else if (e.getCode() == KeyCode.DOWN)
            {
                historyForward();
                e.consume();
            }
            else if (e.getCode() == KeyCode.ENTER && e.isShiftDown())
            {
                softReturn();
                e.consume();
            }
        });
        inputField.setOnAction(e -> {
            String line = inputField.getText();
            if (line.trim().isEmpty())
                return; // Don't allow entry of blank lines
            command(line, true);
            inputField.setEditable(false);    // don't allow input while we're thinking
            inputField.setText("");
            currentCommand = (currentCommand + line).trim();
            if(currentCommand.length() != 0)
            {
                history.add(line);
                String cmd = currentCommand;
                currentCommand = "";
                SwingUtilities.invokeLater(() -> executeCommand(cmd));
            }
        });

        historyView.setCellFactory(lv -> new ListCell<HistoryRow>() {
            {
                JavaFXUtil.addStyleClass(this, "codepad-row");
            }

            @Override
            @OnThread(Tag.FX)
            public void updateItem(HistoryRow item, boolean empty)
            {
                super.updateItem(item, empty);
                if (!empty && item != null)
                {
                    setGraphic(item.getGraphic());
                    setText(item.getText());
                    JavaFXUtil.selectPseudoClass(this, Arrays.asList(allRowStyles).indexOf(item.getStyle().getPseudoClass()), allRowStyles);
                }
                else
                {
                    setGraphic(null);
                    setText("");
                    JavaFXUtil.selectPseudoClass(this, -1, allRowStyles);
                }
            }
        });
    }

    private void copySelectedRows()
    {
        String copied = historyView.getSelectionModel().getSelectedItems().stream().map(HistoryRow::getText).collect(Collectors.joining("\n"));
        Clipboard.getSystemClipboard().setContent(Collections.singletonMap(DataFormat.PLAIN_TEXT, copied));
    }

    /**
     * Clear the local variables.
     */
    @OnThread(Tag.Swing)
    public void clearVars()
    {
        localVars.clear();
        if (textParser != null && frame.getProject() != null) {
            textParser.newClassLoader(frame.getProject().getClassLoader());
        }
    }
    
    //   --- ValueCollection interface ---
    
    /*
     * @see bluej.debugmgr.ValueCollection#getValueIterator()
     */
    @OnThread(Tag.Swing)
    public Iterator<CodepadVar> getValueIterator()
    {
        return localVars.iterator();
    }
    
    /*
     * @see bluej.debugmgr.ValueCollection#getNamedValue(java.lang.String)
     */
    @OnThread(Tag.Swing)
    public NamedValue getNamedValue(String name)
    {
        Class<Object> c = Object.class;
        NamedValue nv = getLocalVar(name);
        if (nv != null) {
            return nv;
        }
        else {
            return frame.getObjectBench().getNamedValue(name);
        }
    }
    
    /**
     * Search for a named local variable, but do not fall back to the object
     * bench if it cannot be found (return null in this case).
     * 
     * @param name  The name of the variable to search for
     * @return    The named variable, or null
     */
    @OnThread(Tag.Swing)
    private NamedValue getLocalVar(String name)
    {
        Iterator<CodepadVar> i = localVars.iterator();
        while (i.hasNext()) {
            NamedValue nv = (NamedValue) i.next();
            if (nv.getName().equals(name))
                return nv;
        }
        
        // not found
        return null;
    }
    
    private class CodePadResultWatcher implements ResultWatcher
    {
        private final String command;

        public CodePadResultWatcher(String command)
        {
            this.command = command;
        }

        /*
                 * @see bluej.debugmgr.ResultWatcher#beginExecution()
                 */
        @Override
        @OnThread(Tag.Swing)
        public void beginCompile()
        {
        }

        /*
         * @see bluej.debugmgr.ResultWatcher#beginExecution()
         */
        @Override
        @OnThread(Tag.Swing)
        public void beginExecution(InvokerRecord ir)
        {
            BlueJEvent.raiseEvent(BlueJEvent.METHOD_CALL, ir);
        }

        /*
         * @see bluej.debugmgr.ResultWatcher#putResult(bluej.debugger.DebuggerObject, java.lang.String, bluej.testmgr.record.InvokerRecord)
         */
        @Override
        @OnThread(Tag.Swing)
        public void putResult(final DebuggerObject result, final String name, final InvokerRecord ir)
        {
            frame.getObjectBench().addInteraction(ir);
            updateInspectors();

            // Newly declared variables are now initialized
            if (newlyDeclareds != null)
            {
                Iterator<CodepadVar> i = newlyDeclareds.iterator();
                while (i.hasNext())
                {
                    CodepadVar cpv = (CodepadVar)i.next();
                    cpv.setInitialized();
                }
                newlyDeclareds = null;
            }

            boolean giveUninitializedWarning = autoInitializedVars != null && autoInitializedVars.size() != 0;

            if (giveUninitializedWarning && Utility.firstTimeThisRun("TextEvalPane.uninitializedWarning"))
            {
                // Some variables were automatically initialized - warn the user that
                // this won't happen in "real" code.

                String warning = uninitializedWarning;

                int findex = 0;
                while (findex < warning.length())
                {
                    int nindex = warning.indexOf('\n', findex);
                    if (nindex == -1)
                        nindex = warning.length();

                    String warnLine = warning.substring(findex, nindex);
                    Platform.runLater(() -> error(warnLine));
                    findex = nindex + 1; // skip the newline character
                }

                autoInitializedVars.clear();
            }

            if (!result.isNullObject())
            {
                DebuggerField resultField = result.getField(0);
                String resultString = resultField.getValueString();

                if (resultString.equals(nullLabel))
                {
                    DataCollector.codePadSuccess(frame.getPackage(), ir.getOriginalCommand(), resultString);
                    Platform.runLater(() -> output(resultString));
                }
                else
                {
                    boolean isObject = resultField.isReferenceType();

                    if (isObject)
                    {
                        DebuggerObject resultObject = resultField.getValueObject(null);
                        String resultType = resultObject.getGenType().toString(true);
                        String resultOutputString = resultString + "   (" + resultType + ")";
                        DataCollector.codePadSuccess(frame.getPackage(), ir.getOriginalCommand(), resultOutputString);
                        Platform.runLater(() -> objectOutput(resultOutputString, new ObjectInfo(resultObject, ir)));
                    }
                    else
                    {
                        String resultType = resultField.getType().toString(true);
                        String resultOutputString = resultString + "   (" + resultType + ")";
                        DataCollector.codePadSuccess(frame.getPackage(), ir.getOriginalCommand(), resultOutputString);
                        Platform.runLater(() -> output(resultOutputString));
                    }
                }
            }
            else
            {
                //markCurrentAs(TextEvalSyntaxView.OUTPUT, false);
            }

            ExecutionEvent executionEvent = new ExecutionEvent(frame.getPackage());
            executionEvent.setCommand(command);
            executionEvent.setResult(ExecutionEvent.NORMAL_EXIT);
            executionEvent.setResultObject(result);
            BlueJEvent.raiseEvent(BlueJEvent.EXECUTION_RESULT, executionEvent);

            textParser.confirmCommand();
            Platform.runLater(() -> inputField.setEditable(true));    // allow next input
            busy = false;
        }

        @OnThread(Tag.Swing)
        private void updateInspectors()
        {
            Project proj = frame.getPackage().getProject();
            Platform.runLater(() -> proj.updateInspectors());
        }

        /**
         * An invocation has failed - here is the error message
         */
        @Override
        @OnThread(Tag.Swing)
        public void putError(String message, InvokerRecord ir)
        {
            if (firstTry)
            {
                if (wrappedResult)
                {
                    // We thought we knew what the result type should be, but there
                    // was a compile time error. So try again, assuming that we
                    // got it wrong, and we'll use the dynamic result type (meaning
                    // we won't get type arguments).
                    wrappedResult = false;
                    errorMessage = null; // use the error message from this second attempt
                    invoker = new Invoker(frame, CodePad.this, command, this);
                    invoker.setImports(textParser.getImportStatements());
                    invoker.doFreeFormInvocation("");
                }
                else
                {
                    // We thought there was going to be a result, but compilation failed.
                    // Try again, but assume we have a statement this time.
                    firstTry = false;
                    invoker = new Invoker(frame, CodePad.this, command, this);
                    invoker.setImports(textParser.getImportStatements());
                    invoker.doFreeFormInvocation(null);
                    if (errorMessage == null)
                    {
                        errorMessage = message;
                    }
                }
            }
            else
            {
                if (errorMessage == null)
                {
                    errorMessage = message;
                }

                // An error. Remove declared variables.
                if (autoInitializedVars != null)
                {
                    autoInitializedVars.clear();
                }

                removeNewlyDeclareds();
                DataCollector.codePadError(frame.getPackage(), ir.getOriginalCommand(), errorMessage);
                showErrorMsg(errorMessage);
                errorMessage = null;
            }
        }

        /**
         * A runtime exception occurred.
         */
        @Override
        @OnThread(Tag.Swing)
        public void putException(ExceptionDescription exception, InvokerRecord ir)
        {
            ExecutionEvent executionEvent = new ExecutionEvent(frame.getPackage());
            executionEvent.setCommand(command);
            executionEvent.setResult(ExecutionEvent.EXCEPTION_EXIT);
            executionEvent.setException(exception);
            BlueJEvent.raiseEvent(BlueJEvent.EXECUTION_RESULT, executionEvent);
            updateInspectors();

            if (autoInitializedVars != null)
            {
                autoInitializedVars.clear();
            }

            removeNewlyDeclareds();
            String message = exception.getClassName() + " (" + exception.getText() + ")";
            DataCollector.codePadException(frame.getPackage(), ir.getOriginalCommand(), message);
            showExceptionMsg(message);
        }

        /**
         * The remote VM terminated before execution completed (or as a result of
         * execution).
         */
        @Override
        @OnThread(Tag.Swing)
        public void putVMTerminated(InvokerRecord ir)
        {
            if (autoInitializedVars != null)
                autoInitializedVars.clear();

            removeNewlyDeclareds();


            String message = Config.getString("pkgmgr.codepad.vmTerminated");
            DataCollector.codePadError(frame.getPackage(), ir.getOriginalCommand(), message);
            Platform.runLater(() -> error(message));

            completeExecution();
        }
    }
    
    /**
     * Remove the newly declared variables from the value collection.
     * (This is needed if compilation fails, or execution bombs with an exception).
     */
    @OnThread(Tag.Swing)
    private void removeNewlyDeclareds()
    {
        if (newlyDeclareds != null) {
            Iterator<CodepadVar> i = newlyDeclareds.iterator();
            while (i.hasNext()) {
                localVars.remove(i.next());
            }
            newlyDeclareds = null;
        }
    }
    
    //   --- end of ResultWatcher interface ---
    
    /**
     * Show an error message, and allow further command input.
     */
    @OnThread(Tag.Swing)
    private void showErrorMsg(final String message)
    {
        Platform.runLater(() -> error("Error: " + message));
        completeExecution();
    }
    
    /**
     * Show an exception message, and allow further command input.
     */
    @OnThread(Tag.Swing)
    private void showExceptionMsg(final String message)
    {
        Platform.runLater(() -> error("Exception: " + message));
        completeExecution();
    }
    
    /**
     * Execution of the current command has finished (one way or another).
     * Allow further command input.
     */
    @OnThread(Tag.Swing)
    private void completeExecution()
    {
        Platform.runLater(() -> inputField.setEditable(true));
        busy = false;
    }

    /**
     * Record part of a command
     * @param s
     */
    private void command(String s, boolean isFinalLine)
    {
        addRow(new CommandRow(s, isFinalLine));
    }

    private void addRow(HistoryRow row)
    {
        historyView.getItems().add(row);
        historyView.scrollTo(historyView.getItems().size() - 1);
    }

    /**
     * Write a (non-error) message to the text area.
     * @param s The message
     */
    private void output(String s)
    {
        addRow(new OutputSuccessRow(s, null));
    }
    
    /**
     * Write a (non-error) message to the text area.
     * @param s The message
     */
    private void objectOutput(String s, ObjectInfo objInfo)
    {
        addRow(new OutputSuccessRow(s, objInfo));
    }
    
    /**
     * Write an error message to the text area.
     * @param s The message
     */
    private void error(String s)
    {
        addRow(new ErrorRow(s));
    }

    public void clear()
    {
        SwingUtilities.invokeLater(this::clearVars);
    }

    public void resetFontSize()
    {

    }

    @OnThread(Tag.Swing)
    private void executeCommand(String command)
    {
        if (busy) {
            return;
        }
        
        firstTry = true;
        busy = true;
        if (textParser == null) {
            textParser = new TextAnalyzer(frame.getProject().getEntityResolver(),
                    frame.getPackage().getQualifiedName(), CodePad.this);
        }
        String retType;
        retType = textParser.parseCommand(command);
        wrappedResult = (retType != null && retType.length() != 0);
        
        // see if any variables were declared
        if (retType == null) {
            firstTry = false; // Only try once.
            command = textParser.getAmendedCommand();
            List<DeclaredVar> declaredVars = textParser.getDeclaredVars();
            if (declaredVars != null) {
                Iterator<DeclaredVar> i = declaredVars.iterator();
                while (i.hasNext()) {
                    if (newlyDeclareds == null) {
                        newlyDeclareds = new ArrayList<CodepadVar>();
                    }
                    if (autoInitializedVars == null) {
                        autoInitializedVars = new ArrayList<String>();
                    }
                    
                    DeclaredVar dv = i.next();
                    String declaredName = dv.getName();
                    
                    if (getLocalVar(declaredName) != null) {
                        // The variable has already been declared
                        String errMsg = Config.getString("pkgmgr.codepad.redefinedVar");
                        errMsg = Utility.mergeStrings(errMsg, declaredName);
                        showErrorMsg(errMsg);
                        removeNewlyDeclareds();
                        return;
                    }
                    
                    CodepadVar cpv = new CodepadVar(dv.getName(), dv.getDeclaredType(), dv.isFinal());
                    newlyDeclareds.add(cpv);
                    localVars.add(cpv);

                    // If the variable was declared but not initialized, the codepad
                    // auto-initializes it. We add to a list so that we can display
                    // a warning to that effect, once the command has completed.
                    if (! dv.isInitialized()) {
                        autoInitializedVars.add(dv.getName());
                    }
                }
            }
        }

        CodePadResultWatcher watcher = new CodePadResultWatcher(command);
        invoker = new Invoker(frame, CodePad.this, command, watcher);
        invoker.setImports(textParser.getImportStatements());
        if (!invoker.doFreeFormInvocation(retType)) {
            // Invocation failed
            firstTry = false;
            watcher.putError("Invocation failed.", null);
        }
    }

    private void softReturn()
    {
        String line = inputField.getText();
        if (line.trim().isEmpty())
            return; // Don't allow entry of blank lines
        currentCommand += line + " ";
        history.add(line);
        command(line, false);
        inputField.setText("");
    }

    private void historyBack()
    {
        String line = history.getPrevious();
        if(line != null) {
            setInput(line);
        }
    }

    private void setInput(String line)
    {
        inputField.setText(line);
        // When going back in history, seems best to put cursor at the end of the field
        // but by default it gets put at the beginning when setting new text.:
        inputField.end();
    }

    private void historyForward()
    {
        String line = history.getNext();
        if(line != null) {
            setInput(line);
        }
    }

    public void focusInputField()
    {
        inputField.requestFocus();
    }

    @OnThread(Tag.Any)
    final class ObjectInfo {
        DebuggerObject obj;
        InvokerRecord ir;
        
        /**
         * Create an object holding information about an invocation.
         */
        public ObjectInfo(DebuggerObject obj, InvokerRecord ir) {
            this.obj = obj;
            this.ir = ir;
        }
    }
    
    final class CodepadVar implements NamedValue {
        
        String name;
        boolean finalVar;
        boolean initialized = false;
        JavaType type;
        
        public CodepadVar(String name, JavaType type, boolean finalVar)
        {
            this.name = name;
            this.finalVar = finalVar;
            this.type = type;
        }
        
        public String getName()
        {
            return name;
        }
        
        public JavaType getGenType()
        {
            return type;
        }
        
        public boolean isFinal()
        {
            return finalVar;
        }
        
        public boolean isInitialized()
        {
            return initialized;
        }
        
        public void setInitialized()
        {
            initialized = true;
        }
    }
    
}