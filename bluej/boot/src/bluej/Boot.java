/*
 This file is part of the BlueJ program. 
 Copyright (C) 1999-2009,2010,2011  Michael Kolling and John Rosenberg 
 
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
package bluej;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Properties;

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
 */
public class Boot
{

    // The version numbers for BlueJ are changed in the BlueJ build.xml
    // and then the update-version target should be executed.
    public static final int BLUEJ_VERSION_MAJOR = 3;
    public static final int BLUEJ_VERSION_MINOR = 0;
    public static final int BLUEJ_VERSION_RELEASE = 4;
    public static final String BLUEJ_VERSION_SUFFIX = "";

    // public static final int BLUEJ_VERSION_NUMBER = BLUEJ_VERSION_MAJOR * 1000 +
    //                                                BLUEJ_VERSION_MINOR * 100 +
    //                                                BLUEJ_VERSION_RELEASE;

    public static final String BLUEJ_VERSION = BLUEJ_VERSION_MAJOR
                                         + "." + BLUEJ_VERSION_MINOR
                                         + "." + BLUEJ_VERSION_RELEASE
                                         + BLUEJ_VERSION_SUFFIX;

    public static final String BLUEJ_VERSION_TITLE = "BlueJ " + BLUEJ_VERSION;
    
    // The version numbers for Greenfoot are changed in the Greenfoot build.xml
    // and then the update-version target should be executed.
    public static String GREENFOOT_VERSION = "2.0.1";
    public static String GREENFOOT_API_VERSION = "2.2.0";
    
    // A singleton boot object so the rest of BlueJ can pick up args etc.
    private static Boot instance;
        
    // The jar files we expect in the BlueJ lib directory
    // The first lot are the ones to run BlueJ itself
    private static String[] bluejJars = { "bluejcore.jar", "bluejeditor.jar", "bluejext.jar",
                                          "AppleJavaExtensions.jar", "org-netbeans-lib-cvsclient.jar",
                                          "svnkit-javahl.jar", "svnkit.jar", "trilead.jar"};

    // Number of jars in above list generated by the BlueJ build process.
    // These can be ignored during development runs (they must be the first
    // jar files  listed in the array).
    private static final int bluejBuildJars = 3;

    // The second group are available to user code (and to bluej)
    // bluejcore.jar is necessary as it contains the support runtime
    // (bluej.runtime.* classes).
    private static final String[] bluejUserJars = { "bluejcore.jar", "junit-4.8.2.jar" };

    // The number of jar files in the user jars which are built from the
    // BlueJ classes directory
    private static final int bluejUserBuildJars = 1;
    
    // In greenfoot we need access to the BlueJ classes.
    // When running from eclipse, the first jar files will be excluded as explained above at the bluejBuildJars field.
    private static final String JLAYER_MP3_JAR = "jl1.0.1.jar";
    private static final String[] greenfootUserJars = {"extensions" + File.separatorChar + "greenfoot.jar", 
        "bluejcore.jar", "bluejeditor.jar", "bluejext.jar",
        "AppleJavaExtensions.jar", "junit-4.8.2.jar", "bluej.jar",
        "commons-httpclient-3.1.jar", "commons-logging-api-1.1.1.jar",
        "commons-codec-1.3.jar", JLAYER_MP3_JAR};

    // Jars that should be included with exported scenarios
    public static final String[] GREENFOOT_EXPORT_JARS = {JLAYER_MP3_JAR};
    
    private static final int greenfootUserBuildJars = 4;
    
    // The variable form of the above
    private static String [] runtimeJars = bluejJars;
    private static String [] userJars = bluejUserJars;
    private static int numBuildJars = bluejBuildJars;
    private static int numUserBuildJars = bluejUserBuildJars;
    
    private static boolean isGreenfoot = false;
    private static File bluejLibDir; 

    private SplashWindow splashWindow;
    
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

        Properties commandLineProps = processCommandLineProperties(args);
        isGreenfoot = commandLineProps.getProperty("greenfoot", "false").equals("true");
        
        SplashLabel image = null;
        if(isGreenfoot) {
            image = new GreenfootLabel();
            runtimeJars = greenfootUserJars;
            userJars = greenfootUserJars;
            numBuildJars = greenfootUserBuildJars;
            numUserBuildJars = greenfootUserBuildJars;
        } else {
            image = new BlueJLabel();
        }

        try {
            instance = new Boot(args, commandLineProps, image);
            instance.bootBluej();
        }
        catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
        
        // Make sure we don't return until the VM is exited
        synchronized (instance) {
            while (true) {
                try {
                    instance.wait();
                }
                catch (InterruptedException ie) {}
            }
        }
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
    private Properties commandLineProps; //Properties specified a the command line (-....)
    private String[] args;      // Command line arguments
    private File javaHomeDir;   // The value returned by System.getProperty
  
    private ClassLoader bootLoader; // The loader this class is loaded with

    private URL[] runtimeUserClassPath; // The initial class path used to run code within BlueJ
    private URL[] runtimeClassPath;     // The class path containing all the BlueJ classes


    /**
     * Constructor for the singleton Boot object.
     * 
     * @param args the arguments with which main() was invoked
     * @param props the properties (created from the args)
     */
    private Boot(String[] args, Properties props, SplashLabel image)
    {
        // Display the splash window, and wait until it's been painted before
        // proceeding. Otherwise, the event thread may be occupied by BlueJ
        // starting up and the window might *never* be painted.
        splashWindow = new SplashWindow(image);
        splashWindow.repaint(); // avoid delay before painting
        splashWindow.waitUntilPainted();

        this.args = args;
        this.commandLineProps = props;
    }

    /**
     * Hide (and dispose) the splash window
     */
    public void disposeSplashWindow()
    {
        splashWindow.dispose();
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
     * Return true if we are booting Greenfoot.
     */
    public boolean isGreenfoot()
    {
        return isGreenfoot;
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
    public static File getBluejLibDir()
    {
        if(bluejLibDir == null) {
            bluejLibDir = calculateBluejLibDir();
        }
        return bluejLibDir;
    }

    /**
     * Returns the runtime classpath. This contains all the classes for BlueJ.
     *
     * @return    The runtimeClassPath value.
     */
    public URL[] getRuntimeClassPath()
    {
        return runtimeClassPath;
    }
    
    /**
     * Returns the runtime user classpath. This is available to code within BlueJ.
     *
     * @return    The runtimeUserClassPath value.
     */
    public URL[] getRuntimeUserClassPath()
    {
        return runtimeUserClassPath;
    }

    /**
     * Returns the boot class loader, the one that is used to load this class.
     *
     * @return The bootClassLoader value.
     */
    public ClassLoader getBootClassLoader ()
    {
        return bootLoader;
    }

    /**
     * Calculate the various path values, create a new classloader and
     * construct a bluej.Main. This needs to be outside the constructor to
     * ensure that the singleton instance is valid by the time
     * bluej.Main is run.
     */
    private void bootBluej()
    {
        initializeBoot();
        try {
            URLClassLoader runtimeLoader = new URLClassLoader(runtimeClassPath, bootLoader);
 
            // Construct a bluej.Main object. This starts BlueJ "proper".
            Class<?> mainClass = Class.forName("bluej.Main", true, runtimeLoader);
            mainClass.newInstance();
            
        } catch (Exception exc) {
            exc.printStackTrace();
        }
    }
    
    private void initializeBoot()
    {
        // Retrieve the current classLoader, this is the boot loader.
        bootLoader = getClass().getClassLoader();

        // Get the home directory of the Java implementation we're being run by
        javaHomeDir = new File(System.getProperty("java.home"));

        try {
            runtimeClassPath = getKnownJars(getBluejLibDir(), runtimeJars, true, numBuildJars);
            runtimeUserClassPath = getKnownJars(getBluejLibDir(), userJars, false, numUserBuildJars);
        }
        catch (Exception exc) {
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
    private static File calculateBluejLibDir()
    {
        File bluejDir = null;
        String bootFullName = Boot.class.getResource("Boot.class").toString();

        try {
            if (! bootFullName.startsWith("jar:")) {
                // Boot.class is not in a jar-file. Find a lib directory somewhere
                // above us to use
                File startingDir = (new File(new URI(bootFullName)).getParentFile());
                while((startingDir != null) &&
                        !(new File(startingDir.getParentFile(), "lib").isDirectory())) {
                    startingDir = startingDir.getParentFile();
                }
                
                if (startingDir == null) {
                    bluejDir = null;
                }
                else {
                    bluejDir = new File(startingDir.getParentFile(), "lib");
                }
            }
            else {
                // The class is in a jar file, '!' separates the jar file name
                // from the class name. Cut off the class name and the "jar:" prefix.
                int classIndex = bootFullName.indexOf("!");
                String bootName = bootFullName.substring(4, classIndex);
                
                File finalFile = new File(new URI(bootName));
                bluejDir = finalFile.getParentFile();
            }   
        } 
        catch (URISyntaxException use) { }
        
        return bluejDir;
    }

    /**
     * Returns an array of URLs for all the required BlueJ jars
     *
     * @param libDir  the BlueJ "lib" dir (where the jars are stored)
     * @param jars    the names of the jar files whose urls to add in the
     *                returned list
     * @param isSystem  True if tools.jar should be included in the returned
     *                  list, on systems that need it
     * @param numBuildJars  The number of jar files in the jars array which
     *                  are built from the BlueJ source. If running from eclipse
     *                  these can be replaced with a single entry - the classes
     *                  directory.
     * 
     * @return  URLs of the required JAR files
     * @exception  MalformedURLException  for any problems with the URLs
     */
    private URL[] getKnownJars(File libDir, String[] jars, boolean isSystem, int numBuildJars) 
        throws MalformedURLException
    {
        boolean useClassesDir = commandLineProps.getProperty("useclassesdir", "false").equals("true");
        
        // by default, we require all our known jars to be present
        int startJar = 0;
        ArrayList<URL> urlList = new ArrayList<URL>();

        // a hack to let BlueJ run from within Eclipse.
        // If specified on command line, lets add a ../classes
        // directory to the classpath (where Eclipse stores the
        // .class files)
        if (numBuildJars != 0 && useClassesDir) {
            File classesDir = new File(libDir.getParentFile(), "classes");
            
            if (classesDir.isDirectory()) {
                urlList.add(classesDir.toURI().toURL());
                if (isGreenfoot) {
                    String gfClassesDir = commandLineProps.getProperty("greenfootclassesdir");
                    if (gfClassesDir != null) {
                        classesDir = new File(gfClassesDir);
                        urlList.add(classesDir.toURI().toURL());
                    }
                }
                
                // skip over requiring bluejcore.jar, bluejeditor.jar etc.
                startJar = numBuildJars;
            }
        }

        for (int i=startJar; i < jars.length; i++) {
            File toAdd = new File(libDir, jars[i]);
            
            // No need to throw exception at this point; we will get
            // a ClassNotFoundException or similar if there is really a
            // problem.
            //if (!toAdd.canRead())
            //    throw new IllegalStateException("required jar is missing or unreadable: " + toAdd);

            if (toAdd.canRead())
                urlList.add(toAdd.toURI().toURL());
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
            return toolsFile.toURI().toURL();

        File parentDir = javaHomeDir.getParentFile();
        toolsFile = new File(parentDir, "lib/tools.jar");
        if (toolsFile.canRead())
            return toolsFile.toURI().toURL();
        else {
            // on other systems where we don't find it, we just warn. We don't expect it
            // to happen, but you never know...
            System.err.println("class Boot: tools.jar not found. Potential problem for execution.");
            return null;
        }
    }
    
    /**
     * Analyse and process command line specified properties.
     * Properties can be specified with -... command line options. For example: -bluej.debug=true
     * 
     * @param args The command line parameters
     * @return The property object
     */
    private static Properties processCommandLineProperties(String[] args)
    {
        Properties props = new Properties();

        for(int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("-"))
                continue;
            
            String definition = args[i].substring(1);
            int definitionEquals = definition.indexOf('=');
            
            if (definitionEquals < 0)
                continue;
            
            String propName = definition.substring(0, definitionEquals); 
            String propValue = definition.substring(definitionEquals+1);
            
            if (!propName.equals("") && !propValue.equals(""))
                props.put(propName, propValue);
        }
        return props;
    }

    /**
     * Returns command line specified properties. <br>
     * 
     * Properties can be specified with -... command line options. For example: -bluej.debug=true
     */
    public Properties getCommandLineProperties()
    {
        return commandLineProps;
    }
    
}
