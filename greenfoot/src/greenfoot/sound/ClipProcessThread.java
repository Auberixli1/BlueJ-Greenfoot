/*
 This file is part of the Greenfoot program. 
 Copyright (C) 2012  Poul Henriksen and Michael Kolling 
 
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
package greenfoot.sound;

import java.util.LinkedList;

/**
 * A thread to process certain sound commands, which for some reason
 * must be processed in a separate thread.
 * 
 * @author Davin McCall
 */
public class ClipProcessThread extends Thread
{
    public static int PLAY = 0;
    public static int LOOP = 1;
    public static int CLOSE = 2;
    
    private static class Entry
    {
        SoundClip clip;
        int command;
    }
    
    private LinkedList<Entry> queue = new LinkedList<Entry>();
    
    public ClipProcessThread()
    {
        setDaemon(true);
        start();
    }
    
    public void addToQueue(SoundClip clip, int command)
    {
        synchronized (queue) {
            Entry entry = new Entry();
            entry.clip = clip;
            entry.command = command;
            queue.add(entry);
            queue.notify();
        }
    }
    
    @Override
    public void run()
    {
        try {
            Entry item;
            while (true) {
                synchronized (queue) {
                    while (queue.isEmpty()) {
                        queue.wait();
                    }
                    item = queue.removeFirst();
                }

                item.clip.processCommand(item.command);
            }
        }
        catch (InterruptedException ie) { }
    }
}
