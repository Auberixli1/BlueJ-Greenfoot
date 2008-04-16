package greenfoot.sound;

import greenfoot.event.SimulationEvent;
import greenfoot.event.SimulationListener;
import greenfoot.util.GreenfootUtil;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.Mixer.Info;

import bluej.Config;

/**
 * Plays sounds from a file or URL. Several sounds can be played at the same
 * time.
 * 
 * <p>
 * 
 * The sounds will be stopped when a compiling or instantiating a new world.
 * 
 * @author Poul Henriksen
 * 
 */
public class SoundPlayer implements SimulationListener
{
    /**
     * Holds a list of all sounds currently playing. We use this list to stop
     * the sounds.
     */
    private List<Sound> playingSounds = new ArrayList<Sound>();

    /** singleton */
    private static SoundPlayer instance;
    
    private SoundCache soundCache;

    
    /**
     * Only use clips when the size of the clip is below this value.
     */
    private static final int maxClipSize = 500 * 1000;

    private SoundPlayer()
    {
        soundCache = new SoundCache();
        new Thread() {
            public void run() {
                init();
            }
        }.start();
    }

    /**
     * Initialize sound player to avoid initial delay when playing sounds. This
     * is a hack to make it faster on the mac, where there is a bug that makes
     * it look up some of the classes on the server. Looking up these classes
     * can take a very long time since they do not exist, so we force them to be
     * looked up when the scenario is started instead of when the first sound is
     * played. <p>
     * The two classes that we have to force loading <p>
     * com.sun.media.sound.DirectAudioDeviceProvider <p>
     * com.sun.media.sound.PortMixerProvider
     */
    private void init()
    {
        if (Config.isMacOS()) {       
            new Thread("MacAppletAudioHack") {
                private Info[] mixers;
                public void run() {
                    while (true) {
                        try {
                            mixers = AudioSystem.getMixerInfo();
                            Thread.sleep(1000);
                        }
                        catch (Throwable ex) {}
                    }
                }
            }.start();            
        }
        
        // create a line and keep it open forever, maybe that will avoid reloading of the classes
    }

    public synchronized static SoundPlayer getInstance()
    {
        if (instance == null) {
            instance = new SoundPlayer();
        }
        return instance;
    }

    /**
     * Stops all sounds currently played by the soundplayer. Includes paused
     * sounds. Stopped sounds can NOT be resumed.
     * 
     */
    public synchronized void stop()
    {
        for (Sound sound : playingSounds) {
            sound.stop();
        }
        playingSounds.clear();
    }

    /**
     * Pauses all sounds. Can be resumed.
     * 
     */
    public synchronized void pause()
    {
        for (Sound sound : playingSounds) {
            sound.pause();
        }
    }

    /**
     * Resumes paused sounds.
     * 
     */
    public synchronized void resume()
    {
        for (Sound sound : playingSounds) {
            sound.resume();
        }
    }

    /**
     * Plays the sound from file.
     * 
     * @param file Name of a file or an url
     * 
     * @throws LineUnavailableException if a matching line is not available due to resource restrictions
     * @throws IOException if an I/O exception occurs
     * @throws SecurityException if a matching line is not available due to security restrictions
     * @throws UnsupportedAudioFileException if the URL does not point to valid audio file data
     * @throws IllegalArgumentException if the system does not support at least one line matching the specified Line.Info object through any installed mixer
     */
    public void play(final String file)
        throws IOException, UnsupportedAudioFileException, LineUnavailableException, SecurityException, IllegalArgumentException
    {
        SoundClip sound = soundCache.get(file);
        if(sound != null) {
            playingSounds.add(sound);
            sound.play();
        }
        else {  // not in cache
            // First, determine the size of the sound, if possible
            URL url = GreenfootUtil.getURL(file, "sounds");
            int size = url.openConnection().getContentLength();

            if (size == -1 || size > maxClipSize) {
                // If we can not get the size, or if it is a big file we stream it
                // in a thread.
                final Sound soundStream = new SoundStream(url, SoundPlayer.this);
                playingSounds.add(soundStream);
                Thread t = new Thread() {
                    public void run()
                    {
                        try {
                            soundStream.play();
                            // Will not actually throw any of the exception.
                            // Only the clip does that at this point.
                        }
                        catch (IllegalArgumentException e) {}
                        catch (SecurityException e) {}
                        catch (LineUnavailableException e) {}
                        catch (IOException e) {}
                        catch (UnsupportedAudioFileException e) {}
                    }
                };
                t.start();
            }
            else {
                // The sound is small enough to be loaded into memory as a clip.
                sound = new SoundClip(file, url, SoundPlayer.this);
                soundCache.put(sound);
                
                
                playingSounds.add(sound);
                sound.play();
            }
        }
    }

    /**
     * Method that should be called by a sound when it is finished playing.
     */
    synchronized void soundFinished(Sound s)
    {
        playingSounds.remove(s);
    }

    /**
     * Stop sounds when simulation is disabled (a new world is created).
     */
    public void simulationChanged(SimulationEvent e)
    {
        if (e.getType() == SimulationEvent.DISABLED) {
            stop();
        }
        else if (e.getType() == SimulationEvent.STOPPED) {
            // pause();
        }
        else if (e.getType() == SimulationEvent.STARTED) {
            // resume();
        }
    }
}
