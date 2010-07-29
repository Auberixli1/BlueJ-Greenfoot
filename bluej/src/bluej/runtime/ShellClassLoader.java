/*
 This file is part of the BlueJ program. 
 Copyright (C) 2010  Michael Kolling and John Rosenberg 
 
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
package bluej.runtime;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/**
 * A classloader which loads only a "shell" class (a class generated by BlueJ itself), and
 * which disrupts the normal classloader delegation model to do so.
 * 
 * <p>A ShellClassLoader is used only once. The benefit is that it can be throw away and
 * garbage collected; this means that the class it was responsible for loading can also be
 * garbage collected.
 * 
 * @author Davin McCall
 */
public class ShellClassLoader extends ClassLoader
{
    private String className;
    
    public ShellClassLoader(ClassLoader parent, String className)
    {
        super(parent);
        this.className = className;
    }
    
    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve)
            throws ClassNotFoundException
    {
        Class<?> c = findLoadedClass(name);
        if (c != null) {
            return c;
        }
        
        try {
            // To account for inner classes, extract the base name
            String baseName = name;
            int firstDollar = name.indexOf('$');
            if (firstDollar != -1) {
                baseName = name.substring(0, firstDollar);
            }
            
            // If the basename matches *our* class, load it ourselves
            if (baseName.equals(className)) {
                String filename = name.replace('.', File.separatorChar) + ".class";
                File f = new File(filename);
                if (f.canRead()) {
                    int length = (int) f.length();
                    byte [] buf = new byte[length];
                    InputStream is = new FileInputStream(f);
                    int offset = 0;
                    while (length > 0) {
                        int read = is.read(buf, offset, length);
                        if (read < 0) {
                            break;
                        }
                        offset += read;
                        length -= read;
                    }
                    
                    return defineClass(name, buf, 0, offset);
                }
            }
        }
        catch (FileNotFoundException fnfe) {}
        catch (IOException ioe) {}
        
        return super.loadClass(name, resolve);
    }
}
