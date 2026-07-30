package com.cn.schrodinger.understatus;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import com.cn.schrodinger.understatus.settings.SettingsRepository;
import com.cn.schrodinger.understatus.settings.UnderStatusSettings;

/**
 * Modeless JDialog acting as a floating popover developer vault.
 * Mounts 15 tabs under a scrollable layout, supporting direct child modal focus tracing.
 *
 * @author peter/antigravity
 */
public class QuickNoteCalcDialog extends JDialog {

    public boolean isPickingColor = false;

    public QuickNoteCalcDialog(java.awt.Window owner) {
        super(owner, ModalityType.MODELESS);
        setUndecorated(true);
        initComponents();
        setupPopoverFocusBehavior();
        pack();
    }

    private void initComponents() {
        getRootPane().setBorder(BorderFactory.createLineBorder(new Color(130, 130, 130), 1));
        setLayout(new BorderLayout());

        // Load size from preferences
        UnderStatusSettings settings = SettingsRepository.getDefault().load();
        int width = settings.toolboxWidth();
        int height = settings.toolboxHeight();

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setPreferredSize(new Dimension(width, height));
        tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

        // Mount 15 modular panels in logical sequence
        tabbedPane.addTab("便签 (Notes)", new NotesTabPanel());
        tabbedPane.addTab("计算器 (Calc)", new CalcTabPanel());
        tabbedPane.addTab("编解码/时间戳 (Utils)", new UtilsTabPanel());
        tabbedPane.addTab("编码转换 (Codec)", new EncodingTabPanel());
        tabbedPane.addTab("JWT解码 (JWT)", new JwtTabPanel());
        tabbedPane.addTab("JSON格式化 (JSON)", new JsonTabPanel());
        tabbedPane.addTab("XML格式化 (XML)", new XmlTabPanel());
        tabbedPane.addTab("SQL格式化 (SQL)", new SqlTabPanel());
        tabbedPane.addTab("文本处理 (Text)", new TextTabPanel());
        tabbedPane.addTab("正则测试 (Regex)", new RegexTabPanel());
        tabbedPane.addTab("取色器 (Color)", new ColorTabPanel());
        tabbedPane.addTab("哈希生成 (Hash)", new HashTabPanel());
        tabbedPane.addTab("文本对比 (Diff)", new DiffTabPanel());
        tabbedPane.addTab("Cron解析 (Cron)", new CronTabPanel());
        tabbedPane.addTab("生成器 (Gen)", new GenTabPanel());
        tabbedPane.addTab("音乐播放器 (Music)", new MusicTabPanel());

        add(tabbedPane, BorderLayout.CENTER);

        // Header Panel with Settings gear & Close button
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        headerPanel.setBackground(UIManager.getColor("Panel.background"));

        JButton settingsBtn = new JButton("⚙️");
        settingsBtn.setFont(settingsBtn.getFont().deriveFont(11f));
        settingsBtn.setToolTipText("状态栏与工具箱显示配置");
        settingsBtn.setBorderPainted(false);
        settingsBtn.setContentAreaFilled(false);
        settingsBtn.setFocusPainted(false);
        settingsBtn.setMargin(new Insets(2, 4, 2, 4));
        settingsBtn.addActionListener(e -> launchSettingsDialog());
        headerPanel.add(settingsBtn);

        JButton closeBtn = new JButton("×");
        closeBtn.setFont(closeBtn.getFont().deriveFont(15f));
        closeBtn.setToolTipText("关闭 (Close)");
        closeBtn.setBorderPainted(false);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setFocusPainted(false);
        closeBtn.setMargin(new Insets(0, 4, 0, 4));
        closeBtn.addActionListener(e -> dispose());
        headerPanel.add(closeBtn);

        add(headerPanel, BorderLayout.NORTH);
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

    private void setupPopoverFocusBehavior() {
        addWindowFocusListener(new WindowFocusListener() {
            @Override
            public void windowGainedFocus(WindowEvent e) {}

            @Override
            public void windowLostFocus(WindowEvent e) {
                if (isPickingColor) {
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
