package bluej;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.*;

/**
 * This class is the BlueJ boot loader. bluej.Boot is the class that should be 
 * started to execute BlueJ. No other external classpath settings are necessary. 
 *
 * This loader finds and loads the known BlueJ classes and sets up the classpath.
 * While doing this, it displays a splash screen.
 *
 * @author  Andrew Patterson
 * @author  Damiano Bolla
 * @author  Michael Kolling
 * @author  Bruce Quig
 * @version $Id: Boot.java 2848 2004-08-06 11:29:43Z mik $
 */
public class Boot
{
    public static int BLUEJ_VERSION_MAJOR = 2;
    public static int BLUEJ_VERSION_MINOR = 0;
    public static int BLUEJ_VERSION_RELEASE = 0;
    public static String BLUEJ_VERSION_SUFFIX = " beta";

    public static int BLUEJ_VERSION_NUMBER = BLUEJ_VERSION_MAJOR * 1000 +
                                             BLUEJ_VERSION_MINOR * 100 +
                                             BLUEJ_VERSION_RELEASE;

    public static String BLUEJ_VERSION = BLUEJ_VERSION_MAJOR
                                         + "." + BLUEJ_VERSION_MINOR
                                       //  + "." + BLUEJ_VERSION_RELEASE  // removed for .0.0 release
                                         + BLUEJ_VERSION_SUFFIX;

    public static String BLUEJ_VERSION_TITLE = "BlueJ " + BLUEJ_VERSION;
    
    // A singleton boot object so the rest of BlueJ can pick up args etc.
    private static Boot instance;
    
    // Number of jars generated by the BlueJ build process. These can
    // be ignored during development runs (they must be the first jar files 
    // listed in the following array).
    private static final int bluejBuildJars = 3;
    
    // The jar files we expect in the BlueJ lib directory
    // The first lot are the ones to run BlueJ itself
    private static String[] bluejJars = { "bluejcore.jar", "bluejeditor.jar", "bluejext.jar",
                                          "antlr.jar", "MRJ141Stubs.jar" };
    // The second group are available to user code (and to bluej)
    private static String[] bluejUserJars = { "junit.jar" };
    
    private static boolean useClassesDir = false;

    /**
     * Entry point for booting BlueJ
     *
     * @param  args  The command line arguments
     */
    public static void main(String[] args)
    {
        if((args.length >= 1) && "-version".equals(args[0])) {
            System.out.println("BlueJ version " + BLUEJ_VERSION
                               + " (Java version "
                               + System.getProperty("java.version")
                               + ")");
            System.out.println("--");

            System.out.println("virtual machine: "
                               + System.getProperty("java.vm.name")
                               + " "
                               + System.getProperty("java.vm.version")
                               + " ("
                               + System.getProperty("java.vm.vendor")
                               + ")");

            System.out.println("running on: "
                               + System.getProperty("os.name")
                               + " "
                               + System.getProperty("os.version")
                               + " ("
                               + System.getProperty("os.arch")
                               + ")");
            System.exit(-1);
        }

        SplashWindow splash = new SplashWindow();
        
        if((args.length >= 1) && "-useclassesdir".equals(args[0])) {
            useClassesDir = true;
        }
        
        instance = new Boot(args);
        instance.bootBluej();

        splash.remove();
    }


    /**
     * Returns the singleton Boot instance, so the rest of BlueJ can find paths, args, etc.
     *
     * @return    the singleton Boot object instance
     */
    public static Boot getInstance()
    {
        return instance;
    }


    // ---- instance part ----
    
    private String[] args;      // Command line arguments
    private File javaHomeDir;   // The value returned by System.getProperty
    private File bluejLibDir;   // Calculated below

//    private URL[] bootClassPath;
    private ClassLoader bootLoader; // The loader this class is loaded with

    private URL[] runtimeClassPath; // The class path used to run the rest of BlueJ
    private URL[] runtimeUserClassPath; // The initial class path used to run code within BlueJ
    private URL[] userLibClassPath; // The class path of user libs in the "ext" directory (lib/userlib)
    private URLClassLoader runtimeLoader;   // The class loader used for the rest of BlueJ


    /**
     * Constructor for the singleton Boot object.
     * 
     * @param args
     *            the arguments with which main() was invoked
     */
    private Boot(String[] args)
    {
        this.args = args;
    }


    /**
     * Retuns the args list passed to the starting program.
     *
     * @return    The args value
     */
    public String[] getArgs()
    {
        return args;
    }


    /**
     * Returns the home directory of the java we have been started with
     *
     * @return    The javaHome value
     */
    public File getJavaHome()
    {
        return javaHomeDir;
    }


    /**
     * Returns the BlueJ library directory.
     *
     * @return    The bluejLibDir value
     */
    public File getBluejLibDir()
    {
        return bluejLibDir;
    }


    /**
     * Returns the runtime classpath. The one used to run BlueJ.
     *
     * @return    The runtimeClassPath value
     */
    public URL[] getRuntimeClassPath()
    {
        return runtimeClassPath;
    }

    /**
     * Returns the runtime user classpath. This is available to code within BlueJ.
     *
     * @return    The runtimeUserClassPath value
     */
    public URL[] getRuntimeUserClassPath()
    {
        return runtimeUserClassPath;
    }


    /**
     * Calculate the various path values, create a new classloader and
     * construct a bluej.Main. This needs to be outside the constructor to
     * ensure that the singleton instance is valid by the time
     * bluej.Main is run.
     */
    private void bootBluej()
    {
        // Retrieve the current classLoader, this is the boot loader.
        bootLoader = getClass().getClassLoader();

        // Get the home directory of the Java implementation we're being run by
        javaHomeDir = new File(System.getProperty("java.home"));

        // Now work out what the BlueJ lib directory is.
        bluejLibDir = calculateBluejLibDir();

        try {
            runtimeClassPath = getKnownJars(bluejLibDir, bluejJars, true);
            runtimeUserClassPath = getKnownJars(bluejLibDir, bluejUserJars, false);
            runtimeLoader = new URLClassLoader(runtimeClassPath, bootLoader);
            userLibClassPath = getUserExtLibItems();

            // Construct a bluej.Main object. This starts BlueJ "proper".
            Class mainClass = Class.forName("bluej.Main", true, runtimeLoader);
            Object main = mainClass.newInstance();
            
        } catch (Exception exc) {
            exc.printStackTrace();
        }
    }

    /**
     * Calculate the bluejLibDir value by doing some reasoning on a resource 
     * we know we have: the .class file for the Boot class.
     * For example:
     * bootUrl=jar:file:/C:/home/bluej/bluej/lib/bluej.jar!/bluej/Boot.class
     * bootFullName=file:/C:/home/bluej/bluej/lib/bluej.jar!/bluej/Boot.class
     * bootName=file:/C:/home/bluej/bluej/lib/bluej.jar
     * finalName=/C:/home/bluej/bluej/lib/bluej.jar
     * Parent=C:\home\bluej\bluej\lib
     *
     * @return    the path of the BlueJ lib directory
     */
	private File calculateBluejLibDir()
    {
        File bluejDir = null;
		String bootFullName = getClass().getResource("Boot.class").getFile();

		// Assuming the class is in a jar file, '!' separates the jar file name from the class name.		
		int classIndex = bootFullName.indexOf("!");
		String bootName = null;
		if (classIndex < 0) {
			// Boot.class is not in a jar-file. Find a lib directory somewhere
            // above us to use
            File startingDir = (new File(bootFullName).getParentFile());

            while((startingDir != null) &&
                   !(new File(startingDir.getParentFile(), "lib").isDirectory())) {
                        startingDir = startingDir.getParentFile();
            }
            
            if (startingDir == null)
                bluejDir = null;
            else
                bluejDir = new File(startingDir.getParentFile(), "lib");			
		} else {
			//It was in a jar. Cut of the class name
			bootName = bootFullName.substring(0, classIndex);
			bootName = getURLPath(bootName);

            File finalFile = new File(bootName);
            bluejDir = finalFile.getParentFile();
		}	
		
		return bluejDir;
	}



    /**
     * Return the path element of a URL, properly decoded - that is: replace 
     * each char encoded as "%xx" with its real character.
     */
    private String getURLPath(String url)
    {
        // Get rid of the initial "file:" string
        if (!url.startsWith("file:"))
            throw new IllegalStateException("Unexpected format of jar file URL (class Boot.java): " + url);
        url = url.substring(5);
//        return java.net.URLDecoder.decode(url);
        
        try {
            return java.net.URLDecoder.decode(url, "UTF-8");
        }
        catch(UnsupportedEncodingException exc) {
            return null;
        }
    }

    /**
     * Returns an array of URLs for all the required BlueJ jars
     *
     * @return  URLs of the required JAR files
     * @exception  MalformedURLException  for any problems with the URLs
     */
    private URL[] getKnownJars(File libDir, String[] jars, boolean isSystem) 
        throws MalformedURLException
    {
        // by default, we require all our known jars to be present
        int startJar = 0;
        ArrayList urlList = new ArrayList();

        // a hack to let BlueJ run from within Eclipse.
        // If specified on command line, lets add a ../classes
        // directory to the classpath (where Eclipse stores the
        // .class files)
        if (isSystem && useClassesDir) {
            File classesDir = new File(libDir.getParentFile(), "classes");
            
            if (classesDir.isDirectory()) {
                urlList.add(classesDir.toURL());
                // skip over requiring bluejcore.jar, bluejeditor.jar etc.
                startJar = bluejBuildJars;
            }
        }

        for (int i=startJar; i < jars.length; i++) {
            File toAdd = new File(libDir, jars[i]);

            if (!toAdd.canRead())
                throw new IllegalStateException("required jar is missing or unreadable: " + toAdd);

            urlList.add(toAdd.toURL());
        }
    
        if (isSystem) {
            // We also need to add tools.jar on some systems
            URL toolsURL = getToolsURL();
            if(toolsURL != null)
                urlList.add(toolsURL);
        }
        return (URL[]) urlList.toArray(new URL[0]);
    }
    
    
    /**
     * Returns an array of URLs for all the JAR files located in the lib/userlib directory
     *
     * @return  URLs of the discovered JAR files
     * @exception  MalformedURLException  for any problems with the URLs
     */
    private URL[] getUserExtLibItems() throws MalformedURLException
    {
        File userLibDir = new File(bluejLibDir, "userlib");

        File[] files = userLibDir.listFiles();
        if (files == null) {
            return new URL[0];
        }
        
        ArrayList urlList = new ArrayList();
        for (int index = 0; index < files.length; index++) {
            File thisFile = files[index];

            // Skip nested directories
            if (thisFile.isDirectory())
                continue;

            // Skip files that do not end in .jar or .zip
            if (!hasValidExtension(thisFile))
                continue;

            // This one looks good, add it to the list.
            urlList.add(thisFile.toURL());
        }
 
        return (URL[]) urlList.toArray(new URL[0]);
    }
    
    /**
     * Return the classpath for valid libs (jars & zips) 
     * in the lib/userlib directory
     * @return the classpath for valid libs
     */
    public URL[] getUserLibClassPath()
    {
        return userLibClassPath;
    }

    /**
     * Try to decide if this filename has the right extension to be a
     * library
     *
     * @param  aFile  the File to be checked
     * @return  true if the File could be library
     */
    private boolean hasValidExtension(File aFile)
    {
        if (aFile == null)
            return false;

        // If it ends in jar it is good.
        if (aFile.getName().endsWith(".jar"))
            return true;

        // if it ends in zip also
        if (aFile.getName().endsWith(".zip"))
            return true;

        return false;
    }


    /**
     * Get the URL of the  current tools.jar file
     * Looks for lib/tools.jar in the current javaHome
     * and in the parent of it.
     * tools.jar is needed on many (but not all!) systems. Currently, 
     * MacOS is the only system known to us without a tools URL, but 
     * there may be others in the furure. This method returns null
     * if tools.jar does not exist.
     *
     * @return   The URL of the tools.jar file for the current Java implementation, or null.
     * @exception  MalformedURLException  for any problems with the URL
     */
    private URL getToolsURL() 
        throws MalformedURLException
    {
        String osname = System.getProperty("os.name", "");
        if(osname.startsWith("Mac"))     // we know it does not exist on a Mac...
            return null;

        File toolsFile = new File(javaHomeDir, "lib/tools.jar");
        if (toolsFile.canRead())
            return toolsFile.toURL();

        File parentDir = javaHomeDir.getParentFile();
        toolsFile = new File(parentDir, "lib/tools.jar");
        if (toolsFile.canRead())
            return toolsFile.toURL();
        else {
            // on other systems where we don't find it, we just warn. We don't expect it
            // to happen, but you never know...
            System.err.println("class Boot: tools.jar not found. Potential problem for execution.");
            return null;
        }
    }
}
