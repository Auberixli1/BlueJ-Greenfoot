package bluej.pkgmgr;

import bluej.Config;
import bluej.BlueJEvent;
import bluej.BlueJEventListener;
import bluej.pkgmgr.Package;
import bluej.utility.Utility;
import bluej.utility.FileUtility;
import bluej.utility.Debug;
import bluej.utility.DialogManager;
import bluej.utility.JavaNames;
import bluej.debugger.*;
import bluej.classmgr.*;
import bluej.views.View;
import bluej.tester.*;

import java.util.*;
import java.io.File;
import java.io.IOException;

import junit.swingui.TestRunner;
import junit.runner.TestSuiteLoader;
import junit.framework.*;

/**
 * A BlueJ Project.
 *
 * @author  Michael Kolling
 * @author  Axel Schmolitzky
 * @author  Andrew Patterson
 * @author  Bruce Quig
 * @version $Id: Project.java 1053 2001-12-19 06:31:58Z ajp $
 */
public class Project
    implements BlueJEventListener
{
    // static fields

    private static final String saveAsTitle =
        Config.getString("pkgmgr.saveAs.title");
    private static final String saveLabel =
        Config.getString("pkgmgr.saveAs.buttonLabel");

    /**
     * collection of all open projects. the canonical name of the project
     * directory is used as the key.
     */
    private static Map projects = new HashMap();

    /**
     * Open a BlueJ project.
     *
     * @param   A string representing the path to open. This can either
     *          be a directory name or the filename of a bluej.pkg file.
     * @return  The Project representing the BlueJ project
     *          that has this directory within it or
     *          null if there were no bluej.pkg files in the
     *          specified directory.
     */
    public static Project openProject(String projectPath)
    {
        String startingPackageName;
        File projectDir, startingDir;

        try {
            startingDir = new File(projectPath).getCanonicalFile();

            /* allow a bluej.pkg file to be specified. In this case,
               we immediately find the parent directory and use that as the
               starting directory */
            if (startingDir.isFile()) {
                if (startingDir.getName().equals("bluej.pkg")) {
                    startingDir = startingDir.getParentFile();
                }
                else {
                    return null;
                }
            }
        }
        catch(IOException ioe)
        {
            Debug.message("could not resolve directory " + projectPath);
            ioe.printStackTrace();
            // give a proper user message here
            return null;
        }

        // if there is an existing bluej package file here we
        // need to find the root directory of the project
        // (and while we are at it we will construct the qualified
        //  package name that lets us open the PkgMgrFrame at the
        //  right point)
        if (Package.isBlueJPackage(startingDir)) {
            File curDir = startingDir;
            File lastDir = null;

            startingPackageName = "";

            while(curDir != null && Package.isBlueJPackage(curDir)) {
                if(lastDir != null)
                    startingPackageName = "." + lastDir.getName() +
                                          startingPackageName;

                lastDir = curDir;
                curDir = curDir.getParentFile();
            }

            if(startingPackageName.length() > 0)
                if(startingPackageName.charAt(0) == '.')
                    startingPackageName = startingPackageName.substring(1);

            // lastDir is now the directory holding the topmost bluej
            // package file in the directory heirarchy

            projectDir = lastDir;
        }
        else {
            // Debug.message("no BlueJ package file found in directory " + startingDir);
            return null;
        }

        // check whether it already exists
        Project proj = (Project)projects.get(projectDir);

        if(proj == null) {
            proj = new Project(projectDir);
            projects.put(projectDir, proj);
        }

        if (startingPackageName.equals("")) {
            Package startingPackage = proj.getPackage("");

            while(startingPackage != null) {
                Package sub = startingPackage.getBoringSubPackage();

                if (sub == null)
                    break;

                startingPackage = sub;
            }

            proj.initialPackageName = startingPackage.getQualifiedName();
        }
        else
            proj.initialPackageName = startingPackageName;

        return proj;
    }

    /**
     * Remove a project from the collection of currently open projects.
     */
    public static void closeProject(Project project)
    {
        PkgMgrFrame[] frames = PkgMgrFrame.getAllProjectFrames(project);

        if (frames != null) {
            for(int i=0; i< frames.length; i++) {
                frames[i].doClose(true);
            }
        }

        BlueJEvent.removeListener(project);
        projects.remove(project.getProjectDir());
    }


    public static boolean createNewProject(String projectPath)
    {
        if (projectPath != null) {

            // check whether name is already in use
            File dir = new File(projectPath);
            if(dir.exists()) {
                DialogManager.showError(null, "directory-exists");
                return false;
            }

            if(dir.mkdir()) {
                File newpkgFile = new File(dir, Package.pkgfileName);
                File newreadmeFile = new File(dir, Package.readmeName);

                try {
                    if(newpkgFile.createNewFile()) {
                        if(FileUtility.copyFile(
                                   Config.getTemplateFile("readme"),
                                   newreadmeFile))
                            return true;
                        else
                            Debug.message("could not copy readme template");
                    }
                }
                catch(IOException ioe)
                {
                }
            }
        }
        return false;
    }

    /**
     * Returns the number of open projects
     */
    public static int getOpenProjectCount()
    {
        return projects.size();
    }

    /**
     * workaround method to get a project from the project list.
     * Added so that if only one project is open you could call this
     * to access that project.  This was added to allow an inspector window
     * created from within the debugger to access custom inspectors for a
     * project.
     * @return an open project (may return null if no projects open)
     *
     */
    public static Project getProject()
    {
        if(projects.size() == 1) {
            Collection projectColl = projects.values();
            Iterator it = projectColl.iterator();
            if(it.hasNext())
                return (Project)it.next();
        }
        return null;
    }

    /* ------------------- end of static declarations ------------------ */

    // instance fields

    /** the path of the project directory. */
    private File projectDir;

    /** collection of open packages in this project
      (indexed by the qualifiedName of the package).
       The unnamed package ie root package of the package tree
       can be obtained by retrieving "" from this collection */
    private Map packages;

    /** a ClassLoader for the local virtual machine */
    private ProjectClassLoader loader;

    /** a ClassLoader for the remote virtual machine */
    private DebuggerClassLoader debuggerLoader;

    /** when a project is opened, the user may specify a
       directory deep into the projects directory structure.
       BlueJ will correctly find the top of this package
       heirarchy but the bit of the name left over will be
       put into this variable
       ie if opening /home/user/foo/com/sun where
       /home/user/foo is the project directory, this variable
       will be set to com.sun */
    private String initialPackageName = "";

    /** the documentation generator for this project. */
    private DocuGenerator docuGenerator;

    /** the test runner for this project */
    public TestRunner testRunner;

    /* ------------------- end of field declarations ------------------- */

    /**
     * Construct a project in the directory projectDir.
     * This must contain the root bluej.pkg of a nested package
     * (this should by its nature be the unnamed package).
     */
    private Project(File projectDir)
    {
        if(projectDir == null)
            throw new NullPointerException();

        this.projectDir = projectDir;

        packages = new TreeMap();
        try {
            packages.put("", new Package(this));
        }
        catch (IOException exc) {
            Debug.reportError("could not read package file (unnamed package)");
        }

        BlueJEvent.addListener(this);

        docuGenerator = new DocuGenerator(this);
        testRunner = new UnitTestRunnerDialog(new UnitTestTestSuiteLoader(this));
    }

    /**
     * Return the name of the project.
     */
    public String getProjectName()
    {
        return projectDir.getName();
    }

    /**
     * Return the location of the project.
     */
    public File getProjectDir()
    {
        return projectDir;
    }

    /**
     * A string which uniquely identifies this project
     */
    public String getUniqueId()
    {
        return "BJID" + getProjectDir().getPath();
    }

    /**
     *
     */
    public String getInitialPackageName()
    {
        return initialPackageName;
    }

    /**
     * Return a package from the project.
     * This will construct the package
     * if need be or return it from the cache
     * if already existing. All parent packages
     * on the way to the root of the package tree will
     * also be constructed.
     *
     *
     * @param qualifiedName the package name to fetch in dot qualified
     *                      notation ie java.util or "" for unnamed package
     */
    public Package getPackage(String qualifiedName)
    {
        Package existing = (Package) packages.get(qualifiedName);

        if (existing != null)
            return existing;

        if (qualifiedName.length() > 0) // should always be true (the unnamed package
                                        // always exists in the package collection)
        {
            Package pkg;
            try {
                Package parent = getPackage(JavaNames.getPrefix(qualifiedName));
                if(parent != null) {
                    pkg = new Package(this, JavaNames.getBase(qualifiedName),
                                      parent);
                    packages.put(qualifiedName, pkg);
                }
                else // parent package does not exist
                    pkg = null;
            }
            catch (IOException exc) {
                // the package did not exist in this project
                pkg = null;
            }

            return pkg;
        }

        throw new IllegalStateException("Project.getPackage()");
    }

    /**
     * Get the names of all packages in this project consisting of
     * rootPackage package and all packages nested below it.
     *
     * @param   rootPackage the root package to consider in looking
     *          for nested packages
     * @return  an array of String containing the fully qualified names
     *          of the packages.
     */
    private List getPackageNames(Package rootPackage)
    {
        List l = new LinkedList(), children;

        l.add(rootPackage.getQualifiedName());

        children = rootPackage.getChildren();

        if(children != null) {
            Iterator i = children.iterator();

            while(i.hasNext()) {
                Package p = (Package) i.next();
                l.addAll(getPackageNames(p));
            }
        }

        return l;
    }

    /**
     * Get the names of all packages in this project.
     * @return an array of String containing the fully qualified names
     * of the packages in this project.
     */
    public List getPackageNames()
    {
        return getPackageNames(getPackage(""));
    }

    /**
     * Generate documentation for the whole project.
     * @return "" if everything was alright, an error message otherwise.
     */
    public String generateDocumentation()
    {
        return docuGenerator.generateProjectDocu();
    }


    /**
     * Save all open packages of this project.
     */
    public void saveAll()
    {
        PkgMgrFrame[] frames = PkgMgrFrame.getAllProjectFrames(this);

        for(int i=0; i< frames.length; i++) {
            frames[i].doSave();
        }
    }

    /**
     * Reload all constructed packages of this project.
     *
     * This function is used after a major change to the contents
     * of the project directory ie an import.
     */
    public void reloadAll()
    {
        Iterator i = packages.values().iterator();

        while(i.hasNext()) {
            Package pkg = (Package) i.next();

            pkg.reload();
        }
    }

    /**
     * Implementation of the "Save As.." user function.
     */
    public void saveAs(PkgMgrFrame frame)
    {
        // get a file name to save under
        String newName = FileUtility.getFileName(frame, saveAsTitle,
                                                 saveLabel, false);

        if (newName != null) {

            saveAll();
            new ExportManager(frame).saveAs(getProjectDir().getPath(),
                                            newName);
            closeProject(this);

            // open new project

            Project openProj = openProject(newName);
            if(openProj != null) {
                Package pkg = openProj.getPackage(
                                         openProj.getInitialPackageName());

                PkgMgrFrame pmf = PkgMgrFrame.createFrame(pkg);
                pmf.show();
            }
            else
                Debug.message("could not open package under new name");
        }
    }

    /**
     * Get the ClassLoader for this project.
     * The ClassLoader load classes on the local VM.
     */
    private synchronized ProjectClassLoader getLocalClassLoader()
    {
        if(loader == null)
            loader = ClassMgr.getProjectLoader(getProjectDir());

        return loader;
    }

    /**
     * Removes the current classloader, and removes
     * references to classes loaded by it (this includes removing
     * the objects from all object benches of this project).\
     * Should be run whenever a source file changes
     */
    synchronized void removeLocalClassLoader()
    {
       if(loader != null) {
            // remove bench objects for all frames in this project
            PkgMgrFrame[] frames = PkgMgrFrame.getAllProjectFrames(this);

            for(int i=0; i< frames.length; i++)
                frames[i].getObjectBench().removeAll(getUniqueId());

            // remove views for classes loaded by this classloader
            View.removeAll(loader);

            loader = null;
        }
    }

    /**
     * Get the DebuggerClassLoader for this package.
     * The DebuggerClassLoader load classes on the remote VM
     * (the machine used for user code execution).
     */
    public synchronized DebuggerClassLoader getRemoteClassLoader()
    {
        if(debuggerLoader == null)
            debuggerLoader = Debugger.debugger.createClassLoader
                                                (getUniqueId(), getProjectDir().getPath());
        return debuggerLoader;
    }

    /**
     * Removes the remote VM classloader.
     * Should be run whenever a source file changes.
     */
    synchronized void removeRemoteClassLoader()
    {
        if(debuggerLoader != null) {
            Debugger.debugger.removeClassLoader(debuggerLoader);
            debuggerLoader = null;
        }
    }

    /**
     * Removes the remote VM classloader.
     * Should be run whenever a source file changes.
     */
    synchronized void removeRemoteClassLoaderLeavingBreakpoints()
    {
        if(debuggerLoader != null) {
            Debugger.debugger.saveBreakpoints();
            Debugger.debugger.removeClassLoader(debuggerLoader);

            // blank out the current loader then cause a new loader
            // to be created
            debuggerLoader = null;
            getRemoteClassLoader();

            Debugger.debugger.restoreBreakpoints(debuggerLoader);
        }
    }

    /**
     * Loads a class using the current classLoader
     */
    public Class loadClass(String className)
    {
        try {
            return getLocalClassLoader().loadClass(className);
        } catch(ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Return a string representing the classpath for this project.
     */
    public String getClassPath()
    {
        ClassPath allcp = ClassMgr.getClassMgr().getAllClassPath();

	allcp.addClassPath(getLocalClassLoader().getAsClassPath());

        return allcp.toString();
    }

    /**
     * Convert a filename into a fully qualified Java name.
     * Returns null if the file is outside the project
     * directory.
     *
     * The behaviour of this function is not guaranteed if
     * you pass in a directory name. It is meant for filenames
     * like /foo/bar/p1/s1/TestName.java
     *
     * An example of its use is if your project was in the
     * directory /foo/bar and you passed in
     * /foo/bar/p1/s1/TestName.java the function would
     * return p1.s1.TestName
     */
    public String convertPathToPackageName(String pathname)
    {
        try {
            File pathfile = new File(pathname).getCanonicalFile();
            File parent = null;
            String name = "";
            int firstDot;

            while((parent = pathfile.getParentFile()) != null) {
                if(pathfile.equals(getProjectDir())) {
                    return name;
                }

                if (name == "") {
                    name = pathfile.getName();

                    if((firstDot = name.indexOf('.')) >= 0) {
                        name = name.substring(0, firstDot);
                    }
                }
                else {
                    name = pathfile.getName() + "." + name;
                }

                pathfile = parent;
            }
        }
        catch(IOException ioe) { }

        return null;
    }

    // ---- BlueJEventListener interface ----

    /**
     *  A BlueJEvent was raised. Check whether it is one that we're interested
     *  in.
     */
    public void blueJEvent(int eventId, Object arg)
    {
        DebuggerThread thread;

        switch(eventId) {
        case BlueJEvent.BREAKPOINT:
            thread = (DebuggerThread)arg;
            if(thread.getParam() == this)
                hitBreakpoint(thread);
            break;
        case BlueJEvent.HALT:
            thread = (DebuggerThread)arg;
            if((thread != null) && (thread.getParam() == this))
                hitHalt(thread);
            break;
        case BlueJEvent.SHOW_SOURCE:
            thread = (DebuggerThread)arg;
            if(thread.getParam() == this)
                showSourcePosition(thread, false);
            break;
        }
    }

    // ---- end of BlueJEventListener interface ----


    /**
     * hitBreakpoint - A breakpoint in this package was hit.
     */
    private void hitBreakpoint(DebuggerThread thread)
    {
        String packageName = JavaNames.getPrefix(thread.getClass(0));
        Package pkg = getPackage(packageName);
        if(pkg != null)
            pkg.hitBreakpoint(thread);
    }

    /**
     * hitHalt - execution stopped interactively or after a step.
     */
    private void hitHalt(DebuggerThread thread)
    {
        String packageName = JavaNames.getPrefix(thread.getClass(0));
        Package pkg = getPackage(packageName);
        if(pkg != null)
            pkg.hitHalt(thread);
    }

    /**
     * showSourcePosition - The debugger display needs updating.
     */
    private void showSourcePosition(DebuggerThread thread,
                                    boolean updateDebugger)
    {
        String packageName = JavaNames.getPrefix(thread.getClass(0));
        Package pkg = getPackage(packageName);
        if(pkg != null)
            pkg.showSourcePosition(thread, updateDebugger);
    }

}
