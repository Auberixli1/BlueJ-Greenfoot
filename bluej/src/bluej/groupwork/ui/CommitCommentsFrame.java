/*
 This file is part of the BlueJ program. 
 Copyright (C) 1999-2009,2010,2014,2016  Michael Kolling and John Rosenberg 
 
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
package bluej.groupwork.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;

import threadchecker.OnThread;
import threadchecker.Tag;
import bluej.BlueJTheme;
import bluej.Config;
import bluej.groupwork.CommitFilter;
import bluej.groupwork.Repository;
import bluej.groupwork.StatusHandle;
import bluej.groupwork.StatusListener;
import bluej.groupwork.TeamStatusInfo;
import bluej.groupwork.TeamUtils;
import bluej.groupwork.TeamworkCommand;
import bluej.groupwork.TeamworkCommandResult;
import bluej.groupwork.actions.CommitAction;
import bluej.groupwork.actions.PushAction;
import bluej.pkgmgr.BlueJPackageFile;
import bluej.pkgmgr.Project;
import bluej.utility.DBox;
import bluej.utility.DBoxLayout;
import bluej.utility.DialogManager;
import bluej.utility.EscapeDialog;
import bluej.utility.SwingWorker;
import bluej.utility.Utility;
import java.util.LinkedList;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;


/**
 * A Swing based user interface to add commit comments.
 * @author Bruce Quig
 * @version $Id$
 */
public class CommitCommentsFrame extends EscapeDialog implements CommitAndPushInterface
{
    private JTable commitOrPushFiles;
    private JPanel topPanel;
    private JPanel bottomPanel;
    private JTextArea commitText;
    private JButton commitButton,pushButton;
    private JCheckBox includeLayout;
    private ActivityIndicator progressBar;
    private CommitAction commitAction;
    private CommitWorker commitWorker;
    private PushAction pushAction;

    private Project project;
    
    private Repository repository;
    private MyTableModel commitOrPushTableModel;
    private String[] columnNames;
    
    /* These lists will contain the files to be commited and pushed. */
    private LinkedList<TeamStatusInfo> filesToCommitList,filesToPushList;
    
    private Set<TeamStatusInfo> changedLayoutFiles;
    
    /** The packages whose layout should be committed compulsorily */
    private Set<File> packagesToCommmit = new HashSet<File>();
    
    private boolean pushWithNoChanges = false;
    
    private static String noFilesToCommit = Config.getString("team.nocommitfiles"); 

    public CommitCommentsFrame(Project proj)
    {
        project = proj;
        changedLayoutFiles = new HashSet<TeamStatusInfo>();
        repository = project.getTeamSettingsController().getRepository(false);
        createUI();
        DialogManager.centreDialog(this);
    }
    
    public void setVisible(boolean show)
    {
        super.setVisible(show);
        if (show) {
            // we want to set comments and commit action to disabled
            // until we know there is something to commit
            commitAction.setEnabled(false);
            commitText.setEnabled(false);
            includeLayout.setSelected(false);
            includeLayout.setEnabled(false);
            changedLayoutFiles.clear();
            commitOrPushTableModel.clear();
            
            repository = project.getRepository();
            
            if (repository != null) {
                try {
                    project.saveAllEditors();
                    project.saveAll();
                }
                catch (IOException ioe) {
                    String msg = DialogManager.getMessage("team-error-saving-project");
                    if (msg != null) {
                        msg = Utility.mergeStrings(msg, ioe.getLocalizedMessage());
                        DialogManager.showErrorText(this, msg);
                    }
                }
                startProgress();
                commitWorker = new CommitWorker();
                commitWorker.start();
            }
            else {
                super.setVisible(false);
            }
        }
    }
    
    /**
     * Create the user-interface for the error display dialog.
     */
    protected void createUI()
    {
        if (repository.isDVCS()){
            setTitle(Config.getString("team.commit.dcvs.title"));
        }else {
            setTitle(Config.getString("team.commit.title"));
        }
        if (repository.isDVCS()){
            columnNames = new String[]{"File Name",
                                "Status",
                                "Commit",
                                "Push"};
        }else {
            columnNames = new String[]{"File Name",
                                "Status",
                                "Commit"};
        }
        commitOrPushTableModel = new MyTableModel(columnNames);
        filesToCommitList = new LinkedList<>();
        filesToPushList = new LinkedList<>();
        
        //setIconImage(BlueJTheme.getIconImage());
        setLocation(Config.getLocation("bluej.commitdisplay"));

        // save position when window is moved
        addComponentListener(new ComponentAdapter() {
                public void componentMoved(ComponentEvent event)
                {
                    Config.putLocation("bluej.commitdisplay", getLocation());
                }
            });

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setBorder(BlueJTheme.generalBorderWithStatusBar);
        splitPane.setResizeWeight(0.5);

        topPanel = new JPanel();

        JScrollPane commitFileScrollPane = new JScrollPane();

        {
            topPanel.setLayout(new BorderLayout());

            JLabel commitFilesLabel = new JLabel(Config.getString(
                        "team.commit.files"));
            commitFilesLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
            topPanel.add(commitFilesLabel, BorderLayout.NORTH);

            commitOrPushFiles = new JTable(commitOrPushTableModel);
            commitOrPushFiles.setEnabled(false);
            commitFileScrollPane.setViewportView(commitOrPushFiles);

            //Center headers.
            TableCellRenderer rendererFromHeader = commitOrPushFiles.getTableHeader().getDefaultRenderer();
            JLabel headerLabel = (JLabel) rendererFromHeader;
            headerLabel.setHorizontalAlignment(JLabel.CENTER);

            
            commitOrPushFiles.getColumnModel().getColumn(0).setPreferredWidth(100);
            commitOrPushFiles.getColumnModel().getColumn(1).setPreferredWidth(90);
            
            if (repository.isDVCS()){
                commitOrPushFiles.getColumnModel().getColumn(columnNames.length-2).setPreferredWidth(20);
            }
            commitOrPushFiles.getColumnModel().getColumn(columnNames.length-1).setPreferredWidth(20);
                        
            topPanel.add(commitFileScrollPane, BorderLayout.CENTER);
        }

        splitPane.setTopComponent(topPanel);

        bottomPanel = new JPanel();

        {
            bottomPanel.setLayout(new BorderLayout());

            JLabel commentLabel = new JLabel(Config.getString(
                        "team.commit.comment"));
            commentLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
            bottomPanel.add(commentLabel, BorderLayout.NORTH);

            commitText = new JTextArea("");
            commitText.setRows(6);
            commitText.setColumns(42);

            Dimension size = commitText.getPreferredSize();
            size.width = commitText.getMinimumSize().width;
            commitText.setMinimumSize(size);

            JScrollPane commitTextScrollPane = new JScrollPane(commitText);
            commitTextScrollPane.setMinimumSize(size);
            bottomPanel.add(commitTextScrollPane, BorderLayout.CENTER);

            commitAction = new CommitAction(this);
            commitButton = BlueJTheme.getOkButton();
            commitButton.setAction(commitAction);
            getRootPane().setDefaultButton(commitButton);
            
            if (repository.isDVCS()){
                pushAction = new PushAction(this);
                pushButton = BlueJTheme.getOkButton();
                pushButton.setAction(pushAction);
            }
                
            JButton closeButton = BlueJTheme.getCancelButton();
            closeButton.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e)
                    {
                        commitWorker.abort();
                        commitAction.cancel();
                        if (repository.isDVCS()) pushAction.cancel();
                        setVisible(false);
                    }
                });
           
            DBox buttonPanel = new DBox(DBoxLayout.X_AXIS, 0, BlueJTheme.commandButtonSpacing, 0.5f);
            buttonPanel.setBorder(BlueJTheme.generalBorder);
            
            progressBar = new ActivityIndicator();
            progressBar.setRunning(false);
            
            DBox checkBoxPanel = new DBox(DBoxLayout.Y_AXIS, 0, BlueJTheme.commandButtonSpacing, 0.5f);
            includeLayout = new JCheckBox(Config.getString("team.commit.includelayout"));
            includeLayout.setEnabled(false);
            includeLayout.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e)
                {
                    JCheckBox layoutCheck = (JCheckBox)e.getSource();
                    if(layoutCheck.isSelected()) {
                        addModifiedLayouts();
                        if(!commitButton.isEnabled()){
                            commitAction.setEnabled(true);
                            if (repository.isDVCS()) pushAction.setEnabled(false);
                            commitText.setEnabled(true);
                        }
                                
                    }
                    // unselected
                    else {
                        removeModifiedLayouts();
                        if(isCommitListEmpty()){
                            commitAction.setEnabled(false);
                            if (repository.isDVCS() && !filesToPushList.isEmpty()) pushAction.setEnabled(true);
                            commitText.setEnabled(false);
                        }
                    }
                }
            });

            checkBoxPanel.add(includeLayout);
            checkBoxPanel.add(buttonPanel);
            
            buttonPanel.add(progressBar);
            buttonPanel.add(commitButton);
            if (repository.isDVCS()) buttonPanel.add(pushButton);
            buttonPanel.add(closeButton);
            bottomPanel.add(checkBoxPanel, BorderLayout.SOUTH);
        }

        splitPane.setBottomComponent(bottomPanel);

        getContentPane().add(splitPane);
        pack();
    }

    public String getComment()
    {
        return commitText.getText();
    }

    public void setComment(String newComment)
    {
        commitText.setText(newComment);
    }

    public void reset()
    {
        commitOrPushTableModel.clear();
        filesToCommitList.clear();
        filesToPushList.clear();
        setComment("");
    }
    
    private void removeModifiedLayouts()
    {
        // remove modified layouts from list of files shown for commit
        for(Iterator<TeamStatusInfo> it = changedLayoutFiles.iterator(); it.hasNext(); ) {
            TeamStatusInfo info = it.next();
            if (! packagesToCommmit.contains(info.getFile().getParentFile()) 
                    && ! filesToCommitList.contains(info) 
                    && !filesToPushList.contains(info)) {
                commitOrPushTableModel.removeElement(info);
            }
        }
        if (commitOrPushTableModel.isEmpty()) {
            commitOrPushTableModel.addElement(noFilesToCommit);
            commitText.setEnabled(false);
        }
    }
    
    private boolean isCommitListEmpty()
    {
        return filesToCommitList.isEmpty();
    }
    
    private void addModifiedLayouts()
    {
        if(commitOrPushTableModel.contains(noFilesToCommit)) {
            commitOrPushTableModel.removeElement(noFilesToCommit);
            commitText.setEnabled(true);
        }
        // add diagram layout files to list of files to be committed
        for (Iterator<TeamStatusInfo> it = changedLayoutFiles.iterator(); it.hasNext();) {
            TeamStatusInfo info = it.next();
            File parentFile = info.getFile().getParentFile();
            if (!packagesToCommmit.contains(parentFile)
                    && !filesToCommitList.contains(info)
                    && !filesToPushList.contains(info)) {
                commitOrPushTableModel.addElement(info);
            }
        }
    }
    
    /**
     * Get a list of the layout files to be committed
     */
    public Set<File> getChangedLayoutFiles()
    {
        Set<File> files = new HashSet<File>();
        for(Iterator<TeamStatusInfo> it = changedLayoutFiles.iterator(); it.hasNext(); ) {
            TeamStatusInfo info = it.next();
            files.add(info.getFile());
        }
        return files;
    }
    
    /**
     * Remove a file from the list of changes layout files.
     */
    private void removeChangedLayoutFile(File file)
    {
        for(Iterator<TeamStatusInfo> it = changedLayoutFiles.iterator(); it.hasNext(); ) {
            TeamStatusInfo info = it.next();
            if (info.getFile().equals(file)) {
                it.remove();
                return;
            }
        }        
    }
    
    /**
     * Get a set of the layout files which have changed (with status info).
     */
    public Set<TeamStatusInfo> getChangedLayoutInfo()
    {
        return changedLayoutFiles;
    }
    
    public boolean includeLayout()
    {
        return includeLayout != null && includeLayout.isSelected();
    }
    
    /**
     * Start the activity indicator.
     */
    public void startProgress()
    {
        progressBar.setRunning(true);
    }

    /**
     * Stop the activity indicator. Call from any thread.
     */
    public void stopProgress()
    {
        progressBar.setRunning(false);
    }
    
    public Project getProject()
    {
        return project;
    }
    
    private void setLayoutChanged(boolean hasChanged)
    {
        includeLayout.setEnabled(hasChanged);
    }
    
    private class MyTableModel extends AbstractTableModel
    {

        public String[] columnNames;
        private final ArrayList<Object> data = new ArrayList<>();

        MyTableModel(String[] columnNames)
        {
            this.columnNames = columnNames;
        }

        @Override
        public int getRowCount()
        {
            return data.size();
        }

        @Override
        public int getColumnCount()
        {
            return columnNames.length;
        }

        @Override
        public String getColumnName(int column)
        {
            return columnNames[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex)
        {
            Object result = null;
            Object objectItem = data.get(rowIndex);
            if (objectItem instanceof TeamStatusInfo) {
                TeamStatusInfo item = (TeamStatusInfo) objectItem;
                if (columnIndex < columnNames.length) {
                    switch (columnIndex) {
                        case 0:
                            result = ResourceDescriptor.getResource(project, item, true);
                            break;
                        case 1:
                            if (item.getStatus() != TeamStatusInfo.STATUS_UPTODATE) {
                                //operation is the local operation.
                                result = TeamStatusInfo.getStatusString(item.getStatus());
                            } else if (item.getRemoteStatus() != TeamStatusInfo.STATUS_UPTODATE) {
                                result = TeamStatusInfo.getDCVSStatusString(item.getRemoteStatus(),true);
                            } else {
                                //status is up-to-date.
                                result = TeamStatusInfo.getStatusString(TeamStatusInfo.STATUS_UPTODATE);
                            }
                            break;
                        case 2:
                            result = item.getStatus() != TeamStatusInfo.STATUS_UPTODATE;
                            break;
                        case 3:
                            result = item.getRemoteStatus() != TeamStatusInfo.STATUS_UPTODATE;
                            break;
                        default:
                            break;
                    }
                }
            } else if (columnIndex == 0) {
                result = objectItem;
            }
            return result;
        }

        /**
         * returns the Class each row element belongs to. This method is mostly
         * used internally to draw the table.
         *
         * @param c row number.
         * @return
         */
        @Override
        public Class getColumnClass(int c)
        {
            Class result = null;
            if (c < columnNames.length) {
                switch (c) {
                    case 0:
                        result = String.class;
                        break;
                    case 1:
                        result = String.class;
                        break;
                    case 2:
                        result = Boolean.class;
                        break;
                    case 3:
                        result = Boolean.class;
                        break;
                    default:
                        break;
                }
            } else {
                result = null;
            }
            return result;
        }

        /**
         * clear the tableModel.
         */
        public void clear()
        {
            data.clear();
            fireTableDataChanged();
        }

        /**
         * checks if the tableModel is empty.
         *
         * @return
         */
        public boolean isEmpty()
        {
            return data.isEmpty();
        }

        /**
         * checks if the tableModel contains the passed element.
         *
         * @param element
         * @return
         */
        public boolean contains(Object element)
        {
            return data.contains(element);
        }

        /**
         * adds a String as an element of the tableModel
         *
         * @param element
         */
        public void addElement(String element)
        {
            data.add(element);
            int index = data.indexOf(element);
            fireTableRowsInserted(index, index);
        }

        /**
         * adds a TeamStatusInfo to the tableModel
         *
         * @param element
         */
        public void addElement(TeamStatusInfo element)
        {
            data.add(element);
            int index = data.indexOf(element);
            fireTableRowsInserted(index, index);
        }

        /**
         * removes the passed element from the tableModel.
         *
         * @param element
         */
        public void removeElement(Object element)
        {
            int index = data.indexOf(element);
            data.remove(element);
            fireTableRowsDeleted(index, index);
        }

        /**
         * removes the i-th element of the tableModel.
         *
         * @param i
         */
        public void remove(int i)
        {
            int index = data.indexOf(i);
            data.remove(i);
            fireTableRowsDeleted(index, index);
        }

        /**
         * returns the i-th element of the tableModel
         *
         * @param i
         * @return
         */
        public Object get(int i)
        {
            return data.get(i);
        }

        /**
         * gets the number of elements in the TableModel.
         *
         * @return
         */
        public int size()
        {
            return data.size();
        }

    }

    /**
    * Inner class to do the actual version control status check to populate commit dialog
    * to ensure that the UI is not blocked during remote call
    */
    class CommitWorker extends SwingWorker implements StatusListener
    {
        List<TeamStatusInfo> response;
        TeamworkCommand command;
        TeamworkCommandResult result;
        private boolean aborted, isCommitAvailable, isPushAvailable;

        public CommitWorker()
        {
            super();
            response = new ArrayList<>();
            FileFilter filter = project.getTeamSettingsController().getFileFilter(true);
            command = repository.getStatus(this, filter, false);
            isCommitAvailable = false;
            isPushAvailable = false;
        }
        
        public boolean isCommitAvailable()
        {
            return this.isCommitAvailable;
        }
        
        public boolean isPushAvailable()
        {
            return this.isPushAvailable;
        }
        
        /*
         * @see bluej.groupwork.StatusListener#gotStatus(bluej.groupwork.TeamStatusInfo)
         */
        @OnThread(Tag.Any)
        @Override
        public void gotStatus(TeamStatusInfo info)
        {
            response.add(info);
        }
        
        /*
         * @see bluej.groupwork.StatusListener#statusComplete(bluej.groupwork.CommitHandle)
         */
        @OnThread(Tag.Any)
        @Override
        public void statusComplete(StatusHandle statusHandle)
        {
            pushWithNoChanges = statusHandle.pushNeeded();
            commitAction.setStatusHandle(statusHandle);
            if (repository.isDVCS()) pushAction.setStatusHandle(statusHandle); 
        }
        
        @OnThread(Tag.Unique)
        @Override
        public Object construct()
        {
            result = command.getResult();
            return response;
        }
        
        public void abort()
        {
            command.cancel();
            aborted = true;
        }

        @Override
        public void finished()
        {
            stopProgress();
            if (!aborted) {
                if (result.isError()) {
                    TeamUtils.handleServerResponse(result, CommitCommentsFrame.this);
                    setVisible(false);
                } else if (response != null) {
                    Set<File> filesToCommit = new HashSet<>();
                    Set<File> filesToAdd = new LinkedHashSet<>();
                    Set<File> filesToDelete = new HashSet<>();
                    Set<File> mergeConflicts = new HashSet<>();
                    Set<File> deleteConflicts = new HashSet<>();
                    Set<File> otherConflicts = new HashSet<>();
                    Set<File> needsMerge = new HashSet<>();
                    Set<File> modifiedLayoutFiles = new HashSet<>();

                    List<TeamStatusInfo> info = response;
                    getCommitFileSets(info, filesToCommit, filesToAdd, filesToDelete,
                            mergeConflicts, deleteConflicts, otherConflicts,
                            needsMerge, modifiedLayoutFiles, false);

                    commitAction.setFiles(filesToCommit);
                    commitAction.setNewFiles(filesToAdd);
                    commitAction.setDeletedFiles(filesToDelete);

                    //update the table with files to commit.
                    updateLists(info, filesToCommit, filesToCommitList);
                    updateLists(info, filesToAdd, filesToCommitList);
                    updateLists(info, filesToDelete, filesToCommitList);

                    if (!filesToAdd.isEmpty() || !filesToCommit.isEmpty() || !filesToDelete.isEmpty()) {
                        this.isCommitAvailable = true;
                    }

                    if (!mergeConflicts.isEmpty() || !deleteConflicts.isEmpty() || !otherConflicts.isEmpty()) {

                        handleConflicts(mergeConflicts, deleteConflicts,
                                otherConflicts, null);
                        return;
                    }

                    if (repository.isDVCS()) {
                        Set<File> filesToCommitInPush = new HashSet<>();
                        Set<File> filesToAddInPush = new HashSet<>();
                        Set<File> filesToDeleteInPush = new HashSet<>();
                        Set<File> mergeConflictsInPush = new HashSet<>();
                        Set<File> deleteConflictsInPush = new HashSet<>();
                        Set<File> otherConflictsInPush = new HashSet<>();
                        Set<File> needsMergeInPush = new HashSet<>();
                        Set<File> modifiedLayoutFilesInPush = new HashSet<>();

                        getCommitFileSets(info, filesToCommitInPush, filesToAddInPush, filesToDeleteInPush,
                                mergeConflictsInPush, deleteConflictsInPush, otherConflictsInPush,
                                needsMergeInPush, modifiedLayoutFilesInPush, true);

                        this.isPushAvailable = pushWithNoChanges || !filesToCommitInPush.isEmpty() || !filesToAddInPush.isEmpty() 
                                               || !filesToDeleteInPush.isEmpty() || !modifiedLayoutFilesInPush.isEmpty();
                        //in the case we are commiting the resolution of a merge, we should check if the same file that is beingmarked as otherConflict 
                        //on the remote branch is being commitd to the local branch. if it is, then this is the user resolution to the conflict and we should 
                        //procceed with the commit. and then with the push as normal.
                        boolean conflicts;
                        conflicts = !mergeConflictsInPush.isEmpty() || !deleteConflictsInPush.isEmpty()
                                || !otherConflictsInPush.isEmpty() || !needsMergeInPush.isEmpty();
                        if (!this.isCommitAvailable && conflicts) {
                            //there is a file in some of the conflict list.
                            //check if this fill will commit normally. if it will, we should allow.
                            Set<File> conflictingFilesInPush = new HashSet<>();
                            conflictingFilesInPush.addAll(mergeConflictsInPush);
                            conflictingFilesInPush.addAll(deleteConflictsInPush);
                            conflictingFilesInPush.addAll(otherConflictsInPush);
                            conflictingFilesInPush.addAll(needsMergeInPush);

                            for (File conflictEntry : conflictingFilesInPush) {
                                if (filesToCommit.contains(conflictEntry)) {
                                    conflictingFilesInPush.remove(conflictEntry);
                                    mergeConflictsInPush.remove(conflictEntry);
                                    deleteConflictsInPush.remove(conflictEntry);
                                    otherConflictsInPush.remove(conflictEntry);
                                    needsMergeInPush.remove(conflictEntry);

                                }
                                if (filesToAdd.contains(conflictEntry)) {
                                    conflictingFilesInPush.remove(conflictEntry);
                                    mergeConflictsInPush.remove(conflictEntry);
                                    deleteConflictsInPush.remove(conflictEntry);
                                    otherConflictsInPush.remove(conflictEntry);
                                    needsMergeInPush.remove(conflictEntry);
                                }
                                if (filesToDelete.contains(conflictEntry)) {
                                    conflictingFilesInPush.remove(conflictEntry);
                                    mergeConflictsInPush.remove(conflictEntry);
                                    deleteConflictsInPush.remove(conflictEntry);
                                    otherConflictsInPush.remove(conflictEntry);
                                    needsMergeInPush.remove(conflictEntry);
                                }
                            }
                            conflicts = !conflictingFilesInPush.isEmpty();
                        }

                        if (!this.isCommitAvailable && conflicts) {

                            handleConflicts(mergeConflictsInPush, deleteConflictsInPush,
                                    otherConflictsInPush, null);
                            return;
                        }

                        updateLists(info, filesToCommitInPush, filesToPushList);
                        updateLists(info, filesToAddInPush, filesToPushList);
                        updateLists(info, filesToDeleteInPush, filesToPushList);
                        updateLists(info, mergeConflictsInPush, filesToPushList);
                        updateLists(info, modifiedLayoutFilesInPush, filesToPushList);

                    }

                }

                updateCommitOrPushListModel(filesToCommitList);
                if (repository.isDVCS()) {
                    updateCommitOrPushListModel(filesToPushList);
                }

                if (filesToCommitList.isEmpty() && filesToPushList.isEmpty()) {
                    if (commitOrPushTableModel.isEmpty()) {
                        commitOrPushTableModel.addElement(noFilesToCommit);
                    }
                } else if (commitOrPushTableModel.size() > 1 && (commitOrPushTableModel.get(0) instanceof String)) {
                    //not empty!
                    commitOrPushTableModel.remove(0);
                }
                if (repository.isDVCS()) {

                    if (isCommitAvailable()) {
                        //there are files to commit.
                        commitText.setEnabled(true);
                        commitText.requestFocusInWindow();
                        commitAction.setEnabled(true);
                        pushAction.setEnabled(false);
                    } else if (!isCommitAvailable() && isPushAvailable()) {
                        //there are files to push
                        commitText.setEnabled(false);
                        commitAction.setEnabled(false);
                        pushAction.setEnabled(true);
                    } else if (!isCommitAvailable() && !isPushAvailable()) {
                        commitText.setEnabled(false);
                        commitAction.setEnabled(false);
                        pushAction.setEnabled(false);
                    }
                } else if (commitOrPushTableModel.isEmpty()) {
                    commitOrPushTableModel.addElement(noFilesToCommit);
                } else {
                    commitText.setEnabled(true);
                    commitText.requestFocusInWindow();
                    commitAction.setEnabled(true);
                }
            }
        }

        private void handleConflicts(Set<File> mergeConflicts, Set<File> deleteConflicts,
                Set<File> otherConflicts, Set<File> needsMerge)
        {
            String dlgLabel;
            String filesList;
            
            // If there are merge conflicts, handle those first
            if (! mergeConflicts.isEmpty()) {
                dlgLabel = "team-resolve-merge-conflicts";
                filesList = buildConflictsList(mergeConflicts);
            }
            else if (! deleteConflicts.isEmpty()) {
                dlgLabel = "team-resolve-conflicts-delete";
                filesList = buildConflictsList(deleteConflicts);
            }
            else if (! otherConflicts.isEmpty()) {
                dlgLabel = "team-update-first";
                filesList = buildConflictsList(otherConflicts);
            }
            else {
                stopProgress();
                DialogManager.showMessage(CommitCommentsFrame.this, "team-uptodate-failed");
                CommitCommentsFrame.this.setVisible(false);
                return;
            }

            stopProgress();
            DialogManager.showMessageWithText(CommitCommentsFrame.this, dlgLabel, filesList);
            CommitCommentsFrame.this.setVisible(false);
        }
        
        /**
         * Buid a list of files, max out at 10 files.
         * @param conflicts
         * @return
         */
        private String buildConflictsList(Set<File> conflicts)
        {
            String filesList = "";
            Iterator<File> i = conflicts.iterator();
            for (int j = 0; j < 10 && i.hasNext(); j++) {
                File conflictFile = (File) i.next();
                filesList += "    " + conflictFile.getName() + "\n";
            }

            if (i.hasNext()) {
                filesList += "    " + Config.getString("team.commit.moreFiles");
            }
            
            return filesList;
        }
        
        /**
         * Go through the status list, and figure out which files to commit, and
         * of those which are to be added (i.e. which aren't in the repository) and
         * which are to be removed.
         * 
         * @param info  The list of files with status (List of TeamStatusInfo)
         * @param filesToCommit  The set to store the files to commit in
         * @param filesToAdd     The set to store the files to be added in
         * @param filesToRemove  The set to store the files to be removed in
         * @param mergeConflicts The set to store files with merge conflicts in.
         * @param deleteConflicts The set to store files with conflicts in, which
         *                        need to be resolved by first deleting the local file
         * @param otherConflicts  The set to store files with "locally deleted" conflicts
         *                        (locally deleted, remotely modified).
         * @param needsMerge     The set of files which are updated locally as
         *                       well as in the repository (required merging).
         * @param conflicts      The set to store unresolved conflicts in
         * 
         * @param remote         false if this is a non-distributed repository.
         */
        private void getCommitFileSets(List<TeamStatusInfo> info, Set<File> filesToCommit, Set<File> filesToAdd,
                Set<File> filesToRemove, Set<File> mergeConflicts, Set<File> deleteConflicts,
                Set<File> otherConflicts, Set<File> needsMerge, Set<File> modifiedLayoutFiles, boolean remote)
        {

            CommitFilter filter = new CommitFilter();
            Map<File,File> modifiedLayoutDirs = new HashMap<>();

            for (TeamStatusInfo statusInfo : info) {
                File file = statusInfo.getFile();
                boolean isPkgFile = BlueJPackageFile.isPackageFileName(file.getName());
                int status;
                //select status to use.
                if (remote){
                    status = statusInfo.getRemoteStatus();
                } else {
                    status = statusInfo.getStatus();
                }

                if(filter.accept(statusInfo, !remote)) {
                    if (!isPkgFile) {
                        filesToCommit.add(file);
                    } 
                    else if (status == TeamStatusInfo.STATUS_NEEDSADD
                            || status == TeamStatusInfo.STATUS_DELETED
                            || status == TeamStatusInfo.STATUS_CONFLICT_LDRM) {
                        // Package file which must be committed.
                        if (packagesToCommmit.add(statusInfo.getFile().getParentFile())) {
                            File otherPkgFile = modifiedLayoutDirs.remove(file.getParentFile());
                            if (otherPkgFile != null) {
                                removeChangedLayoutFile(otherPkgFile);
                                filesToCommit.add(otherPkgFile);
                            }
                        }
                        filesToCommit.add(statusInfo.getFile());
                    } 
                    else {
                        // add file to list of files that may be added to commit
                        File parentFile = file.getParentFile();
                        if (!packagesToCommmit.contains(parentFile)) {
                            modifiedLayoutFiles.add(file);
                            modifiedLayoutDirs.put(parentFile, file);
                            // keep track of StatusInfo objects representing changed diagrams
                            changedLayoutFiles.add(statusInfo);
                        } else {
                            // We must commit the file unconditionally
                            filesToCommit.add(file);
                        }
                    }

                    if (status == TeamStatusInfo.STATUS_NEEDSADD) {
                        filesToAdd.add(statusInfo.getFile());
                    } 
                    else if (status == TeamStatusInfo.STATUS_DELETED
                            || status == TeamStatusInfo.STATUS_CONFLICT_LDRM) {
                        filesToRemove.add(statusInfo.getFile());
                    }
                } 
                else if (!isPkgFile) {
                    if (status == TeamStatusInfo.STATUS_HASCONFLICTS) {
                        mergeConflicts.add(statusInfo.getFile());
                    }
                    if (status == TeamStatusInfo.STATUS_UNRESOLVED
                            || status == TeamStatusInfo.STATUS_CONFLICT_ADD
                            || status == TeamStatusInfo.STATUS_CONFLICT_LMRD) {
                        deleteConflicts.add(statusInfo.getFile());
                    }
                    if (status == TeamStatusInfo.STATUS_CONFLICT_LDRM) {
                        otherConflicts.add(statusInfo.getFile());
                    }
                    if (status == TeamStatusInfo.STATUS_NEEDSMERGE) {
                        needsMerge.add(statusInfo.getFile());
                    }
                }
            }

            if (!remote) {
                setLayoutChanged(!changedLayoutFiles.isEmpty());
            }
        }
        
        /**
         * Update CommitOrPushListModel with the list of statusInfo. This will update
         * the table.
         * @param listOfStatusInfo 
         */
        private void updateCommitOrPushListModel(Iterable<TeamStatusInfo> listOfStatusInfo)
        {
            for (TeamStatusInfo entry : listOfStatusInfo) {
                commitOrPushTableModel.addElement(entry);
            }
        }
        
        private TeamStatusInfo getStatusInfo(List<TeamStatusInfo> list, File f)
        {
            TeamStatusInfo r = null;
            for (TeamStatusInfo item:list){
                if (item.getFile().equals(f)){
                    r = item;
                    break;
                }
            }
            return r;
        }
        
        private void updateLists(List<TeamStatusInfo> tsiList, Set<File> fileSet, LinkedList<TeamStatusInfo> displayList)
        {
            for (File f : fileSet) {
                TeamStatusInfo item = getStatusInfo(tsiList, f);
                if (item != null && !displayList.contains(item)) {
                    displayList.add(item);
                }
            }
        }
    }
}
