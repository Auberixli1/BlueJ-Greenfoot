// Copyright (c) 2000 BlueJ Group, Monash University
//
// This software is made available under the terms of the "MIT License"
// A copy of this license is included with this source distribution
// in "license.txt" and is also available at:
// http://www.opensource.org/licenses/mit-license.html 
// Any queries should be directed to Michael Kolling mik@mip.sdu.dk
package bluej.editor;

import java.awt.print.PrinterJob;


/**
 * Interface between an editor and the rest of BlueJ
 * 
 * @version $Id: Editor.java 1626 2003-02-11 01:46:35Z ajp $
 * @author Michael Cahill
 * @author Michael Kolling
 */
public interface Editor
{
    /**
     * Read a file into the editor buffer and show the editor. If the editor
     * already contains text, it is cleared first.
     * 
     * @param filename    the file to be read
     * @param compiled    true if this is a compiled class
     * 
     * @return false is there was a problem, true otherwise
     */
    boolean showFile(String filename, boolean compiled);

    /**
     * Reload and display the same file that was displayed before.
     */
    void reloadFile();

    /**
     * Clear the current buffer. The editor is not redisplayed after a call to
     * this function. It is typically used in a sequence "clear; [insertText];
     * show".
     */
    void clear();

    /**
     * Insert a string into the buffer. The editor is not immediately
     * redisplayed. This function is typically used in a sequence "clear;
     * [insertText]; show".
     * 
     * @param text        the text to be inserted
     * @param caretBack    move the caret to the beginning of the inserted text
     */
    void insertText(String text, boolean caretBack);

    /**
     * Set the selection of the editor to be a len characters on the line
     * lineNumber, starting with column columnNumber
     * 
     * @param lineNumber the line to select characters on
     * @param column the column to start selection at (1st column is 1 - not 0)
     * @param len the number of characters to select
     */
    void setSelection(int lineNumber, int column, int len);

    /**
     * Set the selection of the editor to be a len characters on the line
     * lineNumber, starting with column columnNumber
     * 
     * @param lineNumber the line to select characters on
     * @param column the column to start selection at (1st column is 1 - not 0)
     * @param len the number of characters to select
     */
    void setSelection(int firstlineNumber, int firstColumn,
                        int secondLineNumber, int SecondColumn );


    /**
     * Show the editor window. This includes whatever is necessary of the
     * following: make visible, de-iconify, bring to front of window stack.
     * 
     * @param vis DOCUMENT ME!
     */
    void setVisible(boolean vis);

    /**
     * True is the editor is on screen.
     * 
     * @return true if editor is on screen
     */
    boolean isShowing();

    /**
     * Save the buffer to disk under the current file name. This is an error if
     * the editor has not been given a file name (ie. if readFile was not
     * executed).
     */
    void save();

    /**
     * Close the editor window.
     */
    void close();

    /**
     * Refresh the editor display (needed if font size has changed)
     */
    void refresh();

    /**
     * Display a message (used for compile/runtime errors). An editor must
     * support at least two lines of message text, so the message can contain
     * a newline character.
     * 
     * @param message    the message to be displayed
     * @param lineNumber    the line to move the cursor to (the line is also
     *        highlighted)
     * @param column        the column to move the cursor to
     * @param beep        if true, do a system beep
     * @param setStepMark    if true, set step mark (for single stepping)
     * @param help        name of help group (may be null)
     */
    void displayMessage(String message, int lineNumber, int column, 
                        boolean beep, boolean setStepMark, String help);

    /**
     * Remove the step mark (the mark that shows the current line when
     * single-stepping through code). If it is not currently displayed, do
     * nothing.
     */
    void removeStepMark();

    /**
     * Change class name.
     * 
     * @param title        new window title
     * @param filename    new file name
     */
    void changeName(String title, String filename);

    /**
     * Set the "compiled" status
     * 
     * @param compiled    true if the class has been compiled
     */
    void setCompiled(boolean compiled);

    /**
     * Remove all breakpoints in this editor.
     */
    void removeBreakpoints();

    /**
     * Determine whether this editor has been modified from the version on disk
     * 
     * @return a boolean indicating whether the file is modified
     */
    boolean isModified();

    /**
     * Prints the contents of the editor
     */
    void print(PrinterJob printerJob);

    void setReadOnly(boolean readOnlyStatus);
} // end interface Editor
