package greenfoot.gui;

import greenfoot.actions.PauseSimulationAction;
import greenfoot.actions.ResetWorldAction;
import greenfoot.actions.RunOnceSimulationAction;
import greenfoot.actions.RunSimulationAction;
import greenfoot.core.Simulation;
import greenfoot.event.SimulationEvent;
import greenfoot.event.SimulationListener;

import java.awt.CardLayout;
import java.awt.FlowLayout;

import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.EventListenerList;

/**
 * Panel that holds the buttons that controls the simulation.
 * 
 * @author Poul Henriksen
 * @version $Id: ControlPanel.java 4900 2007-03-26 09:52:16Z mik $
 */
public class ControlPanel extends Box
    implements ChangeListener, SimulationListener
{
    private RunSimulationAction runSimulationAction;
    private PauseSimulationAction pauseSimulationAction;
    private RunOnceSimulationAction runOnceSimulationAction;

    private ResetWorldAction resetWorldAction ;
    private JSlider speedSlider;

    protected EventListenerList listenerList = new EventListenerList();
    
    private CardLayout runpauseLayout;
    private JPanel runpauseContainer;
    
    private Simulation simulation;
    
    /**
     * 
     * @param simulation
     * @param includeAllControls If false, the act-button and speedslider will be excluded.
     */
    public ControlPanel(Simulation simulation, boolean includeAllControls)
    {
        super(BoxLayout.X_AXIS);
        
        this.simulation = simulation;
        
        add(createButtonPanel(simulation, includeAllControls));

        if (includeAllControls) {
            add(createSpeedSlider());
        }
        simulation.addSimulationListener(this);

    }

    private JPanel createButtonPanel(Simulation simulation, boolean includeAllControls)
    {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        
        if (includeAllControls) {
            runOnceSimulationAction = RunOnceSimulationAction.getInstance();
            runOnceSimulationAction.attachSimulation(simulation);
            runOnceSimulationAction.putValue(Action.LONG_DESCRIPTION, "Call 'act' once for every actor.");
            runOnceSimulationAction.putValue(Action.SHORT_DESCRIPTION, "Call 'act' once for every actor.");
            runOnceSimulationAction.setEnabled(false);
            AbstractButton stepButton = new JButton(runOnceSimulationAction);

            buttonPanel.add(stepButton);
        }

        runSimulationAction = RunSimulationAction.getInstance();
        runSimulationAction.attachSimulation(simulation);
        runSimulationAction.putValue(Action.LONG_DESCRIPTION, "Run the simulation until stopped.");
        runSimulationAction.putValue(Action.SHORT_DESCRIPTION, "Run the simulation.");
        runSimulationAction.setEnabled(false);
        JButton runButton = new JButton(runSimulationAction);
        runButton.setFocusable(false);

        pauseSimulationAction = PauseSimulationAction.getInstance();
        pauseSimulationAction.attachSimulation(simulation);
        pauseSimulationAction.putValue(Action.LONG_DESCRIPTION,
                "Pause the simulation, leaving it in the current state.");
        pauseSimulationAction.putValue(Action.SHORT_DESCRIPTION, "Pause the simulation.");
        pauseSimulationAction.setEnabled(false);
        JButton pauseButton = new JButton(pauseSimulationAction);
        pauseButton.setFocusable(false);
        
        runpauseLayout = new CardLayout();
        runpauseContainer = new JPanel(runpauseLayout) {
            public boolean isValidateRoot()
            {
                return true;
            }
        };
        runpauseContainer.add(runButton, "run");
        runpauseContainer.add(pauseButton, "pause");
        buttonPanel.add(runpauseContainer);
        
        
        resetWorldAction = ResetWorldAction.getInstance();
        resetWorldAction.putValue(Action.LONG_DESCRIPTION, "Instantiate a new world.");
        resetWorldAction.putValue(Action.SHORT_DESCRIPTION, "Instantiate a new world.");
        resetWorldAction.attachSimulation(simulation);
        resetWorldAction.setEnabled(false);
        AbstractButton resetButton = new JButton(resetWorldAction);
        resetButton.setFocusable(false);
        buttonPanel.add(resetButton);
        
        return buttonPanel;
    }

    private JComponent createSpeedSlider()
    {
        JPanel speedPanel = new JPanel(new FlowLayout());
        JLabel speedLabel = new JLabel("Speed:");
        speedPanel.add(speedLabel);
        
        int min = 0;
        int max = Simulation.MAX_SIMULATION_SPEED;
        speedSlider = new JSlider(JSlider.HORIZONTAL, min, max, simulation.getSpeed());
        speedSlider.setPaintLabels(false);
        speedSlider.setMajorTickSpacing( max / 2);
        speedSlider.setMinorTickSpacing( max / 4);
        speedSlider.setPaintTicks(true);
        speedSlider.setValue( max / 2 );
        speedSlider.setEnabled(false);
        speedSlider.addChangeListener(this);
        speedSlider.setToolTipText("Adjusts the execution speed");
        speedPanel.add(speedSlider);
        
        return speedPanel;
    }
    
    
    /**
     *
     */
    public void simulationChanged(SimulationEvent e)
    {
        int etype = e.getType();
        if (etype == SimulationEvent.STARTED) {
            if(speedSlider != null) {
                speedSlider.setEnabled(true);
            }
            runpauseLayout.show(runpauseContainer, "pause");
        }
        else if (etype == SimulationEvent.STOPPED) {
            if(speedSlider != null) {
                speedSlider.setEnabled(true);
            }
            runpauseLayout.show(runpauseContainer, "run");
        }
        else if (etype == SimulationEvent.CHANGED_SPEED) {
            if(speedSlider != null) {
                speedSlider.setEnabled(true);            
                int newSpeed = simulation.getSpeed();
                if (newSpeed != speedSlider.getValue()) {
                    speedSlider.setValue(newSpeed);
                }
            }
        } 
        else if(etype == SimulationEvent.DISABLED) {
            if(speedSlider != null) {
                speedSlider.setEnabled(false);
            }
        }
    }
    

    // ---------- ChangeListener interface (for speed slider changes) -----------
    
    public void stateChanged(ChangeEvent e)
    {
        simulation.setSpeed(speedSlider.getValue());
    }


}