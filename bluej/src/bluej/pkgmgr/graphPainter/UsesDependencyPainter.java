package bluej.pkgmgr.graphPainter;

import java.awt.*;
import java.awt.Graphics2D;

import bluej.Config;
import bluej.graph.GraphElementController;
import bluej.pkgmgr.dependency.Dependency;
import bluej.pkgmgr.dependency.UsesDependency;
import bluej.pkgmgr.target.DependentTarget;

/**
 * Paints usesDependencies
 * @author fisker
 * @author  Michael Kolling
 * @version $Id: UsesDependencyPainter.java 2755 2004-07-07 15:52:12Z mik $
 */
public class UsesDependencyPainter implements DependencyPainter
{
    protected static final float strokeWidthDefault = 1.0f;
    protected static final float strokeWidthSelected = 2.0f;
    static final int ARROW_SIZE = 10;		// pixels
    static final double ARROW_ANGLE = Math.PI / 6;	// radians
    
    private static final Color normalColour = Config.getItemColour("colour.arrow.uses");


    private static final float  dash1[] = {5.0f,2.0f};
    private static final BasicStroke dashedUnselected = new BasicStroke(strokeWidthDefault,
            BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_MITER,
            10.0f, dash1, 0.0f);
    private static final BasicStroke dashedSelected = new BasicStroke(strokeWidthSelected,
            BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_MITER,
            10.0f, dash1, 0.0f);
    private static final BasicStroke normalSelected = new BasicStroke(strokeWidthSelected);
    private static final BasicStroke normalUnselected = new BasicStroke(strokeWidthDefault);
    
    private GraphPainterStdImpl graphPainterStdImpl;
    
    public UsesDependencyPainter(GraphPainterStdImpl graphPainterStdImpl)
    {
        this.graphPainterStdImpl = graphPainterStdImpl;
    }
    
    public void paint(Graphics2D g, Dependency dependency)
    {
        if (!(dependency instanceof UsesDependency)) {
            throw new IllegalArgumentException("Not a UsesDependency");
        }
        Stroke oldStroke = g.getStroke();        
        UsesDependency d = (UsesDependency) dependency; 
        Stroke dashedStroke, normalStroke;
        boolean isSelected = d.isSelected() && graphPainterStdImpl.isGraphEditorInFocus();
        if (isSelected) 
        {
            dashedStroke = dashedSelected;
            normalStroke = normalSelected;         
        } 
        else
        {
            dashedStroke = dashedUnselected;
            normalStroke = normalUnselected;   
        }
        g.setStroke(normalStroke);
        int src_x = d.getSourceX();
        int src_y = d.getSourceY();
        int dst_x = d.getDestX();
        int dst_y = d.getDestY();;

        g.setColor(normalColour);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // Draw the end arrow
        int delta_x = d.isEndLeft() ? -10 : 10;
        int[] xPoints = { dst_x, dst_x + delta_x, dst_x + delta_x };
        int[] yPoints = { dst_y, dst_y - 3, dst_y + 3 };

        g.drawLine(dst_x, dst_y, dst_x + delta_x, dst_y + 4);
        g.drawLine(dst_x, dst_y, dst_x + delta_x, dst_y - 4);
        g.setStroke(dashedStroke);

        // Draw the start
        int corner_y = src_y + (d.isStartTop() ? -15 : 15);
        g.drawLine(src_x, corner_y, src_x, src_y);
        src_y = corner_y;

        // Draw the last line segment
        int corner_x = dst_x + (d.isEndLeft() ? -15 : 15);
        g.drawLine(corner_x, dst_y, dst_x, dst_y);
        dst_x = corner_x;

        // if arrow vertical corner, draw first segment up to corner
        if((src_y != dst_y) && (d.isStartTop() == (src_y < dst_y))) {
            corner_x = ((src_x + dst_x) / 2) + (d.isEndLeft() ? 15 : -15);
            corner_x = (d.isEndLeft() ? Math.min(dst_x, corner_x) :
                Math.max(dst_x, corner_x));
            g.drawLine(src_x, src_y, corner_x, src_y);
            src_x = corner_x;
        }

        // if arrow horiz. corner, draw first segment up to corner
        if((src_x != dst_x) && (d.isEndLeft() == (src_x > dst_x))) {
            corner_y = ((src_y + dst_y) / 2) + (d.isStartTop() ? 15 : -15);
            corner_y = (d.isStartTop() ? Math.min(src_y, corner_y) :
                Math.max(src_y, corner_y));
            g.drawLine(dst_x, corner_y, dst_x, dst_y);
            dst_y = corner_y;
        }

        // draw the middle bit
        g.drawLine(src_x, src_y, src_x, dst_y);
        g.drawLine(src_x, dst_y, dst_x, dst_y);
        
        g.setStroke(oldStroke);
    }

    /**
     * Paint the usesdependency from DependTarget d, as a straight arrow to a
     * point in the graph determined also by d
     * @param g
     * @param d
     */
    public void paintIntermedateDependency(Graphics2D g, DependentTarget d) {
        Stroke dashedStroke, normalStroke;
        dashedStroke = dashedUnselected;
        normalStroke = normalUnselected;
        g.setStroke(normalStroke);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g.setColor(normalColour);

        // Start from the centre of the src class
        Point pFrom = new Point(d.getX() + (int)d.getWidth()/2, d.getY() + (int)d.getHeight()/2);
        Point pTo = new Point(GraphElementController.dependencyArrowX, 
  			  				  GraphElementController.dependencyArrowY);

        // Get the angle of the line from src to dst.
        double angle = Math.atan2(-(pFrom.getY() - pTo.getY()), pFrom.getX() - pTo.getX());

        Point pArrow = new Point(pTo.x + (int)((ARROW_SIZE - 2) * Math.cos(angle)), pTo.y - (int)((ARROW_SIZE - 2) * Math.sin(angle)));

        // setup the arrow head
        int[] xPoints =  { pTo.x, pTo.x + (int)((ARROW_SIZE) * Math.cos(angle + ARROW_ANGLE)), pTo.x + (int)(ARROW_SIZE * Math.cos(angle - ARROW_ANGLE)) };
        int[] yPoints =  { pTo.y, pTo.y - (int)((ARROW_SIZE) * Math.sin(angle + ARROW_ANGLE)), pTo.y - (int)(ARROW_SIZE * Math.sin(angle - ARROW_ANGLE)) };

        //draw the arrowhead
        g.drawLine(xPoints[0],yPoints[0],xPoints[1],yPoints[1]);
        g.drawLine(xPoints[0],yPoints[0],xPoints[2],yPoints[2]);
        //draw the arrow line
        g.setStroke(dashedStroke);
        g.drawLine(pFrom.x, pFrom.y, xPoints[0],yPoints[0]);
        
    }

    /* (non-Javadoc)
     * @see bluej.pkgmgr.graphPainter.DependencyPainter#getPopupMenuPosition(bluej.pkgmgr.dependency.Dependency)
     */
    public Point getPopupMenuPosition(Dependency d) {
        UsesDependency usesDependency;
        if (! (d instanceof UsesDependency)){
            throw new IllegalArgumentException("Not a UsesDependency");
        }
        usesDependency = (UsesDependency) d;
        
        int delta_x = usesDependency.isEndLeft() ? -10 : 10;
        int dst_x = usesDependency.getDestX();
        int dst_y = usesDependency.getDestY();
        
        int[] xPoints = { dst_x, dst_x + delta_x, dst_x + delta_x };
        int[] yPoints = { dst_y, dst_y - 3, dst_y + 3 };

        return new Point((xPoints[0] + xPoints[1] + xPoints[2])/3, 
       		 			 (yPoints[0] + yPoints[1] + yPoints[2])/3);
    }
}
