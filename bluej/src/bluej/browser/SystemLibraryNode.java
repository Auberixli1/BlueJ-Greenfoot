package bluej.browser;

import javax.swing.tree.*;
import javax.swing.*;

import java.awt.event.*;

/**
 * LibraryNode subclass used to represent a single
 * system defined package available in the library browser tree.  Each package
 * has a display version of it's name, the directory where it
 * lives in the filesystem, and an option shadowArea if the 
 * directory is not writable.  Each package can also store a list
 * of all the classfiles contained within it.
 * 
 * @see LibraryChooser
 * @author $Author: mik $
 * @version $Id: SystemLibraryNode.java 36 1999-04-27 04:04:54Z mik $
 */
public class SystemLibraryNode extends LibraryNode {

	/**
	 * Create a new SystemLibraryNode with the specified displayName,
	 * and internalName.
	 * 
	 * @param displayName the display version of the nodes name
	 * @param internalName the directory containing the package
	 */
	public SystemLibraryNode(String displayName, String internalName) {
		super(displayName, internalName);
	}

}
