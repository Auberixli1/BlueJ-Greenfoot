/*
 This file is part of the Greenfoot program. 
 Copyright (C) 2005-2009,2010,2012,2014,2015  Poul Henriksen and Michael Kolling
 
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
package greenfoot.guifx.images;

import bluej.utility.Debug;
import greenfoot.util.GreenfootUtil;

import java.io.File;
import java.io.FilenameFilter;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javafx.collections.FXCollections;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/**
 * A list component which displays a list of images (found in a directory) with their
 * filenames.
 * 
 * @author Davin McCall
 * @author Poul Henriksen
 * @author Amjad Altadmri
 */
public class ImageLibList extends ListView<ImageLibList.ImageListEntry>
{   
    /** The directory whose images are currently displayed in this list */
    private File directory;
    private File defaultImage;
    private final String[] imageFileExtensions = new String[] { "jpg", "jpeg", "png", "gif" };

    /**
     * Construct an empty ImageLibList.
     */
    ImageLibList(final boolean editable)
    {      
        super();
        this.setEditable(editable);
        this.setCellFactory(param -> new ImageLibCell());
    }

    /**
     * Construct an empty ImageLibList, and populate it with entries from
     * the given directory.
     * 
     * @param directory  The directory to retrieve images from
     * @param defaultImage The image the class will have if it does not specify one (blank or parent's)
     */
    ImageLibList(File directory, final boolean editable, File defaultImage)
    {
        this(editable);
        this.defaultImage = defaultImage;
        setDirectory(directory);
    }

    /**
     * Clear the list and re-populate it with images from the given directory.
     * 
     * @param directory   The directory to retrieve images from
     */
    public void setDirectory(File directory)
    {
        this.directory = directory;

        // We can accept only image files.
        FilenameFilter filter = (dir, name) -> Stream.of(imageFileExtensions).anyMatch(extension -> name.toLowerCase().endsWith(extension));
        File[] imageFiles = directory.listFiles(filter);
        if (imageFiles == null)
        {
            imageFiles = new File[0];
        }
        
        Arrays.sort(imageFiles);
        setItems(FXCollections.observableArrayList(Arrays.stream(imageFiles).map(ImageListEntry::new).collect(Collectors.toList())));
    }

    /**
     * Get the current directory this list is pointing to.
     */
    public File getDirectory()
    {
        return directory;
    }

    /**
     * Refresh the contents of the list.
     */
    public void refresh()
    {
        if(getDirectory()!=null) {
            setDirectory(getDirectory());
        }
    }

    /**
     * If the given file exists in this list, it will be selected.
     */
    public void select(File imageFile)
    {
        refresh();
        this.getSelectionModel().select(new ImageListEntry(imageFile));
    }

    static class ImageLibCell extends ListCell<ImageListEntry>
    {
        @Override
        public void updateItem(ImageListEntry item, boolean empty)
        {
            super.updateItem(item, empty);
            if (item != null)
            {
                setText(GreenfootUtil.removeExtension(item.getImageName()));
                setTooltip(new Tooltip(item.getImageName()));
                setGraphic(item.getIcon());
            }
            else
            {
                setText(null);
                setGraphic(null);
            }
        }
    }

    public class ImageListEntry
    {
        File imageFile;
        ImageView icon;

        private ImageListEntry(File file)
        {
            this.imageFile = file;
        }

        /**
         * Returns the image file name.
         *
         * @return the image's file name.
         */
        private String getImageName()
        {
            return imageFile.getName();
        }

        /**
         * Return a thumbnail icon of the image. It checks its existence
         * first to avoid reconstruction each time.
         *
         * @return an Image view of the image file.
         */
        private ImageView getIcon()
        {
            if (icon == null && imageFile != null)
            {
                icon = getImageView();
            }
            return icon;
        }

        /**
         * Loads the image file and construct an image view of it.
         * This view is returned of the loading succeeded, otherwise
         * an empty view is returned.
         *
         * @return an image view containing a thumbnail of the image.
         */
        private ImageView getImageView()
        {
            try
            {
                return new ImageView(new Image(imageFile.toURI().toURL().toExternalForm(), 30, 30, true, true));
            }
            catch (MalformedURLException e)
            {
                Debug.reportError(e);
            }
            return new ImageView();
        }
        
        public boolean equals(Object other)
        {
            if(!(other instanceof ImageListEntry))
            {
                return false;
            }
            ImageListEntry otherEntry = (ImageListEntry) other;
            //other cannot be null here because it passed the instanceof check above:
            if (otherEntry.imageFile == null && this.imageFile == null)
            {
                return true;
            }
            else if (otherEntry.imageFile == null || this.imageFile == null)
            {
                return false;
            }
            else
            {
                return otherEntry.imageFile.equals(this.imageFile);
            }
        }
        
        public int hashCode() 
        {
            return imageFile.hashCode();
        }
    }
}