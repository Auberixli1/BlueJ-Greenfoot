/*
 This file is part of the Greenfoot program. 
 Copyright (C) 2005-2009,2010,2011,2013,2014,2015,2016  Poul Henriksen and Michael Kolling 
 
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
package greenfoot.core;

import bluej.extensions.PackageNotFoundException;
import bluej.extensions.ProjectNotOpenException;
import bluej.extensions.SourceType;
import bluej.utility.Debug;
import greenfoot.World;
import greenfoot.util.GreenfootUtil;
import rmiextension.wrappers.RClass;
import rmiextension.wrappers.RPackage;

import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Represents a package in Greenfoot.
 * 
 * <p>A GPackage is essentially a reference to a remote package (RPackage), together
 * with a pool of GClass objects representing the classes in the package. 
 * 
 * @author Poul Henriksen
 */
public class GPackage
{
    private RPackage pkg;
    private GProject project; 
    
    private Map<RClass,GClass> classPool = new HashMap<RClass,GClass>();
    
    /**
     * Contructor for an unspecified package, but for which a project is known.
     * Used to allow a class to not be part of a package, but still being able
     * to get the project the class is part of.
     */
    GPackage(GProject project) 
    {
        if(project == null) {
            throw new NullPointerException("Project must not be null.");
        }
        this.project = project;
    }
    
    /**
     * Construct a new GPackage; this should generally only be called by
     * GProject.
     * 
     * @param pkg  The reference to the remote package
     * @param project  The project
     */
    public GPackage(RPackage pkg, GProject project)
    {
        if(pkg == null) {
            throw new NullPointerException("Pkg must not be null.");
        }
        if(project == null) {
            throw new NullPointerException("Project must not be null.");
        }
        this.pkg = pkg;
        this.project = project;
    }
    
    /**
     * Get the GClass wrapper for a remote class in this package.
     */
    public GClass getGClass(RClass remoteClass, boolean inRemoteCallback)
    {
        if (remoteClass == null) {
            return null;
        }
        
        GClass gClass;
        synchronized (classPool) {
            gClass = classPool.get(remoteClass);
            if (gClass == null) {
                gClass = new GClass(remoteClass, this, inRemoteCallback);
                classPool.put(remoteClass, gClass);
                gClass.loadSavedSuperClass(inRemoteCallback);
            }
        }
        return gClass;
    }

    public GProject getProject()
    {
        return project;
    }

    public GClass[] getClasses(boolean inRemoteCallback)
    {
        try {
            RClass[] rClasses = pkg.getRClasses();
            GClass[] gClasses = new GClass[rClasses.length];
            for (int i = 0; i < rClasses.length; i++) {
                RClass rClass = rClasses[i];
                gClasses[i] = getGClass(rClass, inRemoteCallback);
            }
            return gClasses;
        }
        catch (ProjectNotOpenException e) {
            Debug.reportError("Could not get package classes", e);
            throw new InternalGreenfootError(e);
        }
        catch (PackageNotFoundException e) {
            Debug.reportError("Could not get package classes", e);
            throw new InternalGreenfootError(e);
        }
        catch (RemoteException e) {
            Debug.reportError("Could not get package classes", e);
            throw new InternalGreenfootError(e);
        }
    }
    
    /**
     * Get the named class (null if it cannot be found).
     * Do not call from a remote callback.
     */
    public GClass getClass(String className)
    {
        try {
            RClass rClass = pkg.getRClass(className);
            return getGClass(rClass, false);
        }
        catch (RemoteException re) {
            Debug.reportError("Getting class", re);
        }
        catch (ProjectNotOpenException pnoe) {
            Debug.reportError("Creating new class", pnoe);
        }
        catch (PackageNotFoundException pnfe) {
            Debug.reportError("Creating new class", pnfe);
        }
        
        return null;
    }

    /** 
     * Returns all the world sub-classes in this package that can be instantiated.
     * Do not call from a remote callback.
     */
    @SuppressWarnings("unchecked")
    public List<Class<? extends World>> getWorldClasses()
    {
        List<Class<? extends World>> worldClasses= new LinkedList<Class<? extends World>>();
        GClass[] classes = getClasses(false);
        for (int i = 0; i < classes.length; i++) {
            GClass cls = classes[i];
            if(cls.isWorldSubclass()) {
                Class<? extends World> realClass = (Class<? extends World>) cls.getJavaClass();   
                if (GreenfootUtil.canBeInstantiated(realClass)) {                  
                    worldClasses.add(realClass);
                }                    
            }
        }
        return worldClasses;
    }

    public SourceType getDefaultSourceType()
    {
        // Our heuristic is: if the scenario contains any Stride files, the default is Stride,
        // otherwise it's Java
        if (Arrays.asList(getClasses(false)).stream().anyMatch(c -> c.getSourceType() == SourceType.Stride))
            return SourceType.Stride;
        else
            return SourceType.Java;
    }
}
