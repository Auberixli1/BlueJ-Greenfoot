/*
 This file is part of the Greenfoot program. 
 Copyright (C) 2010 Poul Henriksen and Michael Kolling 
 
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
package rmiextension;

import greenfoot.core.Simulation;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import rmiextension.wrappers.WrapperPool;
import bluej.debugger.Debugger;
import bluej.debugger.DebuggerEvent;
import bluej.debugger.DebuggerListener;
import bluej.debugger.DebuggerObject;
import bluej.debugger.DebuggerThread;
import bluej.debugger.SourceLocation;
import bluej.debugger.DebuggerEvent.BreakpointProperties;
import bluej.extensions.BPackage;
import bluej.extensions.BProject;
import bluej.extensions.ExtensionBridge;
import bluej.extensions.ProjectNotOpenException;
import bluej.utility.Debug;

/**
 * A class that does several things.
 * 
 * Firstly, it listens for the debugger terminating the Greenfoot VM, and relaunches Greenfoot.
 * 
 * Secondly, it tries to make sure that the debugger never stops the code
 * entirely outside the user's code (i.e. there should always be some user code
 * somewhere in the call stack) 
 * 
 * @author Neil Brown
 */
public class GreenfootDebugHandler implements DebuggerListener
{  
    private static final String INVOKE_CLASS = Simulation.class.getName();
    
    private static final String[] INVOKE_METHODS = {Simulation.ACT_WORLD, Simulation.ACT_ACTOR, Simulation.NEW_INSTANCE};
    private static final String[] SPECIAL_METHODS = {Simulation.ACT_WORLD, Simulation.ACT_ACTOR, Simulation.NEW_INSTANCE, Simulation.PAUSED};
    
    /**
     * The scheduledTasks collection exists to solve some tricky issues with timing and deadlock
     * among the VMs.  Here's the scenario:
     * 
     * The VM event handler calls examineDebuggerEvent as part of handling an event.
     * The examineDebuggerEvent method realises that it wants to set the VM going again.
     * It can't make a direct call to stepInfo & co, because that requires some back-and-forth
     * with the VM event handler, which is calling us (and so would lead to deadlock).
     * 
     * So it needs to run the task later on, when the VM event handler is able to do the
     * interactions necessary, hence the idea of running the task later.  But -- if we
     * straight away call EventQueue.invokeLater, the task can execute before the VM event
     * handler has finished processing the event.  This causes problems because the VM event
     * handler makes some crucial suspend() calls that need to happen before our tasks
     * (VMEventHandler, line 122, at the time of writing), and we don't want to be vulnerable
     * to subtle timing issues.  So we store the tasks in scheduledTasks until
     * examineDebuggerEvent has completed and the suspend() calls made, and only schedule
     * them to run on the EventQueue in the later-called processDebuggerEvent. 
     * 
     */
    private IdentityHashMap<DebuggerThread, Runnable> scheduledTasks = new IdentityHashMap<DebuggerThread, Runnable>();
    
    private BProject project;
    
    private GreenfootDebugHandler(BProject project)
    {
        this.project = project;
    }
        
    /**
     * This is the publicly-visible way to add a debugger listener for a particular project.    
     */
    public static void addDebuggerListener(BProject project)
    {
        try {
            // Technically I could collapse the two listeners into one, but they
            // perform orthogonal tasks so it's nicer to keep the code separate:
            ExtensionBridge.addDebuggerListener(project, new GreenfootDebugHandler(project));
            ExtensionBridge.addDebuggerListener(project, new GreenfootDebugControlsLink());
        } catch (ProjectNotOpenException ex) {
            Debug.reportError("Project not open when adding debugger listener in Greenfoot", ex);
        }
    }

    // ------------- DebuggerListener interface ------------
    
    /**
     * An early examination of the debugger event (gets called before processDebuggerEvent)
     * 
     * This method is responsible for checking where the debugger has stopped (if it has),
     * and deciding whether it should be run on for a bit until it reaches user code.
     * 
     * This method does not actually run it on; see the comments on the scheduledTasks field
     * at the top of the class for how it works.
     */
    public boolean examineDebuggerEvent(final DebuggerEvent e)
    {
        Debugger debugger = (Debugger)e.getSource();
        List<SourceLocation> stack = e.getThread().getStack();
        
        if ((e.getID() == DebuggerEvent.THREAD_BREAKPOINT || e.getID() == DebuggerEvent.THREAD_HALT)
                && isSimulationThread(stack)) {
            if (insideUserCode(stack)) {
                // They are in an act method, make sure the breakpoints are cleared:
                
                // This method can be safely invoked without needing to talk to the worker thread:
                debugger.removeBreakpointsForClass(INVOKE_CLASS);
                        
                // If they have just hit the breakpoint and are in InvokeAct itself,
                // step-into the World/Actor:
                if (atInvokeBreakpoint(e.getBreakpointProperties())) {
                    // Avoid tricky re-entrant issues:
                    scheduledTasks.put(e.getThread(), new Runnable() {
                        public void run() {
                            e.getThread().stepInto();
                        }
                    });
                    return true;
                } else if (inInvokeMethods(stack, 0)) {
                    // Finished calling act() and have stepped out; run to next one:
                    scheduledTasks.put(e.getThread(), runToInternalBreakpoint(debugger, e.getThread()));
                    return true;                    
                } //otherwise they are in their own code
            } else if (atPauseMethod(stack)) {
                // They are going to pause; remove all special breakpoints and set them going
                // (so that they actually hit the pause):
                debugger.removeBreakpointsForClass(INVOKE_CLASS);
                e.getThread().cont();
                return true;
            } else {
                // They are not in an act() method; run until they get there:
                scheduledTasks.put(e.getThread(), runToInternalBreakpoint(debugger, e.getThread()));
                return true;
            }
        }

        return false;
    }
    
    /**
     * Processes a debugger event.  This is called after examineDebuggerEvent, with a second
     * parameter that effectively corresponds to the return result of examineDebuggerEvent.
     * 
     * Thus, if the parameter is true, we look for a scheduled task to run.
     * 
     * This is also the method where we check to see if we need to relaunch Greenfoot after
     * the VM has been terminated, and we call threadHalted if necessary.
     */
    public void processDebuggerEvent(DebuggerEvent e, boolean skipUpdate)
    {
        if (skipUpdate) {
            // Now is the time to schedule running the action that will restart the VM.
            // We didn't do this earlier because we needed to wait for the thread to be suspended again
            // during the event handling
            
            Runnable task = scheduledTasks.remove(e.getThread());
            if (task != null) {
                new Thread(task).start();
            }
            
        } else if (e.getNewState() == Debugger.NOTREADY && e.getOldState() == Debugger.IDLE) {           
            //It is important to have this code run at a later time.
            //If it runs from this thread, it tries to notify the VM event handler,
            //which is currently calling us and we get a deadlock between the two VMs.
            new Thread() {
                public void run()
                {
                    try { 
                        WrapperPool.instance().remove(project);
                        BPackage bPackage = project.getPackages()[0];
                        WrapperPool.instance().remove(bPackage);
                        ProjectManager.instance().openGreenfoot(bPackage.getProject());
                    }
                    catch (ProjectNotOpenException e) {
                        // Project closed, so no need to relaunch
                    }
                }
            }.start();
            
        } else if (e.getID() == DebuggerEvent.THREAD_BREAKPOINT || e.getID() == DebuggerEvent.THREAD_HALT) {
            threadHalted((Debugger)e.getSource(), e.getThread());
        }
    }

    /**
     * Decides what to do when the debugger has stopped the simulation thread
     * (which includes the possibility that the user hit Suspend in the GUI)
     * 
     * <p>Has roughly similar logic to examineDebuggerEvent but is called in different circumstances
     */
    private void threadHalted(final Debugger debugger, final DebuggerThread thread)
    {
        final List<SourceLocation> stack = thread.getStack();
        
        if (isSimulationThread(stack)) {
            if (insideUserCode(stack)) {
                // It's okay, they are in an act method, make sure the breakpoints are cleared:
                debugger.removeBreakpointsForClass(INVOKE_CLASS);
                //This method ^ can be safely invoked without needing to talk to the worker thread
            } else {
                // Set this going now; we are not the examine method:
                new Thread(runToInternalBreakpoint(debugger, thread)).start();
            }
        }
    }

    /**
     * Runs the debugger on until it hits the special invoke-act breakpoints that occur
     * just before user code might be encountered.  This method doesn't actually check if you're
     * thereabouts already, so it should be only called once you've checked that you actually
     * want to run onwards.
     * 
     * Returns a task that will run them onwards, which can be scheduled as you like
     */
    private static Runnable runToInternalBreakpoint(final Debugger debugger, final DebuggerThread thread)
    {
        //This method is called (via several others) from the thread that handles VM events
        //If we directly toggle breakpoints from this thread, it tries to wake up
        //the worker thread to do the toggling.  But that only works if it can send events
        //to the VM-event-handling-thread and get a response -- but that would be us!
        
        //To avoid that nasty re-entrant deadlock, we must run this method in a different thread:
        
        return new Runnable () {
            public void run () {
                // Set a break point where we want them to be:
                setSpecialBreakpoints(debugger);

                // Then set them running again:
                thread.cont();
            }
        };
    }
    
    /**
     * Works out if the given call-stack is the simulation thread
     * (by examining the bottom of the stack to see which class it is from)
     */
    private static boolean isSimulationThread(List<SourceLocation> stack)
    {
        if (stack.isEmpty())
            return false;
        SourceLocation root = stack.get(stack.size() - 1);
        
        return root.getClassName().equals("greenfoot.core.Simulation");
    }
    
    /**
     * Works out if we are currently in a call to the World or Actor act() methods
     * by looking in the call stack for them. Strictly speaking, we might not be
     * truly inside the user code: it might be we are about to enter or have just
     * left the act() method. It is only valid to call this for the simulation
     * thread.
     */
    private static boolean insideUserCode(List<SourceLocation> stack)
    {
        for (int i = 0; i < stack.size();i++) {
            if (inInvokeMethods(stack, i))
                return true;
        }
        return false;
    }
    
    /**
     * Works out if they are currently in the Simulation.PAUSED method by looking at the call stack.
     */
    private static boolean atPauseMethod(List<SourceLocation> stack)
    {
        SourceLocation frame = stack.get(0);
        return (INVOKE_CLASS.equals(frame.getClassName()) && Simulation.PAUSED.equals(frame.getMethodName()));
    }
   
    /**
     * Works out if the specified frame in the call-stack is in one of the special invoke-act
     * methods that call the World and Actor's act() methods or the method that runs
     * other user code on the simulation thread 
     */
    private static boolean inInvokeMethods(List<SourceLocation> stack, int frame)
    {
        if (frame < stack.size()) {
            if (stack.get(frame).getClassName().equals(INVOKE_CLASS)) {
                String methodName = stack.get(frame).getMethodName();
                for (String actMethod : INVOKE_METHODS) {
                    if (actMethod.equals(methodName))
                        return true;
                }
            }
        }
        
        return false;
    }
    
    private static final String INTERNAL_INVOKE_BREAKPOINT = INVOKE_CLASS + "INTERNAL";
    
    /**
     * Checks if the given breakpoint was set by the setInvokeActBreakpoints call, below  
     */
    private static boolean atInvokeBreakpoint(BreakpointProperties props)
    {
        return props != null && props.get(INTERNAL_INVOKE_BREAKPOINT) != null;
    }
    
    /**
     * Sets breakpoints in the special invoke-act methods that call the World and Actor's
     * act() methods, and the method that constructs new objects, and the method called when
     * the simulation will pause.  These breakpoints will thus be encountered immediately before control
     * would descend into the World and Actor's act() methods or other tasks (i.e. potential user code),
     * or if the simulation is going to wait for the user to click the controls (e.g. end of an
     * Act, or because the simulation is now going to be Paused).
     */
    private static void setSpecialBreakpoints(final Debugger debugger)
    {
        Map<String, String> properties = new HashMap<String, String>();
        properties.put(INTERNAL_INVOKE_BREAKPOINT, "yes");
        
        for (String method : SPECIAL_METHODS) {
            String err = debugger.toggleBreakpoint(INVOKE_CLASS, method, true, properties);
            if (err != null) {
                Debug.reportError("Problem setting special breakpoint: " + err);
            }
        }
    }
    
    /**
     * A second debug listener that only worries about enabling and disabling the
     * Act/Run/Pause buttons according to whether the Simulation thread is currently at
     * a breakpoint.
     */
    private static class GreenfootDebugControlsLink implements DebuggerListener
    {
        private DebuggerThread simulationThread;
        
        public boolean examineDebuggerEvent(DebuggerEvent e)
        {
            return false;
        }

        @Override
        public void processDebuggerEvent(DebuggerEvent e, boolean skipUpdate)
        {
            final String stateVar;
            if (e.getID() == DebuggerEvent.THREAD_BREAKPOINT || e.getID() == DebuggerEvent.THREAD_HALT) {
                List<SourceLocation> stack = e.getThread().getStack();
                if (isSimulationThread(stack)) {
                    simulationThread = e.getThread();
                    stateVar = "NOT_RUNNING";
                }
                else {
                    return;
                }
            }
            else if (e.getID() == DebuggerEvent.THREAD_CONTINUE) {
                if (e.getThread() == simulationThread) {
                    stateVar = "RUNNING";
                }
                else {
                    return;
                }
            } else {
                return;
            }
            
            final Debugger debugger = (Debugger) e.getSource();
            
            /* We are on the BlueJ VM, but we need to adjust the state of the buttons
             * on the Greenfoot VM (aka Debug VM).  We use this slight hack of constructing
             * a class on the Greenfoot VM that will do the work for us there.
             * 
             * For a parameter, we pass one of the static objects that the class holds
             * (this was more obviously do-able than passing a boolean constant, fix it if you know how)
             * 
             * We must do this in a new thread because we'll deadlock if we try to directly
             * create the object from a debug handler as we are. 
             */

            new Thread(new Runnable() {

                @Override
                public void run()
                {
                    try {
                        debugger.getClass("greenfoot.actions.RunActionsAdjuster");
                        //DAV
                        System.out.println("Calling into greenfoot VM with state: " + stateVar);
                        debugger.instantiateClass("greenfoot.actions.RunActionsAdjuster", new String[] {"java.lang.Object"}, new DebuggerObject[] {debugger.getStaticValue("greenfoot.actions.RunActionsAdjuster", stateVar)});
                    } catch (ClassNotFoundException ex) {
                        Debug.reportError("Could not find internal class RunActionsAdjuster", ex);
                    }
                }
            }).start();                
        }
    }
}
