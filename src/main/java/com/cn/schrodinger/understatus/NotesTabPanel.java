package com.cn.schrodinger.understatus;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import com.cn.schrodinger.understatus.settings.SettingsRepository;
import com.cn.schrodinger.understatus.toolbox.notes.Note;
import com.cn.schrodinger.understatus.toolbox.notes.NoteCodec;

/**
 * Redeveloped notebook tab panel.
 * Supports N independent notes with sidebar JList manager.
 * Supports add (+), delete (-), and rename options, with backward data compatibility.
 *
 * @author peter/antigravity
 */
public class NotesTabPanel extends JPanel {

    private final List<NoteEntry> notesList = new ArrayList<>();
    private final NoteCodec noteCodec = new NoteCodec();
    private DefaultListModel<String> listModel;
    private JList<String> noteJList;
    private JTextArea noteTextArea;
    private boolean isUpdatingSelection = false;

    public NotesTabPanel() {
        initComponents();
        loadNotesFromPreferences();
        if (!notesList.isEmpty()) {
            noteJList.setSelectedIndex(0);
        }
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Left Panel (JList & CRUD Buttons)
        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.setPreferredSize(new Dimension(130, 0));

        listModel = new DefaultListModel<>();
        noteJList = new JList<>(listModel);
        noteJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        noteJList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int idx = noteJList.getSelectedIndex();
            if (idx >= 0) {
                isUpdatingSelection = true;
                noteTextArea.setText(notesList.get(idx).content);
                noteTextArea.setEnabled(true);
                isUpdatingSelection = false;
            } else {
                noteTextArea.setText("");
                noteTextArea.setEnabled(false);
            }
        });
        
        leftPanel.add(new JScrollPane(noteJList), BorderLayout.CENTER);

        // CRUD Button panel
        JPanel buttonPanel = new JPanel(new GridLayout(1, 3, 2, 2));
        JButton addBtn = new JButton("+");
        addBtn.setMargin(new Insets(2, 2, 2, 2));
        addBtn.setToolTipText("添加新便签 (Add note)");
        addBtn.addActionListener(e -> addNote());

        JButton delBtn = new JButton("-");
        delBtn.setMargin(new Insets(2, 2, 2, 2));
        delBtn.setToolTipText("删除选中便签 (Delete note)");
        delBtn.addActionListener(e -> deleteNote());

        JButton renameBtn = new JButton("✎");
        renameBtn.setMargin(new Insets(2, 2, 2, 2));
        renameBtn.setToolTipText("重命名便签 (Rename note)");
        renameBtn.addActionListener(e -> renameNote());

        buttonPanel.add(addBtn);
        buttonPanel.add(delBtn);
        buttonPanel.add(renameBtn);
        leftPanel.add(buttonPanel, BorderLayout.SOUTH);

        // Right Panel (Active JTextArea)
        noteTextArea = new JTextArea();
        noteTextArea.setLineWrap(true);
        noteTextArea.setWrapStyleWord(true);
        noteTextArea.setFont(UIManager.getFont("TextArea.font").deriveFont(12f));
        noteTextArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { saveCurrentNoteText(); }
            @Override
            public void removeUpdate(DocumentEvent e) { saveCurrentNoteText(); }
            @Override
            public void changedUpdate(DocumentEvent e) { saveCurrentNoteText(); }
        });
        JScrollPane textScrollPane = new JScrollPane(noteTextArea);

        // Split Pane container
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, textScrollPane);
        splitPane.setDividerLocation(130);
        add(splitPane, BorderLayout.CENTER);
    }

    private void saveCurrentNoteText() {
        if (isUpdatingSelection) return;
        int idx = noteJList.getSelectedIndex();
        if (idx >= 0) {
            notesList.get(idx).content = noteTextArea.getText();
            saveNotesToPreferences();
        }
    }

    private void addNote() {
        String title = JOptionPane.showInputDialog(this, "请输入新建便签名称:", "新建便签 (Add Note)", JOptionPane.PLAIN_MESSAGE);
        if (title == null) return;
        title = title.trim();
        if (title.isEmpty()) {
            title = "新建便签 " + (notesList.size() + 1);
        }
        NoteEntry newEntry = new NoteEntry(title, "");
        notesList.add(newEntry);
        listModel.addElement(title);
        saveNotesToPreferences();
        noteJList.setSelectedIndex(notesList.size() - 1);
    }

    private void deleteNote() {
        int idx = noteJList.getSelectedIndex();
        if (idx < 0) return;

        int confirm = JOptionPane.showConfirmDialog(this, "确定删除便签 [" + notesList.get(idx).title + "] 吗？", "确认删除", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        notesList.remove(idx);
        listModel.remove(idx);
        saveNotesToPreferences();

        if (!notesList.isEmpty()) {
            noteJList.setSelectedIndex(Math.max(0, idx - 1));
        } else {
            // Always keep at least 1 note
            NoteEntry def = new NoteEntry("便签 1", "");
            notesList.add(def);
            listModel.addElement(def.title);
            saveNotesToPreferences();
            noteJList.setSelectedIndex(0);
        }
    }

    private void renameNote() {
        int idx = noteJList.getSelectedIndex();
        if (idx < 0) return;

        String oldTitle = notesList.get(idx).title;
        String newTitle = JOptionPane.showInputDialog(this, "请输入便签新名称:", oldTitle);
        if (newTitle == null) return;
        newTitle = newTitle.trim();
        if (newTitle.isEmpty()) return;

        notesList.get(idx).title = newTitle;
        listModel.set(idx, newTitle);
        saveNotesToPreferences();
        noteJList.setSelectedIndex(idx);
    }

    private void saveNotesToPreferences() {
        List<Note> notes = notesList.stream().map(entry -> new Note(entry.title, entry.content)).toList();
        SettingsRepository.getDefault().saveNotes(noteCodec.encode(notes));
    }

    private void loadNotesFromPreferences() {
        String data = SettingsRepository.getDefault().loadNotes();
        notesList.clear();
        listModel.clear();

        for (Note note : noteCodec.decode(data)) {
            NoteEntry entry = new NoteEntry(note.title(), note.content());
            notesList.add(entry);
            listModel.addElement(entry.title);
        }

        if (notesList.isEmpty()) {
            NoteEntry def = new NoteEntry("便签 1", "");
            notesList.add(def);
            listModel.addElement(def.title);
        }
    }

    public static class NoteEntry {
        public String title;
        public String content;

        public NoteEntry(String title, String content) {
            this.title = title;
            this.content = content;
        }
    }
}
