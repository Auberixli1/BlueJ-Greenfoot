package bluej.extensions;

import bluej.debugger.DebuggerObject;
import bluej.debugger.ObjectWrapper;
import bluej.pkgmgr.Package;
import bluej.pkgmgr.PkgMgrFrame;

import com.sun.jdi.ObjectReference;

import bluej.debugger.*;

/**
 * A wrapper for an object in the BlueJ object bench.
 * This wraps an object so you can add and remove it from the bench.
 * Bobjects can be obtained from BConstructor, BMethods or BField.
 *
 * @version $Id: BObject.java 1848 2003-04-14 10:24:47Z damiano $
 */

/*
 * Author Clive Miller, University of Kent at Canterbury, 2002
 * Author Damiano Bolla, University of Kent at Canterbury 2003
 */
public class BObject
{
    private ObjectWrapper  wrapper;  

    /**
     * Constructor for BObject.
     */
    public BObject (ObjectWrapper aWrapper)
    {
        wrapper = aWrapper;
    }

    /**
     * Tests if this BObject is still valid in BlueJ.
     * This object may not be valid since what it represent has been modified or deleted
     * from the main BlueJ graphical user interface.
     * Return true if it is false otherwise.
     */
    public boolean isValid()
        {
        if ( wrapper == null ) return ( false);
        // TODO: Possible others checks here
        return true;
        }


    /**
     * Return the package this object belongs to.
     * It returns null if this is not a valid object anymore.
     */
    public BPackage getPackage()
    {
        if ( ! isValid() ) return null;

        return new BPackage(wrapper.getPackage());
    }
        
    /**
     * Removes this object from the Object Bench. 
     * This will also remove it from the visible part of the bench.
     * Once the object is removed from the bench it will not be available again.
     */
    public void removeFromBench()
        {
        if ( ! isValid() ) return;

        // This should really always exists, no need to check
        Package aPackage = wrapper.getPackage();

        // This may reasonably fail
        PkgMgrFrame aFrame = PkgMgrFrame.findFrame ( aPackage );
        if ( aFrame == null ) return;

        ObjectBench aBench = aFrame.getObjectBench();
        aBench.remove(wrapper, aPackage.getId());

        wrapper = null;
        }
    

    /**
     * Adds this object on the Object Bench.
     * If you pass null as instanceName the object will have a predefined name.
     * If the object is not a valid one nothing will happen.
     * 
     * @param instanceName  The name you want this object to have on the bench.
     */
    public void addToBench(String instanceName)
        {
        if ( ! isValid() ) return;

        // No reational to add a null object, isn't it ?
        if (wrapper.getObject().isNullObject()) return;

        // If you want you may set the instance name here. Othervise accept default
        if ( instanceName != null ) wrapper.setName(instanceName);
        
        // This should really always exists, no need to check
        Package aPackage = wrapper.getPackage();

        // This may reasonably fail
        PkgMgrFrame aFrame = PkgMgrFrame.findFrame ( aPackage );
        if ( aFrame == null ) return;

        ObjectBench aBench = aFrame.getObjectBench();
        aBench.add(wrapper);

        // load the object into runtime scope
        Debugger.debugger.addObjectToScope(aPackage.getId(),wrapper.getName(), wrapper.getObject());
        }


    /**
     * Return the name of the object on the bench.
     * It can return null if the object is invalid.
     */
    public String getInstanceName()
        {
        if ( ! isValid() ) return null;

        return wrapper.getName();
        }
    
    /**
     * Return the class of this object.
     * Similar to Reflection API.
     * It can return null if the object is invalid.
     */
    public BClass getBClass()
    {
        if ( ! isValid() ) return null;

        return new BClass (wrapper.getPackage(), wrapper.getClassName());
    } 

    /**
     * Returns the underlyng BlueJ package.
     * Should remain visible only to package members.
     */
    Package getBluejPackage ()
    {
        if ( wrapper == null ) return null;
        return wrapper.getPackage();
    }

    /**
     * Used by BField to get hold of the real Object
     */
    ObjectReference getObjectReference()
        {
        if ( wrapper == null ) return null;
        DebuggerObject obj = wrapper.getObject();

        if ( obj == null ) return null;
        return obj.getObjectReference();
        }


    /**
     * Return a reasonable representation of this object.
     */
    public String toString ()
      {
      return "BObject instanceName="+getInstanceName()+" Class Name="+wrapper.getClassName();
      }


}   