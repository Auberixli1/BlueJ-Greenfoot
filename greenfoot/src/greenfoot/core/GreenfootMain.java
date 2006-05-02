package greenfoot.core;

import greenfoot.ActorVisitor;
import greenfoot.event.ActorInstantiationListener;
import greenfoot.event.CompileListener;
import greenfoot.event.CompileListenerForwarder;
import greenfoot.gui.GreenfootFrame;
import greenfoot.gui.MessageDialog;
import greenfoot.util.GreenfootUtil;
import greenfoot.util.Version;

import java.awt.Frame;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.swing.JButton;
import javax.swing.SwingUtilities;

import rmiextension.wrappers.RBlueJ;
import rmiextension.wrappers.RPackage;
import rmiextension.wrappers.RProject;
import rmiextension.wrappers.event.RInvocationListener;
import bluej.Config;
import bluej.debugmgr.CallHistory;
import bluej.extensions.CompilationNotStartedException;
import bluej.extensions.PackageNotFoundException;
import bluej.extensions.ProjectNotOpenException;
import bluej.utility.FileUtility;
import bluej.utility.Utility;
import java.awt.Point;

/**
 * The main class for greenfoot. This is a singelton (in the JVM). Since each
 * project is opened in its own JVM there can be several Greenfoot instances,
 * but each will be in its own JVM so it is effectively a singleton.
 * 
 * @author Poul Henriksen <polle@mip.sdu.dk>
 * @version $Id: GreenfootMain.java 4071 2006-05-02 13:14:23Z mik $
 */
public class GreenfootMain
{
    /** Greenfoot is a singleton - this is the instance. */
    private static GreenfootMain instance;

    /** Used to enable logging. */
    private transient final static Logger logger = Logger.getLogger("greenfoot");

    /** The connection to BlueJ via RMI */
    private RBlueJ rBlueJ;

    /** The main frame of greenfoot. */
    private GreenfootFrame frame;

    /** The project this Greenfoot singelton refers to. */
    private GProject project;

    /** The package this Greenfoot singelton refers to. */
    private GPackage pkg;

    /** Map of class names to images */
    private Map classImages = new HashMap();

    /**
     * Forwards compile events to all the compileListeners that has registered
     * to reccieve compile events.
     */
    private CompileListenerForwarder compileListenerForwarder;
    private List<CompileListener> compileListeners = new ArrayList<CompileListener>();

    /** Listens for instantiations of Actor objects. */
    private ActorInstantiationListener instantiationListener;

    /** List of invocation listeners that has been registered. */
    private List<RInvocationListener> invocationListeners = new ArrayList<RInvocationListener>();

    /** History of parameters passed to methods. */
    private CallHistory callHistory = new CallHistory();

    /** Filter that matches class files */
    private static FilenameFilter classFilter = new FilenameFilter() {
        public boolean accept(File dir, String name)
        {
            return name.toLowerCase().endsWith(".class");
        }
    };

    
    // ----------- static methods ------------

    /**
     * Initializes the singleton. This can only be done once - subsequent calls
     * will have no effect.
     */
    public static void initialize(RBlueJ rBlueJ, RPackage pkg)
    {
        if (instance == null) {
            instance = new GreenfootMain(rBlueJ, pkg);
            System.setProperty("apple.laf.useScreenMenuBar", "true");
        }
    }


    /**
     * Gets the singleton.
     * 
     */
    public static GreenfootMain getInstance()
    {
        return instance;
    }


    /**
     * Gets the properties for the greenfoot project run on this copy of 
     * greenfoot.
     */
    public static ProjectProperties getProjectProperties()
    {
        return instance.getProject().getProjectProperties();
    }


    // ----------- instance methods ------------

    /**
     * Contructor is private. This class is initialised via the 'initialize'
     * method (below).
     */
    private GreenfootMain(final RBlueJ rBlueJ, final RPackage pkg)
    {
        instance = this;
        this.rBlueJ = rBlueJ;
        try {
            this.pkg = new GPackage(pkg);
            this.project = this.pkg.getProject();
            // Config is initialized in GreenfootLauncher
        }
        catch (RemoteException e) {
            e.printStackTrace();
        }
        catch (ProjectNotOpenException e) {
            e.printStackTrace();
        }

        // Threading avoids deadlock when classbrowser tries to instantiate
        // objects to get images. this is necessy because greenfoot is started
        // from BlueJ-VM which waits for this call to return.
        final GProject finalProject = project;
        Thread t = new Thread() {
            public void run()
            {
                long t1 = System.currentTimeMillis();

                try {
                    frame = new GreenfootFrame(GreenfootMain.this.rBlueJ, finalProject);
                }
                catch (ProjectNotOpenException e2) {
                    e2.printStackTrace();
                }
                catch (RemoteException e2) {
                    e2.printStackTrace();
                }
                logger.info("Frame created");

                frame.setVisible(true);
                Utility.bringToFront();
                logger.info("Frame visible");

                try {
                    instantiationListener = new ActorInstantiationListener(WorldHandler.instance());
                    GreenfootMain.this.rBlueJ.addInvocationListener(instantiationListener);
                    compileListenerForwarder = new CompileListenerForwarder(compileListeners);
                    GreenfootMain.this.rBlueJ.addCompileListener(compileListenerForwarder, pkg.getProject().getName());
                }
                catch (RemoteException e) {
                    e.printStackTrace();
                }
                catch (ProjectNotOpenException e) {
                    e.printStackTrace();
                }
                long t2 = System.currentTimeMillis();
                logger.info("Creation of frame took: " + (t2 - t1));
            }
        };
        SwingUtilities.invokeLater(t);
    }


    /**
     * Opens the project in the given directory.
     * 
     * @param projectDir
     */
    public void openProject(String projectDir)
        throws RemoteException
    {
        boolean doOpen = GreenfootMain.updateApi(new File(projectDir), rBlueJ.getSystemLibDir(), frame);
        if (doOpen) {
            rBlueJ.openProject(projectDir);
        }

    }

    
    /**
     * Opens a file browser to find a greenfoot project
     * 
     */
    public void openProjectBrowser()
    {
        File dirName = FileUtility.getPackageName(frame);

        if (dirName != null) {
            try {
                openProject(dirName.getAbsolutePath());
            }
            catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Gets the package for this.
     */
    public GPackage getPackage()
    {
        return pkg;
    }

    public GProject getProject()
    {
        return project;
    }

    /**
     * Closes this greenfoot frame
     * 
     * TODO This sometimes leaves proceses hanging? Seems to be fixed with later
     * BlueJ versions
     */
    public void closeThisInstance()
    {
        try {
            logger.info("closeThisInstance(): " + project.getName());
            rBlueJ.removeCompileListener(compileListenerForwarder);
            rBlueJ.removeInvocationListener(instantiationListener);
            storeFrameState();
            for (RInvocationListener element : invocationListeners) {
                rBlueJ.removeInvocationListener(element);
            }
            if (rBlueJ.getOpenProjects().length <= 1) {
                // Close everything
                // TODO maybe open dummy project instead

                // And then exit greenfoot
                logger.info("exit greenfoot");
                rBlueJ.exit();
            }
            else {
                logger.info("closing project: " + project.getName());
                project.close();
            }
        }
        catch (ProjectNotOpenException e) {
            e.printStackTrace();
        }
        catch (RemoteException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Store the current main window size to the project properties.
     */
    private void storeFrameState()
    {
        ProjectProperties projectProperties = getProject().getProjectProperties();
        
        projectProperties.setInt("mainWindow.width", frame.getWidth());
        projectProperties.setInt("mainWindow.height", frame.getHeight());
        Point loc = frame.getLocation();
        projectProperties.setInt("mainWindow.x", loc.x);
        projectProperties.setInt("mainWindow.y", loc.y);

        projectProperties.setInt("simulation.speed", Simulation.getInstance().getDelay());
        
        projectProperties.save();
    }

    /**
     * Adds a listener for compile events
     * 
     * @param listener
     */
    public void addCompileListener(CompileListener listener)
    {
        compileListeners.add(listener);
    }

    /**
     * removes a listener for compile events
     * 
     * @param listener
     */
    public void removeCompileListener(CompileListener listener)
    {
        compileListeners.remove(listener);
    }

    /**
     * Adds a listener for invocation events
     * 
     * @param listener
     */
    public void addInvocationListener(RInvocationListener listener)
        throws RemoteException
    {
        invocationListeners.add(listener);
        rBlueJ.addInvocationListener(listener);
    }

    /**
     * Compiles all files
     */
    public void compileAll()
    {
        try {
            // we recompile all files in ccase something has been modified
            // externally and the isCompiled state is no longer up-to-date.
            pkg.compileAll(false);
        }
        catch (ProjectNotOpenException e) {
            e.printStackTrace();
        }
        catch (PackageNotFoundException e) {
            e.printStackTrace();
        }
        catch (RemoteException e) {
            e.printStackTrace();
        }
        catch (CompilationNotStartedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a new project
     */
    public void newProject()
    {
        String newname = FileUtility.getFileName(frame, Config.getString("pkgmgr.newPkg.title"), Config
                .getString("pkgmgr.newPkg.buttonLabel"), false, null, true);
        if (newname != null) {
            try {
                File f = new File(newname);
              /*  f.mkdir();
                // Make sure that the new project has a project version.
                ProjectProperties newProperties = new ProjectProperties(f);
                newProperties.storeApiVersion();*/
                RProject newProject = rBlueJ.newProject(f);
                // The rest of the project preparation will be done by the
                // ProjectLauncher on the BlueJ side.
            }
            catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Get a reference to the CallHistory instance.
     */
    public CallHistory getCallHistory()
    {
        return callHistory;
    }

    /**
     * Get a reference to the invocation listener.
     */
    public ActorInstantiationListener getInvocationListener()
    {
        return instantiationListener;
    }

    /**
     * Get a reference to the greenfoot frame.
     */
    public GreenfootFrame getFrame()
    {
        return frame;
    }


    // ========= Private methods ==========

    /**
     * Makes a project a greenfoot project. That is, copy the system classes to
     * the users library.
     * 
     * @param projectDir absolute path to the project
     */
    private static void prepareGreenfootProject(File blueJLibDir, File projectDir)
    {
        if (isStartupProject(blueJLibDir, projectDir)) {
            logger.info("GreenfootLauncher: This is startupProject... ");
            return;
        }

        File src = new File(blueJLibDir, "skeletonProject");
        File dst = projectDir;

        validateClassFiles(src, dst);
        GreenfootUtil.copyDir(src, dst);
        ProjectProperties newProperties = new ProjectProperties(projectDir);
        newProperties.setApiVersion();
        newProperties.save();
    }

    /**
     * Checks whether the API version this project was created with is
     * compatible with the current API version. If it is not, it will attempt to
     * update the project to the current version of the API and present the user
     * with a dialog with instructions on what to do if there is a changes in
     * API version that requires manual modifications of the API.
     * <p>
     * If is considered safe to open this project with the current API version
     * the method will return true. value will be 'true'.
     * 
     * @param project The project in question
     * @return True If we should try to open the project.
     * @throws RemoteException
     */
    public static boolean updateApi(File projectDir, File systemLibDir, Frame parent)
        throws RemoteException
    {
        ProjectProperties newProperties = new ProjectProperties(projectDir);
        Version projectVersion = newProperties.getAPIVersion();

        Version apiVersion = GreenfootMain.getAPIVersion();

        if (projectVersion.equals(apiVersion)) {
            GreenfootMain.prepareGreenfootProject(systemLibDir, projectDir);
            return true;
        }

        if (projectVersion == Version.NO_VERSION) {
            String message = "The project that you are trying to open appears to be an old greenfoot project (before greenfoot version 0.5). This will most likely result in some errors that will have to be fixed manually.";
            JButton continueButton = new JButton("Continue");
            MessageDialog dialog = new MessageDialog(parent, message, "Versions does not match", 50,
                    new JButton[]{continueButton});
            dialog.displayModal();
            System.out.println(message);
            GreenfootMain.prepareGreenfootProject(systemLibDir, projectDir);
            return true;
        }
        else if (projectVersion.compareTo(apiVersion) < 0) {
            String message = "The project that you are trying to open appears to be an old greenfoot project (API version " + projectVersion
                    + "). The project will be updated to the current version (API version " + apiVersion
                    + "), but it might require some manual fixing of errors due to API changes.";
            JButton continueButton = new JButton("Continue");
            MessageDialog dialog = new MessageDialog(parent, message, "Versions does not match", 50,
                    new JButton[]{continueButton});
            dialog.displayModal();
            GreenfootMain.prepareGreenfootProject(systemLibDir, projectDir);
            return true;
        }
        else if (projectVersion.compareTo(apiVersion) > 0) { //
            String message = "The project that you are trying to open appears to be a greenfoot project created with"
                    + "a newer version of the Greenfoot API (version " + projectVersion + ")."
                    + "Opening the project with this version might result in"
                    + "some errors that will have to be fixed manually." + "\n \n"
                    + "Do you want to continue opening the project?";

            JButton cancelButton = new JButton("Cancel");
            JButton continueButton = new JButton("Continue");
            MessageDialog dialog = new MessageDialog(parent, message, "Versions does not match", 50, new JButton[]{
                    continueButton, cancelButton});
            JButton pressed = dialog.displayModal();
            if (pressed == cancelButton) {
                return false;
            }
            else {
                prepareGreenfootProject(systemLibDir, projectDir);
                return true;
            }
        }
        else {
            String message = "This is not a Greenfoot project: " + projectDir;
            JButton continueButton = new JButton("Continue");
            MessageDialog dialog = new MessageDialog(parent, message, "Versions does not match", 50,
                    new JButton[]{continueButton});
            dialog.displayModal();
            return false;
        }

    }

    /**
     * Checks whether the old and new source files for Actor and World are the
     * same. If they are not, the class files are deleted.
     */
    public static void validateClassFiles(File src, File dst)
    {
        File newActor = new File(src, "greenfoot/Actor.java");
        File oldActor = new File(dst, "greenfoot/Actor.java");
        File actorClassFile = new File(dst, "greenfoot/Actor.class");

        File newWorld = new File(src, "greenfoot/World.java");
        File oldWorld = new File(dst, "greenfoot/World.java");
        File gwClassFile = new File(dst, "greenfoot/World.class");

        if (!sameFileContents(newActor, oldActor) || !sameFileContents(newWorld, oldWorld)) {

            deleteClassFiles(dst);

            File greenfootDir = new File(dst, "greenfoot");
            // the greenfoot dir does not necessarily exist
            if (greenfootDir.canRead()) {
                deleteClassFiles(greenfootDir);
            }
        }
    }

    /**
     * Deletes all class files in the given directory.
     * 
     * @param dir The directory MUST exist
     */
    private static void deleteClassFiles(File dir)
    {
        System.out.println("deleting classes in:" + dir);
        String[] classFiles = dir.list(classFilter);
        for (int i = 0; i < classFiles.length; i++) {
            String fileName = classFiles[i];
            File file = new File(dir, fileName);
            file.delete();
        }
    }

    private static boolean sameFileContents(File f1, File f2)
    {
        if (f1.canRead() && f2.canRead()) {
            if (f1.length() != f2.length()) {
                return false;
            }
            try {
                FileReader reader1 = new FileReader(f1);
                FileReader reader2 = new FileReader(f2);
                int read1;

                try {
                    while ((read1 = reader1.read()) != -1) {
                        int read2 = reader2.read();
                        if (read1 != read2) {
                            return false;
                        }
                    }
                    return true;
                }
                catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            catch (FileNotFoundException e) {}
        }
        return false;
    }

    /**
     * Checks if the project is the default startup project that is used when no
     * other project is open. It is necessary to have this dummy project,
     * becuase we must have a project in order to launch the DebugVM.
     * 
     */
    public static boolean isStartupProject(File blueJLibDir, File projectDir)
    {
        File startupProject = new File(blueJLibDir, "startupProject");
        if (startupProject.equals(projectDir)) {
            return true;
        }

        return false;
    }

    /**
     * Gets the version number of the greenfoot API.
     * 
     * @return
     */
    public static Version getAPIVersion()
    {
        return ActorVisitor.getApiVersion();
    }
}