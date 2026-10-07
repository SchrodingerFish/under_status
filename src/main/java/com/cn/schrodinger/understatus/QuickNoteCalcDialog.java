package com.cn.schrodinger.understatus;

import com.cn.schrodinger.understatus.settings.SettingsRepository;
import com.cn.schrodinger.understatus.settings.UnderStatusSettings;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.prefs.Preferences;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Modeless JDialog acting as a floating popover developer vault.
 * Features 16 modular tabs with on-demand lazy initialization for instant loading,
 * persistent active tab memory, and mouse drag-to-move support.
 *
 * @author peter/antigravity
 */
public class QuickNoteCalcDialog extends JDialog {

    private static final String PREF_LAST_TAB = "last_toolbox_tab_index";
    private static final Preferences PREFS = Preferences.userNodeForPackage(QuickNoteCalcDialog.class);

    public boolean isPickingColor = false;
    public boolean isPinned = false;

    private JTabbedPane tabbedPane;
    private final List<TabEntry> tabEntries = new ArrayList<>();
    private Point dragOffset;

    private static final class TabEntry {
        final String title;
        final Supplier<Component> supplier;
        Component loadedComponent;

        TabEntry(String title, Supplier<Component> supplier) {
            this.title = title;
            this.supplier = supplier;
        }
    }

    public QuickNoteCalcDialog(java.awt.Window owner) {
        super(owner, ModalityType.MODELESS);
        setTitle(UiDefaults.text("Toolbox.title"));
        getAccessibleContext().setAccessibleDescription(UiDefaults.text("Toolbox.navigationHint"));
        setUndecorated(true);
        initComponents();
        setupPopoverFocusBehavior();
        setupKeyboardActions();
        pack();
    }

    private void initComponents() {
        getRootPane().setBorder(BorderFactory.createLineBorder(UiDefaults.border(), 1));
        setLayout(new BorderLayout());

        // Load size from preferences
        UnderStatusSettings settings = SettingsRepository.getDefault().load();
        int width = settings.toolboxWidth();
        int height = settings.toolboxHeight();

        tabbedPane = new JTabbedPane();
        tabbedPane.getAccessibleContext().setAccessibleName(UiDefaults.text("Toolbox.tabs"));
        tabbedPane.getAccessibleContext().setAccessibleDescription(UiDefaults.text("Toolbox.navigationHint"));
        tabbedPane.setPreferredSize(new Dimension(width, height));
        tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

        // Register 16 modular panels with lazy supplier definitions
        registerLazyTab(UiDefaults.text("Toolbox.tab.notes"), NotesTabPanel::new);
        registerLazyTab(UiDefaults.text("Toolbox.tab.calc"), CalcTabPanel::new);
        registerLazyTab(UiDefaults.text("Toolbox.tab.utils"), UtilsTabPanel::new);
        registerLazyTab(UiDefaults.text("Toolbox.tab.codec"), EncodingTabPanel::new);
        registerLazyTab(UiDefaults.text("Toolbox.tab.jwt"), JwtTabPanel::new);
        registerLazyTab(UiDefaults.text("Toolbox.tab.json"), JsonTabPanel::new);
        registerLazyTab(UiDefaults.text("Toolbox.tab.xml"), XmlTabPanel::new);
        registerLazyTab(UiDefaults.text("Toolbox.tab.sql"), SqlTabPanel::new);
        registerLazyTab(UiDefaults.text("Toolbox.tab.text"), TextTabPanel::new);
        registerLazyTab(UiDefaults.text("Toolbox.tab.regex"), RegexTabPanel::new);
        registerLazyTab(UiDefaults.text("Toolbox.tab.color"), ColorTabPanel::new);
        registerLazyTab(UiDefaults.text("Toolbox.tab.hash"), HashTabPanel::new);
        registerLazyTab(UiDefaults.text("Toolbox.tab.diff"), DiffTabPanel::new);
        registerLazyTab(UiDefaults.text("Toolbox.tab.cron"), CronTabPanel::new);
        registerLazyTab(UiDefaults.text("Toolbox.tab.gen"), GenTabPanel::new);
        registerLazyTab(UiDefaults.text("Toolbox.tab.music"), MusicTabPanel::new);

        // Tab change listener for lazy loading & active tab persistence
        tabbedPane.addChangeListener(e -> {
            int idx = tabbedPane.getSelectedIndex();
            if (idx >= 0 && idx < tabEntries.size()) {
                ensureTabLoaded(idx);
                PREFS.putInt(PREF_LAST_TAB, idx);
            }
        });

        // Restore last active tab
        int lastTabIdx = PREFS.getInt(PREF_LAST_TAB, 0);
        if (lastTabIdx < 0 || lastTabIdx >= tabEntries.size()) {
            lastTabIdx = 0;
        }
        ensureTabLoaded(lastTabIdx);
        tabbedPane.setSelectedIndex(lastTabIdx);

        add(tabbedPane, BorderLayout.CENTER);

        // Header Panel with title, Drag handle, Settings gear & Close button
        JPanel headerPanel = new JPanel(new BorderLayout(8, 0));
        headerPanel.setBackground(UIManager.getColor("Panel.background"));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 4));

        JLabel titleLabel = new JLabel(UiDefaults.text("Toolbox.title"));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 12f));
        titleLabel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        headerPanel.add(titleLabel, BorderLayout.WEST);

        // Drag to move dialog behavior
        MouseAdapter dragAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragOffset = e.getPoint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragOffset != null) {
                    Point curr = getLocation();
                    setLocation(curr.x + e.getX() - dragOffset.x, curr.y + e.getY() - dragOffset.y);
                }
            }
        };
        headerPanel.addMouseListener(dragAdapter);
        headerPanel.addMouseMotionListener(dragAdapter);

        JPanel actionBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actionBtns.setOpaque(false);

        JToggleButton pinBtn = new JToggleButton("📌");
        pinBtn.setFont(pinBtn.getFont().deriveFont(11f));
        UiDefaults.describe(pinBtn, UiDefaults.text("Toolbox.pin"), UiDefaults.text("Toolbox.pinHint"));
        pinBtn.setMargin(new Insets(2, 4, 2, 4));
        pinBtn.addActionListener(e -> {
            isPinned = pinBtn.isSelected();
            if (isPinned) {
                pinBtn.setText("📍");
                UiDefaults.describe(pinBtn, UiDefaults.text("Toolbox.unpin"), UiDefaults.text("Toolbox.unpinHint"));
            } else {
                pinBtn.setText("📌");
                UiDefaults.describe(pinBtn, UiDefaults.text("Toolbox.pin"), UiDefaults.text("Toolbox.pinHint"));
            }
        });
        actionBtns.add(pinBtn);

        JButton settingsBtn = new JButton("⚙️");
        settingsBtn.setFont(settingsBtn.getFont().deriveFont(11f));
        UiDefaults.describe(settingsBtn, UiDefaults.text("Action.settings"), UiDefaults.text("Toolbox.settingsHint"));
        settingsBtn.setMargin(new Insets(2, 4, 2, 4));
        settingsBtn.addActionListener(e -> launchSettingsDialog());
        actionBtns.add(settingsBtn);

        JButton closeBtn = new JButton("×");
        closeBtn.setFont(closeBtn.getFont().deriveFont(15f));
        UiDefaults.describe(closeBtn, UiDefaults.text("Action.close"), UiDefaults.text("Toolbox.closeHint"));
        closeBtn.setMargin(new Insets(0, 4, 0, 4));
        closeBtn.addActionListener(e -> dispose());
        actionBtns.add(closeBtn);

        headerPanel.add(actionBtns, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);
    }

    private void registerLazyTab(String title, Supplier<Component> supplier) {
        TabEntry entry = new TabEntry(title, supplier);
        tabEntries.add(entry);
        // Initially mount a lightweight placeholder JPanel
        JPanel placeholder = new JPanel();
        placeholder.setOpaque(false);
        tabbedPane.addTab(title, placeholder);
    }

    private void ensureTabLoaded(int index) {
        if (index < 0 || index >= tabEntries.size()) return;
        TabEntry entry = tabEntries.get(index);
        if (entry.loadedComponent == null) {
            entry.loadedComponent = entry.supplier.get();
            tabbedPane.setComponentAt(index, entry.loadedComponent);
            if (entry.loadedComponent instanceof JPanel panel) {
                panel.revalidate();
                panel.repaint();
            }
        }
    }

    private void launchSettingsDialog() {
        Window parent = SwingUtilities.getWindowAncestor(this);
        ToolbarSettingDialog configDlg = new ToolbarSettingDialog(parent, () -> {
            BottomToolbarView toolbar = findBottomToolbarView(parent);
            if (toolbar != null) {
                toolbar.loadSettings();
            }
            dispose();
        });

        isPickingColor = true;
        try {
            configDlg.setVisible(true);
        } finally {
            isPickingColor = false;
        }
    }

    private BottomToolbarView findBottomToolbarView(java.awt.Container container) {
        if (container instanceof BottomToolbarView) {
            return (BottomToolbarView) container;
        }
        if (container != null) {
            for (java.awt.Component comp : container.getComponents()) {
                if (comp instanceof java.awt.Container) {
                    BottomToolbarView found = findBottomToolbarView((java.awt.Container) comp);
                    if (found != null) {
                        return found;
                    }
                }
            }
        }
        return null;
    }

    private void setupKeyboardActions() {
        getRootPane().registerKeyboardAction(
                e -> dispose(),
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }

    private void setupPopoverFocusBehavior() {
        addWindowFocusListener(new WindowFocusListener() {
            @Override
            public void windowGainedFocus(WindowEvent e) {}

            @Override
            public void windowLostFocus(WindowEvent e) {
                if (isPinned || isPickingColor) {
                    return;
                }
                java.awt.Window opposite = e.getOppositeWindow();
                if (opposite != null) {
                    java.awt.Window owner = opposite;
                    while (owner != null) {
                        if (owner == QuickNoteCalcDialog.this) {
                            return; // Focus is captured by a sub-dialog or child window, do not close!
                        }
                        owner = owner.getOwner();
                    }
                }
                SwingUtilities.invokeLater(() -> dispose());
            }
        });
    }
}
