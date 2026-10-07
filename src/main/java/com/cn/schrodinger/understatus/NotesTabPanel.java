package com.cn.schrodinger.understatus;

import com.cn.schrodinger.understatus.toolbox.notes.NotesSession;
import com.cn.schrodinger.understatus.toolbox.notes.NotesSession.Entry;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.HierarchyEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.PlainDocument;

/** Notebook view over the shared, asynchronously persisted notes session. */
public class NotesTabPanel extends JPanel {

    private final NotesSession session;
    private final DefaultListModel<Entry> listModel = new DefaultListModel<>();
    private final JList<Entry> noteJList = new JList<>(listModel);
    private final JTextArea noteTextArea = new JTextArea();
    private final JLabel status = new JLabel();
    private final JButton retryButton = new JButton("重试 (Retry)");
    private final JButton addButton = new JButton("+");
    private final JButton deleteButton = new JButton("-");
    private final JButton renameButton = new JButton("✎");
    private final PlainDocument emptyDocument = new PlainDocument();
    private boolean updatingSelection;
    private Runnable unsubscribe;

    public NotesTabPanel() {
        this(NotesSession.getDefault());
    }

    public NotesTabPanel(NotesSession session) {
        this.session = session;
        initComponents();
        attach();
        session.ensureLoaded();
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && !isShowing()) {
                flushSaveToPreferences();
            }
        });

        JPanel leftPanel = new JPanel(new BorderLayout(5, 5));
        leftPanel.setPreferredSize(new Dimension(130, 0));
        noteJList.setName("notes-list");
        noteJList.getAccessibleContext().setAccessibleName("便签列表 (Notes list)");
        noteJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        noteJList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !updatingSelection) {
                session.flush();
                showSelectedNote();
            }
        });
        leftPanel.add(new JScrollPane(noteJList), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new GridLayout(1, 3, 2, 2));
        configureButton(addButton, "添加新便签 (Add note)");
        addButton.addActionListener(e -> addNote());
        configureButton(deleteButton, "删除选中便签 (Delete note)");
        deleteButton.addActionListener(e -> deleteNote());
        configureButton(renameButton, "重命名便签 (Rename note)");
        renameButton.addActionListener(e -> renameNote());
        buttonPanel.add(addButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(renameButton);
        leftPanel.add(buttonPanel, BorderLayout.SOUTH);

        noteTextArea.setName("notes-editor");
        noteTextArea.getAccessibleContext().setAccessibleName("便签内容 (Note content)");
        noteTextArea.setLineWrap(true);
        noteTextArea.setWrapStyleWord(true);
        if (UIManager.getFont("TextArea.font") != null) {
            noteTextArea.setFont(UIManager.getFont("TextArea.font").deriveFont(12f));
        }
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                leftPanel, new JScrollPane(noteTextArea));
        splitPane.setDividerLocation(130);
        add(splitPane, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(5, 0));
        status.setName("notes-status");
        status.getAccessibleContext().setAccessibleName("便签保存状态 (Note save status)");
        retryButton.setName("notes-retry");
        retryButton.getAccessibleContext().setAccessibleName("重试加载或保存便签 (Retry notes)");
        retryButton.addActionListener(e -> session.retry());
        footer.add(status, BorderLayout.CENTER);
        footer.add(retryButton, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);
    }

    private static void configureButton(JButton button, String name) {
        button.setMargin(new Insets(2, 2, 2, 2));
        button.setToolTipText(name);
        button.getAccessibleContext().setAccessibleName(name);
    }

    private void attach() {
        if (unsubscribe == null) unsubscribe = session.subscribe(this::refresh);
        refresh();
    }

    @Override public void addNotify() {
        super.addNotify();
        attach();
    }

    @Override public void removeNotify() {
        // Capture while documents are attached; completion never waits on IO.
        flushSaveToPreferences();
        if (unsubscribe != null) {
            unsubscribe.run();
            unsubscribe = null;
        }
        // JTextArea's UI listeners otherwise keep a disposed panel reachable
        // through the process-wide document, even after its session unsubscribe.
        noteTextArea.setDocument(emptyDocument);
        super.removeNotify();
    }

    private void refresh() {
        List<Entry> entries = session.entries();
        boolean changed = entries.size() != listModel.size();
        for (int i = 0; !changed && i < entries.size(); i++) {
            changed = entries.get(i) != listModel.get(i);
        }
        if (changed) {
            Entry selected = noteJList.getSelectedValue();
            int previousIndex = noteJList.getSelectedIndex();
            updatingSelection = true;
            try {
                listModel.clear();
                for (Entry entry : entries) listModel.addElement(entry);
                int selectedIndex = selected == null ? -1 : entries.indexOf(selected);
                if (selectedIndex < 0 && !entries.isEmpty()) {
                    selectedIndex = Math.max(0, Math.min(previousIndex - 1, entries.size() - 1));
                }
                noteJList.setSelectedIndex(selectedIndex);
            } finally {
                updatingSelection = false;
            }
        }
        noteJList.repaint();
        showSelectedNote();
        noteJList.setEnabled(session.isLoaded());
        addButton.setEnabled(session.isLoaded());
        deleteButton.setEnabled(noteJList.getSelectedValue() != null);
        renameButton.setEnabled(noteJList.getSelectedValue() != null);
        status.setText(session.statusText());
        status.setToolTipText(session.detail().isEmpty() ? null : session.detail());
        status.getAccessibleContext().setAccessibleDescription(session.detail());
        retryButton.setVisible(session.canRetry());
    }

    private void showSelectedNote() {
        Entry selected = noteJList.getSelectedValue();
        var document = selected == null ? emptyDocument : selected.document();
        if (noteTextArea.getDocument() != document) noteTextArea.setDocument(document);
        noteTextArea.setEnabled(session.isLoaded() && selected != null);
    }

    /** Retained for callers; captures on the EDT and queues background storage. */
    public void flushSaveToPreferences() {
        if (SwingUtilities.isEventDispatchThread()) session.flush();
        else SwingUtilities.invokeLater(session::flush);
    }

    private void addNote() {
        String title = JOptionPane.showInputDialog(this, "请输入新建便签名称:",
                "新建便签 (Add Note)", JOptionPane.PLAIN_MESSAGE);
        if (title == null) return;
        title = title.trim();
        if (title.isEmpty()) title = "新建便签 " + (session.entries().size() + 1);
        Entry created = session.add(title);
        noteJList.setSelectedValue(created, true);
        session.flush();
    }

    private void deleteNote() {
        Entry selected = noteJList.getSelectedValue();
        if (selected == null) return;
        int confirm = JOptionPane.showConfirmDialog(this, "确定删除便签 [" + selected.title() + "] 吗？",
                "确认删除", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        session.delete(selected);
        session.flush();
    }

    private void renameNote() {
        Entry selected = noteJList.getSelectedValue();
        if (selected == null) return;
        String title = JOptionPane.showInputDialog(this, "请输入便签新名称:", selected.title());
        if (title == null || title.isBlank()) return;
        session.rename(selected, title);
        session.flush();
    }
}
