import greenfoot.World;
import greenfoot.Actor;

import java.util.Random;

public class Forrest extends World
{
    private final static int WIDTH = 60;
    private final static int HEIGHT = 60;
    
    private Random random;
    
    /**
     * Creates a new world with 20x20 cells and
     * with a cell size of 50x50 pixels
     */
    public Forrest() {
        super(WIDTH, HEIGHT, Tree.SIZE);
        random = new Random();
        getBackground().setColor(java.awt.Color.WHITE);
        getBackground().fill();
        populate(50);
        Tree tree = (Tree) getObjects(Tree.class).get(0);
        tree.burn();
    }
     
    public void fill() {
         long t1 = System.currentTimeMillis();
         for(int i=0; i < WIDTH; i+=1) {
            for(int j=0; j < HEIGHT; j+=1) {
                    Tree tree = new Tree();                    
                    addObject(tree, i, j);      
            }
        }
        long t2 = System.currentTimeMillis();                  
       // System.out.println("Populated with " + WIDTH*HEIGHT + " trees in: " + (t2-t1));   
    }
    
    /**
     * Populate the forrest with some trees.
     */
    public void populate(int density) 
    {
        int n = 0;
        int sum=0;
        for(int i=0; i < WIDTH; i++) {
            for(int j=0; j < HEIGHT; j++) {
                if(random.nextInt(100) < density) {
                    long t1 = System.currentTimeMillis();
                    Tree tree = new Tree();
                    long t2 = System.currentTimeMillis();                  
                    long t3 = System.currentTimeMillis();
                    addObject(tree, i, j);                   
                    long t4 = System.currentTimeMillis();
                    n++;
                    sum+= t2-t1;
                }
            }
        }
    }
}