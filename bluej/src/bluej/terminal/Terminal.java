package bluej.terminal;

import bluej.Config;
import bluej.BlueJEvent;
import bluej.BlueJEventListener;
import bluej.prefmgr.PrefMgr;
import bluej.utility.Debug;
import bluej.utility.FileUtility;
import bluej.utility.DialogManager;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.text.*;
import java.io.IOException;
import java.io.Reader;
import java.io.OutputStream;
import java.io.Writer;
import java.io.FileWriter;

/**
 * The Frame part of the Terminal window used for I/O when running programs
 * under BlueJ.
 *
 * @author  Michael Kolling
 * @version $Id: Terminal.java 1229 2002-04-19 14:17:18Z mik $
 */
public final class Terminal extends JFrame
    implements KeyListener, BlueJEventListener
{
    private static final String WINDOWTITLE = Config.getString("terminal.title");
    private static final int windowHeight =
        Config.getPropInteger("bluej.terminal.height", 22);
    private static final int windowWidth =
        Config.getPropInteger("bluej.terminal.width", 80);

    private static final Color activeBgColour = Color.white;
    private static final Color inactiveBgColour = new Color(224, 224, 224);
    private static final Color fgColour = Color.black;
    private static final Color errorColour = Color.red;
    private static final Image iconImage =
        Config.getImageAsIcon("image.icon.terminal").getImage();

    private static final int SHORTCUT_MASK =
        Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
        //Event.CTRL_MASK;

    // -- static singleton factory method --

    static Terminal frame = null;
    static boolean enabled = true;

    public synchronized static Terminal getTerminal()
    {
        if(frame == null)
            frame = new Terminal();
        return frame;
    }


    // -- instance --

    private TermTextArea text;
    private JTextArea errorText;
    private JScrollPane errorScrollPane;
    private boolean isActive = false;
    private boolean recordMethodCalls = false;
    private InputBuffer buffer;

    private JCheckBoxMenuItem recordCalls;
    private JCheckBoxMenuItem unlimitedBuffering;

    Reader in = new TerminalReader();
    Writer out = new TerminalWriter(false);
    Writer err = new TerminalWriter(true);


    /**
     * Create a new terminal window with default specifications.
     */
    private Terminal()
    {
        this(WINDOWTITLE, windowWidth, windowHeight);
    }


    /**
     * Create a new terminal window.
     */
    private Terminal(String title, int columns, int rows)
    {
        super(title);

        buffer = new InputBuffer(256);
        makeWindow(columns, rows);
        BlueJEvent.addListener(this);
    }


    /**
     * Show or hide the terminal window.
     */
    public void showTerminal(boolean doShow)
    {
        setVisible(doShow);
        if(doShow) {
            setState(Frame.NORMAL);  // de-iconify / de-minimize
            text.requestFocus();
        }
    }


    /**
     * Return true if the window is currently displayed.
     */
    public boolean isShown()
    {
        return isShowing();
    }


    /**
     * Make the window active.
     */
    public void activate(boolean active)
    {
        if(active != isActive) {
            text.setEditable(active);
            //text.setEnabled(active);
            //text.setBackground(active ? activeBgColour : inactiveBgColour);
            isActive = active;
        }
    }


    /**
     * Clear the terminal.
     */
    public void clear()
    {
        text.setText("");
    }


    /**
     * Save the terminal text to file.
     */
    public void save()
    {
        String fileName = FileUtility.getFileName(this,
                                 Config.getString("terminal.save.title"),
                                 Config.getString("terminal.save.buttonText"),
                                 false, null, false);
        if(fileName != null) {
            try {
                FileWriter writer = new FileWriter(fileName);
                text.write(writer);
                writer.close();
            }
            catch (IOException ex) {
                DialogManager.showError(this, "error-save-file");
            }
        }
    }


    /**
     * Write some text to the terminal.
     */
    private void writeToTerminal(String s)
    {
        text.append(s);
        text.setCaretPosition(text.getDocument().getLength());
    }


    /**
     * Write a character to the terminal.
     */
    private void writeToTerminal(char ch)
    {
        text.append(String.valueOf(ch));
        text.setCaretPosition(text.getDocument().getLength());
    }


    /**
     * Write some text to the terminal.
     */
    private void writeToErrorOut(String s)
    {
        // FIX
        text.append(s);
        text.setCaretPosition(text.getDocument().getLength());
    }


    /**
     * Write a character to the terminal.
     */
    private void writeToErrorOut(char ch)
    {
        if(!errorScrollPane.isVisible()) {
            errorScrollPane.setVisible(true);
            pack();
        }
        errorText.append(String.valueOf(ch));
        errorText.setCaretPosition(errorText.getDocument().getLength());
    }


    /**
     * Set the terminal size the the specified number of rows and columns.
     */
    private void setScreenSize(int columns, int rows)
    {
        text.setColumns(columns);
        text.setRows(rows);
        pack();
    }


    /**
     * Prepare the terminal for I/O.
     */
    private void prepare()
    {
        if(!isShown())
            showTerminal(true);
    }


    /**
     * Return the input stream that can be used to read from this terminal.
     */
    public Reader getReader()
    {
        return in;
    }


    /**
     * Return the output stream that can be used to write to this terminal
     */
    public Writer getWriter()
    {
        return out;
    }


    /**
     * Return the output stream that can be used to write error output to this terminal
     */
    public Writer getErrorWriter()
    {
        return err;
    }


    // ---- KeyListener interface ----

    public void keyPressed(KeyEvent event) {
        if(event.getModifiers() != SHORTCUT_MASK)  // let menu commands pass
            event.consume();
    }

    public void keyReleased(KeyEvent event) {
        if(event.getModifiers() != SHORTCUT_MASK)
            event.consume();
    }

    public void keyTyped(KeyEvent event)
    {
        if(isActive) {
            char ch = event.getKeyChar();

            switch(ch) {

            case '\b':	// backspace
                if(buffer.backSpace()) {
                    try {
                        int length = text.getDocument().getLength();
                        text.replaceRange("", length-1, length);
                    }
                    catch (Exception exc) {
                        Debug.reportError("bad location " + exc);
                    }
                }
                break;

            case '\r':	// carriage return
            case '\n':	// newline
                if(buffer.putChar('\n')) {
                    writeToTerminal(ch);
                    buffer.notifyReaders();
                }
                break;

            default:
                if(buffer.putChar(ch))
                    writeToTerminal(ch);
                break;
            }
        }
        event.consume();	// make sure the text area doesn't handle this
    }


    // ---- BlueJEventListener interface ----

    /**
     * Called when a BlueJ event is raised. The event can be any BlueJEvent
     * type. The implementation of this method should check first whether
     * the event type is of interest an return immediately if it isn't.
     *
     * @param eventId  A constant identifying the event. One of the event id
     *                 constants defined in BlueJEvent.
     * @param arg      An event specific parameter. See BlueJEvent for
     *                 definition.
     */
    public void blueJEvent(int eventId, Object arg)
    {
        if(eventId == BlueJEvent.METHOD_CALL) {
            if(recordMethodCalls) {
                try {
                    if(text.getCaretPosition() !=
                         text.getLineStartOffset(text.getLineCount())) {
                        writeToTerminal("\n");
                    }
                }
                catch(BadLocationException exc) {
                    writeToTerminal("\n");
                }
                writeToTerminal("[ ");
                writeToTerminal((String)arg);
                writeToTerminal(" ]\n");
            }
        }
    }

    // ---- make window frame ----

    private void makeWindow(int columns, int rows)
    {
        setIconImage(iconImage);

        text = new TermTextArea(rows, columns);
        JScrollPane scrollPane = new JScrollPane(text);
        text.setFont(PrefMgr.getTerminalFont());
        text.setEditable(false);
        text.setLineWrap(false);
        text.setForeground(fgColour);
        text.setMargin(new Insets(6, 6, 6, 6));
        //text.setBackground(inactiveBgColour);

        getContentPane().add(scrollPane, BorderLayout.CENTER);

        errorText = new JTextArea(4, columns);
        errorScrollPane = new JScrollPane(errorText);
        errorText.setFont(PrefMgr.getTerminalFont());
        errorText.setEditable(false);
        errorText.setLineWrap(false);
        errorText.setForeground(errorColour);
        errorText.setMargin(new Insets(6, 6, 6, 6));

        getContentPane().add(errorScrollPane, BorderLayout.SOUTH);
        errorScrollPane.setVisible(false);

        text.addKeyListener(this);

        JMenuBar menubar = new JMenuBar();
        JMenu menu = new JMenu(Config.getString("terminal.options"));
        JMenuItem item;
        item = menu.add(new ClearAction());
        item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_K,
                                                   SHORTCUT_MASK));
        item = menu.add(getCopyAction());
        item.setText(Config.getString("terminal.copy"));
        item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C,
                                                   SHORTCUT_MASK));
        item = menu.add(new SaveAction());
        item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S,
                                                   SHORTCUT_MASK));
        menu.add(new JSeparator());

      // the following should be replaced once jdk 1.2.x goes out of fashion.
      // as of 1.3, the JCheckBoxMenuItem can be created with an action
      // parameter directly
        recordCalls = new JCheckBoxMenuItem(
                                     Config.getString("terminal.recordCalls"));
        recordCalls.addActionListener(new RecordCallAction());
        menu.add(recordCalls);

        unlimitedBuffering = new JCheckBoxMenuItem(
                                     Config.getString("terminal.buffering"));
        unlimitedBuffering.addActionListener(new BufferAction());
        menu.add(unlimitedBuffering);

        menu.add(new JSeparator());
        item = menu.add(new CloseAction());
        item.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W,
                                                   SHORTCUT_MASK));

        menubar.add(menu);
        setJMenuBar(menubar);

        // Close Action when close button is pressed
        addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent event) {
                    Window win = (Window)event.getSource();
                    win.setVisible(false);
                }
            });

        // save position when window is moved
        addComponentListener(new ComponentAdapter() {
                public void componentMoved(ComponentEvent event)
                {
                    Config.putLocation("bluej.terminal", getLocation());
                }
            });

        setLocation(Config.getLocation("bluej.terminal"));

        pack();
    }


    private class ClearAction extends AbstractAction
    {
        public ClearAction()
        {
            super(Config.getString("terminal.clear"));
        }

        public void actionPerformed(ActionEvent e) {
            clear();
        }
    }

    private class SaveAction extends AbstractAction
    {
        public SaveAction()
        {
            super(Config.getString("terminal.save"));
        }

        public void actionPerformed(ActionEvent e) {
            save();
        }
    }

    private class CloseAction extends AbstractAction
    {
        public CloseAction()
        {
            super(Config.getString("terminal.close"));
        }

        public void actionPerformed(ActionEvent e) {
            showTerminal(false);
        }
    }

    private Action getCopyAction()
    {
        Action[] textActions = text.getActions();
        for (int i=0; i < textActions.length; i++)
            if(textActions[i].getValue(Action.NAME).equals("copy-to-clipboard"))
                return textActions[i];

        return null;
    }

    private class RecordCallAction extends AbstractAction
    {
        public RecordCallAction()
        {
            super(Config.getString("terminal.recordCalls"));
        }

        public void actionPerformed(ActionEvent e) {
            recordMethodCalls = recordCalls.isSelected();
        }
    }

    private class BufferAction extends AbstractAction
    {
        public BufferAction()
        {
            super(Config.getString("terminal.buffering"));
        }

        public void actionPerformed(ActionEvent e) {
            text.setUnlimitedBuffering(unlimitedBuffering.isSelected());
        }
    }
            
    /**
     * A Reader which reads from the terminal.
     */
    private class TerminalReader extends Reader {

        public int read(char[] cbuf, int off, int len) throws IOException
        {
            int charsRead = 0;

            while(charsRead < len) {
                cbuf[off + charsRead] = buffer.getChar();
                charsRead++;
                if(buffer.numberOfCharacters() == 0)
                    break;
            }
            return charsRead;
        }

        public void close() throws IOException
        {
        }

    }

    /**
     * A writer which writes to the terminal. It can be flagged for error output.
     * The idea is that error output could be presented differently from standard
     * output.
     */
    private class TerminalWriter extends Writer {

        private boolean isErrorOut;

        TerminalWriter(boolean isError)
        {
            super();
            isErrorOut = isError;
        }

        public void write(char[] cbuf, int off, int len) throws IOException
        {
            if (enabled) {
                prepare();
                if(isErrorOut)
                    writeToErrorOut(new String(cbuf, off, len));
                else
                    writeToTerminal(new String(cbuf, off, len));
            }
        }

        public void write(int ch) throws IOException
        {
            if (enabled) {
                prepare();
                if(isErrorOut)
                    writeToErrorOut((char)ch);
                else
                    writeToTerminal((char)ch);
            }
        }

        public void flush() throws IOException
        {
        }

        public void close() throws IOException
        {
        }
    }

}
