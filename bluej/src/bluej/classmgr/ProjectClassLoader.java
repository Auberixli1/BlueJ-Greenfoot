package bluej.classmgr;

import java.io.*;
import java.util.*;
import java.net.*;

import bluej.utility.Debug;
import bluej.Config;

/**
 * A class loader that will load classes from the current directory
 * and from jar files within a +libs directory.
 *
 * @author  Andrew Patterson
 * @version $Id: ProjectClassLoader.java 1053 2001-12-19 06:31:58Z ajp $
 */
public class ProjectClassLoader extends URLClassLoader
{
    private String libsString = "+libs";

    public ProjectClassLoader(File projectDir)
    {
        this(projectDir, ClassLoader.getSystemClassLoader());
    }

    /**
     * Construct a class loader that load classes from the
     * directory projectDir using the parent class loader parent.
     */
    public ProjectClassLoader(File projectDir, ClassLoader parent)
    {
        super(getDirectoryAsURL(projectDir), parent);

	// the subdirectory of the project which can hold project specific
	// jars and zips
	File libsDirectory = new File(projectDir, libsString);

	// the list of jars and zips we find
	File libsJars[] = null;

	if (libsDirectory.isDirectory()) {
            libsJars = libsDirectory.listFiles(new JarFilter());
	}

	// if we found any jar files in the libs directory then add their
	// URLs
	if (libsJars != null) {
            for(int i=0; i<libsJars.length; i++) {
                try {
                    addURL(libsJars[i].toURL());
                }
                catch(MalformedURLException mue) { }
            }
	}
    }

    /**
     * Construct and return a ClassPath representing all the entries
     * managed by this class loader
     */
    public ClassPath getAsClassPath()
    {
	return new ClassPath(getURLs());
    }

    /**
     * Turns a directory File object into an array of length 1
     * containing a single URL. This is a helper function for
     * the constructor of this class.
     */
    private static URL[] getDirectoryAsURL(File projectDir)
    {
	if (!projectDir.isDirectory())
	    throw new IllegalArgumentException("project directory was not a directory");

	// the project directory is always added as a URL
        try {
            URL urls[] = { projectDir.toURL() };
	    return urls;
        }
        catch(MalformedURLException mue) { }

        URL blankUrls[] = { };

        return blankUrls; 
    }

}

/**
 * A FileFilter that only accepts jar and zip files.
 */
class JarFilter implements FileFilter
{
    /**
     * This method only accepts files that are jar or zip files.
     */
    public boolean accept(File pathname)
    {
	String name = pathname.getName().toLowerCase();

	return pathname.isFile() &&
	    (name.endsWith(".zip") || name.endsWith(".jar"));
    }
}

