package bluej.utility;

import bluej.Config;

import java.awt.*;
import javax.swing.*;

/**
 ** @version $Id: MultiLineLabel.java 36 1999-04-27 04:04:54Z mik $
 ** @author Justin Tan
 ** A multi-line Label-like AWT component.
 **/
public class MultiLineLabel extends JPanel
{
    int fontAttributes = Font.PLAIN;
    int alignment;
	
    /**
     ** Constructor - make a multiline label
     **/
    public MultiLineLabel(String text, int alignment)
    {
	this.alignment = alignment;
	setLayout(new GridLayout(0,1));
	if(text != null)
	    setText(text);
    }

    /**
     ** Constructor, defaults to centered text
     **/
    public MultiLineLabel(String text)
    {
	this(text, JLabel.CENTER);
    }

    /**
     ** Constructor, empty with the given alignment
     **/
    public MultiLineLabel(int alignment)
    {
	this(null, alignment);
    }

    /**
     ** Constructor - make an empty multiline label
     **/
    public MultiLineLabel()
    {
	this(null, JLabel.CENTER);
    }
	
    public void setText(String text)
    {
	// clear the existing lines from the panel
	removeAll();
		
	addText(text);
    }
	
    public void addText(String text)
    {
	String strs[] = Utility.splitLines(text);
	JLabel l;

	for (int i = 0; strs != null && i < strs.length; i++)
	    {
		l = new JLabel(strs[i]);
		l.setFont(new Font("SansSerif", fontAttributes, Config.fontsize));
		l.setHorizontalAlignment(alignment);
		add(l);
	    }	
    }
	
    public void setItalic(boolean italic)
    {
	if(italic)
	    fontAttributes |= Font.ITALIC;
	else
	    fontAttributes &= ~Font.ITALIC;
    }
	
    public void setBold(boolean bold)
    {
	if(bold)
	    fontAttributes |= Font.BOLD;
	else
	    fontAttributes &= ~Font.BOLD;
    }
}
