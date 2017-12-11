/*
 This file is part of the Greenfoot program. 
 Copyright (C) 2017  Poul Henriksen and Michael Kolling 
 
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
package greenfoot.guifx.classes;

import bluej.pkgmgr.Project;
import bluej.pkgmgr.target.ClassTarget;
import greenfoot.guifx.classes.ClassGroup.ClassInfo;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

/**
 * The class diagram on the right-hand side of the Greenfoot window.
 *
 * For now, this is very primitive, but is useful for implementing other Greenfoot functionality.
 */
public class ClassDiagram extends VBox
{
    private ClassTarget selected = null;
    // The three groups of classes in the display: World+subclasses, Actor+subclasses, Other
    private final ClassGroup worldClasses = new ClassGroup();
    private final ClassGroup actorClasses = new ClassGroup();
    private final ClassGroup otherClasses = new ClassGroup();

    public ClassDiagram(Project project)
    {
        getChildren().setAll(worldClasses, actorClasses, otherClasses);
        // Organise the current classes into their groups:
        calculateGroups(project.getUnnamedPackage().getClassTargets());
    }

    /**
     * Takes a list of ClassTargets in the project, and puts them into a tree structure
     * according to their superclass relations, with Actor and World subclasses
     * going into their own group.
     */
    private void calculateGroups(ArrayList<ClassTarget> originalClassTargets)
    {
        // Start by mapping everything to false;
        HashMap<ClassTarget, Boolean> classTargets = new HashMap<>();
        for (ClassTarget originalClassTarget : originalClassTargets)
        {
            classTargets.put(originalClassTarget, false);
        }
        // Note that the classTargets list will be modified by each findAllSubclasses call,
        // so the order here is very important.  Actor and World must come before other:
        
        // First, we must take out any World and Actor classes:
        List<ClassInfo> worldSubclasses = findAllSubclasses("greenfoot.World", classTargets);
        ClassInfo worldClassesInfo = new ClassInfo("World", null, worldSubclasses);
        worldClasses.setClasses(Collections.singletonList(worldClassesInfo));

        List<ClassInfo> actorSubclasses = findAllSubclasses("greenfoot.Actor", classTargets);
        ClassInfo actorClassesInfo = new ClassInfo("Actor", null, actorSubclasses);
        actorClasses.setClasses(Collections.singletonList(actorClassesInfo));
        
        // All other classes can be found by passing null, see docs on findAllSubclasses:
        otherClasses.setClasses(findAllSubclasses(null, classTargets));
    }

    /**
     * Finds all subclasses of the given fully-qualified parent class name.  The subclass search
     * is recursive, so if you pass "Grandparent", then both "Parent" and "Child" will be found 
     * and removed.  Any found subclasses will have their boolean changed to true in the given map,
     * and only those that currently map to false will be searched.
     * 
     * @param parentClassName The fully-qualified parent class name to search.  If null, then all classes
     *                        in the classTargets list will be processed and returned.
     * @param classTargets Class targets to search -- only those mapped to false will be searched.  If
     *                     they are processed into a ClassInfo, their value will be flipped to true.
     * @return The list of ClassInfo at the requested level (there may be a deeper tree inside).
     */
    private List<ClassInfo> findAllSubclasses(String parentClassName, Map<ClassTarget, Boolean> classTargets)
    {
        List<ClassInfo> curLevel = new ArrayList<>();
        for (Entry<ClassTarget, Boolean> classTargetAndVal : classTargets.entrySet())
        {
            // Ignore anything already mapped to true:
            if (classTargetAndVal.getValue() == true)
                continue;
            
            ClassTarget classTarget = classTargetAndVal.getKey();
            String superClass = classTarget.analyseSource().getSuperclass();
            boolean includeAtThisLevel;
            if (parentClassName == null)
            {
                // We want all classes, but we still want to pick out subclass relations.  Some classes
                // may have a parent class (e.g. java.util.List) that is not in the list of class targets, but
                // the class should still be included at the top-level.  The key test for top-level is:
                //   Is the parent class either null, or not present in the list?

                includeAtThisLevel = superClass == null || !classTargets.keySet().stream().anyMatch(ct -> Objects.equals(ct.getQualifiedName(), superClass));
            }
            else
            {
                // Does it directly inherit from the requested class?
                includeAtThisLevel = Objects.equals(superClass, parentClassName);
            }

            if (includeAtThisLevel)
            {
                // Update processed status before recursing:
                classTargetAndVal.setValue(true);

                List<ClassInfo> subClasses = findAllSubclasses(classTarget.getQualifiedName(), classTargets);
                curLevel.add(new ClassInfo(classTarget.getQualifiedName(), null, subClasses));
            }
        }
        return curLevel;
    }

    /**
     * Make the graphical item for a ClassTarget.  Currently just a Label.
     */
    private Node makeClassItem(ClassTarget classTarget)
    {
        Label label = new Label(classTarget.getBaseName());
        label.setOnContextMenuRequested(e -> {
            Class<?> cl = classTarget.getPackage().loadClass(classTarget.getQualifiedName());
            if (cl != null)
            {
                ContextMenu contextMenu = new ContextMenu();
                classTarget.getRole().createClassConstructorMenu(contextMenu.getItems(), classTarget, cl);
                if (!contextMenu.getItems().isEmpty())
                {
                    contextMenu.getItems().add(new SeparatorMenuItem());
                }
                classTarget.getRole().createClassStaticMenu(contextMenu.getItems(), classTarget, classTarget.hasSourceCode(), cl);
                contextMenu.show(label, e.getScreenX(), e.getScreenY());
            }
        });
        label.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 1)
            {
                selected = classTarget;
                // Hacky, for now until we sort out graphics for class diagram:
                for (Node other : getChildren())
                {
                    other.setStyle("");
                }
                label.setStyle("-fx-underline: true;");
            }
            else if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2)
            {
                classTarget.open();
            }
        });
        return label;
    }

    /**
     * Gets the currently selected class in the diagram.  May be null if no selection
     */
    public ClassTarget getSelectedClass()
    {
        return selected;
    }
}
