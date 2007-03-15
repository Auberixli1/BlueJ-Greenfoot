package bluej.groupwork.actions;

import java.io.File;
import java.io.IOException;
import java.util.*;

import javax.swing.SwingUtilities;

import org.netbeans.lib.cvsclient.command.CommandAbortedException;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;

import bluej.Config;
import bluej.groupwork.InvalidCvsRootException;
import bluej.groupwork.Repository;
import bluej.groupwork.UpdateListener;
import bluej.groupwork.UpdateResult;
import bluej.groupwork.UpdateServerResponse;
import bluej.groupwork.ui.ConflictsDialog;
import bluej.pkgmgr.Package;
import bluej.pkgmgr.PkgMgrFrame;
import bluej.pkgmgr.Project;
import bluej.pkgmgr.target.ClassTarget;
import bluej.pkgmgr.target.PackageTarget;
import bluej.pkgmgr.target.ReadmeTarget;
import bluej.pkgmgr.target.Target;
import bluej.utility.DialogManager;
import bluej.utility.JavaNames;


/**
 * Action to update out-of-date files.
 * 
 * @author fisker
 * @version $Id: UpdateAction.java 4843 2007-03-15 01:20:24Z davmac $
 */
public class UpdateAction extends TeamAction implements UpdateListener
{
    private Project project;
    private boolean includeLayout;
    
    /** A list of packages whose bluej.pkg file has been removed */
    private List removedPackages;
    
    public UpdateAction()
    {
        super("team.update");
        putValue(SHORT_DESCRIPTION, Config.getString("tooltip.update"));
    }

    /* (non-Javadoc)
     * @see java.awt.event.ActionListener#actionPerformed(java.awt.event.ActionEvent)
     */
    public void actionPerformed(PkgMgrFrame pmf)
    {
        project = pmf.getProject();
        includeLayout = project.getTeamSettingsController().includeLayout();
        
        if (project != null) {
            project.saveAllEditors();
            doUpdate(project);
        }
    }

    private void doUpdate(final Project project)
    {
        final Repository repository = project.getRepository();
        if (repository == null) {
            return;
        }
        
        Thread thread = new Thread() {
            public void run()
            {
                removedPackages = new ArrayList();
                boolean success = false;
                try {
                    final UpdateServerResponse response = repository.updateAll(UpdateAction.this);
                                        
                    Runnable projectUpdate = new Runnable() {
                        public void run()
                        {
                            handleConflicts(response);
                        }
                    };
                    
                    SwingUtilities.invokeLater(projectUpdate);
                    
                    // update layout files if necessary
                    //if (includeLayout && ! response.isError()) {
                        // Save the current graph layout, so that we pick up
                        // actual changes
                    //    project.saveAllGraphLayout();
                    //    repository.updateAndOverride(pkgArray, UpdateAction.this);
                    //}
                    
                    success = ! response.isError();
                    
                } catch (CommandAbortedException e) {
                    e.printStackTrace();
                } catch (CommandException e) {
                    e.printStackTrace();
                } catch (AuthenticationException e) {
                    handleAuthenticationException(e);
                } catch (InvalidCvsRootException e) {
                    handleInvalidCvsRootException(e);
                }
                finally {
                    stopProgressBar();
                    if (success) {
                        setStatus(Config.getString("team.update.statusDone"));
                    }
                    else {
                        clearStatus();
                    }
                    handleRemovedPkgs();
                }
            }
            
            /**
             * If packages were removed by the update, remove them from the
             * parent package graph.
             */
            private void handleRemovedPkgs()
            {
                for (Iterator i = removedPackages.iterator(); i.hasNext(); ) {
                    String packageName = i.next().toString();
                    String parentPackage = JavaNames.getPrefix(packageName);
                    String baseName = JavaNames.getBase(packageName);
                    
                    File packageDir = JavaNames.convertQualifiedNameToFile(packageName);
                    if (! packageDir.exists()) {
                        // Get the parent package so we can remove the child.
                        Package pkg = project.getPackage(parentPackage);
                        if (pkg == null) {
                            return;
                        }
                        Target target = pkg.getTarget(baseName);
                        if (target instanceof PackageTarget) {
                            pkg.removeTarget(target);
                        }
                    }
                }
            }
            
            protected void handleConflicts(UpdateServerResponse updateServerResponse)
            {
                if (updateServerResponse == null) {
                    return;
                }
                
                if (updateServerResponse.getConflicts().size() <= 0) {
                    return;
                }
                
                /** A list of files to replace with repository version */
                Set filesToOverride = new HashSet();
                
                // Binary conflicts
                for (Iterator i = updateServerResponse.getBinaryConflicts().iterator();
                        i.hasNext(); ) {
                    File f = (File) i.next();
                    
                    // TODO proper check for name - case insensitive file systems
                    if (f.getName().equals("bluej.pkg")) {
                        filesToOverride.add(f);
                    }
                    else {
                        // TODO make the displayed file path relative to project
                        int answer = DialogManager.askQuestion(PkgMgrFrame.getMostRecent(),
                                "team-binary-conflict", new String[] {f.getName()});
                        if (answer == 0) {
                            // keep local version
                        }
                        else {
                            // use repository version
                            filesToOverride.add(f);
                        }
                    }
                }
                
                updateServerResponse.overrideFiles(filesToOverride);
                
                List blueJconflicts = new LinkedList();
                List nonBlueJConflicts = new LinkedList();
                List targets = new LinkedList();
                
                for (Iterator i = updateServerResponse.getConflicts().iterator();
                        i.hasNext();) {
                    UpdateResult updateResult = (UpdateResult) i.next();
                    
                    // Calculate the file base name
                    String fileName = updateResult.getFilename();
                    String baseName;
                    int n = fileName.lastIndexOf('/');
                    if (n != -1) {
                        baseName = fileName.substring(n + 1);
                    }
                    else {
                        baseName = fileName;
                    }
                    
                    // bluej.pkg may come up as a conflict, but it won't cause a problem,
                    // so it can be ignored.
                    if (! baseName.equals("bluej.pkg")) {
                        Target target = null;
                        
                        if (baseName.endsWith(".java") || baseName.endsWith(".class")) {
                            File file = new File(project.getProjectDir(), fileName);
                            String pkg = project.getPackageForFile(file);
                            if (pkg != null) {
                                String targetId = filenameToTargetIdentifier(baseName);
                                targetId = JavaNames.combineNames(pkg, targetId);
                                target = project.getTarget(targetId);
                            }
                        }
                        else if (baseName.equals("README.TXT")) {
                            File file = new File(project.getProjectDir(), fileName);
                            String pkg = project.getPackageForFile(file);
                            if (pkg != null) {
                                String targetId = ReadmeTarget.README_ID;
                                targetId = JavaNames.combineNames(pkg, targetId);
                                target = project.getTarget(targetId);
                            }
                        }
                        
                        if (target == null) {
                            nonBlueJConflicts.add(fileName);
                        } else {
                            blueJconflicts.add(fileName);
                            targets.add(target);
                        }
                    }
                }
                
                if (! blueJconflicts.isEmpty() || ! nonBlueJConflicts.isEmpty()) {
                    project.clearAllSelections();
                    project.selectTargetsInGraphs(targets);
                    
                    ConflictsDialog conflictsDialog = new ConflictsDialog(project,
                            blueJconflicts, nonBlueJConflicts);
                    conflictsDialog.setVisible(true);
                }
            }
            
            /**
             * Strip the dot-suffix from a file name.
             * @param filename
             * @return
             */
            private String filenameToTargetIdentifier(String filename)
            {
                int lastDot = filename.lastIndexOf('.');
                return filename.substring(0, lastDot);
            }
        };
        
        thread.start();
        startProgressBar();
        setStatus(Config.getString("team.update.statusMessage"));
    }
    
    /* (non-Javadoc)
     * @see bluej.groupwork.UpdateListener#fileAdded(java.io.File)
     */
    public void fileAdded(final File f)
    {
        SwingUtilities.invokeLater(new Runnable() {
            public void run()
            {
                project.prepareCreateDir(f.getParentFile());
                
                String fileName = f.getName();
                if (! fileName.endsWith(".java") &&
                        ! fileName.endsWith(".class") &&
                        ! fileName.equals("bluej.pkg")) {
                    return;
                }
                
                // First find out the package name...
                String packageName = project.getPackageForFile(f);
                if (packageName == null) {
                    return;
                }
                
                if (fileName.equals("bluej.pkg")) {
                    if (packageName.length() > 0) {
                        // If we now have a new package, we might need to add it
                        // as a target in an existing package
                        String parentPackageName = JavaNames.getPrefix(packageName);
                        Package parentPackage = project.getCachedPackage(parentPackageName);
                        if (parentPackage != null) {
                            Target t = parentPackage.addPackage(JavaNames.getBase(packageName));
                            parentPackage.positionNewTarget(t);
                        }
                    }
                }
                else {
                    int n = fileName.lastIndexOf(".");
                    String name = fileName.substring(0, n);
                    if (! JavaNames.isIdentifier(name)) {
                        return;
                    }
                    
                    Package pkg = project.getCachedPackage(packageName);
                    if (pkg == null) {
                        return;
                    }
                    Target t = pkg.getTarget(name);
                    if (t != null && ! (t instanceof ClassTarget)) {
                        return;
                    }
                    ClassTarget ct = (ClassTarget) t;
                    if (ct == null) {
                        ct = pkg.addClass(name);
                        pkg.positionNewTarget(ct);
                    }
                    ct.reload();
                }
            }
        });
    }
    
    /* (non-Javadoc)
     * @see bluej.groupwork.UpdateListener#fileRemoved(java.io.File)
     */
    public void fileRemoved(final File f)
    {
        SwingUtilities.invokeLater(new Runnable() {
           public void run()
            {
               String fileName = f.getName();
               if (! fileName.endsWith(".java") &&
                       ! fileName.endsWith(".class") &&
                       ! fileName.equals("bluej.pkg")) {
                   return;
               }
               
               // First find out the package name...
               String packageName = project.getPackageForFile(f);
               if (packageName == null) {
                   return;
               }
               
               if (fileName.equals("bluej.pkg")) {
                   // Delay removing the package until
                   // after the update has finished, and only do it if there
                   // are no files left in the package.
                   removedPackages.add(packageName);
               }
               else {
                   // Remove a class
                   int n = fileName.lastIndexOf(".");
                   String name = fileName.substring(0, n);
                   Package pkg = project.getCachedPackage(packageName);
                   if (pkg == null) {
                       return;
                   }
                   Target t = pkg.getTarget(name);
                   if (! (t instanceof ClassTarget)) {
                       return;
                   }
                   
                   ClassTarget ct = (ClassTarget) t;
                   if (ct.hasSourceCode() && ! fileName.endsWith(".java")) {
                       ct.setInvalidState();
                   }
                   else {
                       ct.remove();
                   }
               }
            } 
        });
    }
    
    /* (non-Javadoc)
     * @see bluej.groupwork.UpdateListener#fileUpdated(java.io.File)
     */
    public void fileUpdated(final File f)
    {
        SwingUtilities.invokeLater(new Runnable() {
            public void run()
            {
                String fileName = f.getName();
                if (! fileName.endsWith(".java") &&
                        ! fileName.endsWith(".class") &&
                        ! fileName.equals("bluej.pkg")) {
                    return;
                }
                
                // First find out the package name...
                String packageName = project.getPackageForFile(f);
                if (packageName == null) {
                    return;
                }
                Package pkg = project.getCachedPackage(packageName);
                if (pkg == null) {
                    return;
                }
                
                if (fileName.equals("bluej.pkg")) {
                    try {
                        if (includeLayout) {
                            pkg.reReadGraphLayout();
                        }
                    }
                    catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                }
                else {
                    int n = fileName.lastIndexOf(".");
                    String name = fileName.substring(0, n);
                    if (pkg == null) {
                        return;
                    }
                    Target t = pkg.getTarget(name);
                    if (! (t instanceof ClassTarget)) {
                        return;
                    }
                    
                    ClassTarget ct = (ClassTarget) t;
                    ct.reload();
                }
            }
        });
    }
}
