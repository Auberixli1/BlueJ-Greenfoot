package bluej.editor.moe;

import bluej.utility.Debug;

import java.awt.*;              // MenuBar, MenuItem, Menu, Button, etc.
import java.awt.event.*;        // New Event model    
import javax.swing.*;		// all the GUI components
import javax.swing.event.*;

/**
 ** @author Michael Kolling
 **
 **/

public final class StatusLabel extends JLabel

	implements DocumentListener
{
  // ---------------- CONSTANTS -----------------

  public static Font statusFont = new Font("SansSerif", Font.BOLD | Font.ITALIC, 11);

  // current save state
  static final int READONLY = 0;
  static final int SAVED = 1;   
  static final int CHANGED = 2; 

  private final String[] stateString = { "read-only", "saved", "changed" };


  // ------------ INSTANCE VARIABLES ------------

  private int state;
  private MoeEditor myEditor;


  // -------------- CONSTRUCTORS ----------------

  public StatusLabel(int initialState, MoeEditor editor)
  {
    super("", JLabel.CENTER);
    setText(stateString[initialState]);
    setFont(statusFont);
    setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
    state = initialState;
    myEditor = editor;
  }

  // ------------- PUBLIC METHODS ---------------

  // -------- three methods from DocumentListener: -------- 

  // insert into document
  public void insertUpdate(DocumentEvent e) 
  {
    if (state != CHANGED) {
	setState (CHANGED);
	myEditor.setChanged();
    }
  }

  // remove from document
  public void removeUpdate(DocumentEvent e) 
  {
    if (state != CHANGED) {
	setState (CHANGED);
	myEditor.setChanged();
    }
  }

  // document (properties?) changed - ignore
  public void changedUpdate(DocumentEvent e) {}

  // ----------------- managing the state -------------------

  public boolean isSaved () 
  {
    return (state != CHANGED);
  }

  public boolean isChanged () 
  {
    return (state == CHANGED);
  }

  public boolean isReadOnly () 
  {
    return (state == READONLY);
  }

  public void setState (int newState)
  {
    state = newState;
    setText(stateString[state]);
  }

}  // end class StatusLabel
