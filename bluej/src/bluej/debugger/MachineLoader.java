package bluej.debugger;

import bluej.Config;

/**
 ** @author Michael Kolling
 **
 ** This class defines a separate thread to load the second ("remote")
 ** virtual machine. This is done asynchronously, since it takes a loooong
 ** time, and we would like to do it in the background.
 **/

public class MachineLoader extends Thread
{
    /**
     * Create the machine loader thread.
     */
    public MachineLoader()
    {
	super("MachineLoader");
    }

    /**
     * run - this method executes when the thread is started. Load
     *  the virtual machine here. (The remote virtual machine is
     *  internally referred to as the "debugger".)
     */
    public void run()
    {
	Debugger.debugger.startDebugger();
    }
}
