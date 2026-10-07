package com.cn.schrodinger.understatus;

import com.cn.schrodinger.understatus.toolbox.core.DiffCalculator;
import com.cn.schrodinger.understatus.toolbox.core.ToolTask;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.UIManager;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;

/**
 * Enhanced Diff Tab Panel.
 * Supports Unified Diff and Side-by-Side Diff, line number counters,
 * diff additions/deletions statistics, and Swap A/B action.
 *
 * @author peter/antigravity
 */
public class DiffTabPanel extends JPanel {

    private final ToolTask tasks = new ToolTask(this);
    private DiffCalculator.Result latest;
    private JTextArea textAreaA;
    private JTextArea textAreaB;

    private JTextPane unifiedDiffPane;
    private JTextPane sideAPane;
    private JTextPane sideBPane;

    private JSplitPane mainSplit;
    private JPanel diffViewerContainer;
    private JPanel sideBySidePanel;
    private JScrollPane unifiedScrollPane;

    private JComboBox<String> viewModeCombo;
    private JLabel statLabel;

    public DiffTabPanel() {
        initComponents();
        tasks.watch(textAreaA, () -> latest = null);
        tasks.watch(textAreaB, () -> latest = null);
    }

    private void initComponents() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // Top comparison split: Input Text A and Input Text B
        JPanel inputsPanel = new JPanel(new GridLayout(1, 2, 6, 6));

        JPanel panelA = new JPanel(new BorderLayout(2, 2));
        panelA.setBorder(BorderFactory.createTitledBorder("原始文本 A (Original Text A)"));
        textAreaA = new JTextArea("粘贴原始文本 A (Text A)");
        textAreaA.setFont(new Font("Monospaced", Font.PLAIN, 12));
        panelA.add(new JScrollPane(textAreaA), BorderLayout.CENTER);
        inputsPanel.add(panelA);

        JPanel panelB = new JPanel(new BorderLayout(2, 2));
        panelB.setBorder(BorderFactory.createTitledBorder("比对文本 B (Modified Text B)"));
        textAreaB = new JTextArea("粘贴比对文本 B (Text B)");
        textAreaB.setFont(new Font("Monospaced", Font.PLAIN, 12));
        panelB.add(new JScrollPane(textAreaB), BorderLayout.CENTER);
        inputsPanel.add(panelB);

        // Bottom difference viewer
        diffViewerContainer = new JPanel(new BorderLayout());

        // 1. Unified Diff Viewer
        unifiedDiffPane = new JTextPane();
        unifiedDiffPane.setEditable(false);
        unifiedDiffPane.setFont(new Font("Monospaced", Font.PLAIN, 12));
        unifiedScrollPane = new JScrollPane(unifiedDiffPane);

        // 2. Side-by-side Viewers
        sideAPane = new JTextPane();
        sideAPane.setEditable(false);
        sideAPane.setFont(new Font("Monospaced", Font.PLAIN, 12));

        sideBPane = new JTextPane();
        sideBPane.setEditable(false);
        sideBPane.setFont(new Font("Monospaced", Font.PLAIN, 12));

        sideBySidePanel = new JPanel(new GridLayout(1, 2, 4, 4));
        JPanel leftSide = new JPanel(new BorderLayout());
        leftSide.setBorder(BorderFactory.createTitledBorder("文本 A 变更"));
        leftSide.add(new JScrollPane(sideAPane), BorderLayout.CENTER);

        JPanel rightSide = new JPanel(new BorderLayout());
        rightSide.setBorder(BorderFactory.createTitledBorder("文本 B 变更"));
        rightSide.add(new JScrollPane(sideBPane), BorderLayout.CENTER);

        sideBySidePanel.add(leftSide);
        sideBySidePanel.add(rightSide);

        diffViewerContainer.add(unifiedScrollPane, BorderLayout.CENTER);

        // Main Vertical Split
        mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, inputsPanel, diffViewerContainer);
        mainSplit.setResizeWeight(0.4);
        add(mainSplit, BorderLayout.CENTER);

        // Control Toolbar
        JPanel bottomBar = new JPanel(new BorderLayout(8, 4));
        bottomBar.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        JPanel leftControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftControls.add(new JLabel("视图模式:"));

        viewModeCombo = new JComboBox<>(new String[]{"统一视图 (Unified)", "左右双栏 (Side-by-Side)"});
        viewModeCombo.setFont(new Font("SansSerif", Font.PLAIN, 12));
        viewModeCombo.addActionListener(e -> {
            diffViewerContainer.removeAll();
            if (viewModeCombo.getSelectedIndex() == 0) {
                diffViewerContainer.add(unifiedScrollPane, BorderLayout.CENTER);
            } else {
                diffViewerContainer.add(sideBySidePanel, BorderLayout.CENTER);
            }
            diffViewerContainer.revalidate();
            diffViewerContainer.repaint();
            renderActiveView();
        });
        leftControls.add(viewModeCombo);

        statLabel = new JLabel("就绪");
        statLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        statLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        leftControls.add(statLabel);
        bottomBar.add(leftControls, BorderLayout.WEST);

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));

        JButton swapBtn = new JButton("⇄ 互换 A/B");
        swapBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        swapBtn.setToolTipText("互换文本 A 与文本 B 并重新对比");
        swapBtn.addActionListener(e -> {
            String temp = textAreaA.getText();
            textAreaA.setText(textAreaB.getText());
            textAreaB.setText(temp);
            triggerComparison();
        });

        JButton compareBtn = new JButton("➔ 开始对比 (Compare)");
        compareBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        compareBtn.addActionListener(e -> triggerComparison());

        JButton clearBtn = new JButton("清空");
        clearBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        clearBtn.addActionListener(e -> {
            tasks.cancel();
            latest = null;
            textAreaA.setText("");
            textAreaB.setText("");
            unifiedDiffPane.setText("");
            sideAPane.setText("");
            sideBPane.setText("");
            statLabel.setText("已清空");
        });

        JButton copyBtn = new JButton("复制对比结果");
        copyBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        copyBtn.addActionListener(e -> {
            String text = viewModeCombo.getSelectedIndex() == 0
                    ? unifiedDiffPane.getText()
                    : ("--- Text A ---\n" + sideAPane.getText() + "\n\n--- Text B ---\n" + sideBPane.getText());
            CommonUtils.copyToClipboard(text);
        });

        rightButtons.add(swapBtn);
        rightButtons.add(compareBtn);
        rightButtons.add(clearBtn);
        rightButtons.add(copyBtn);
        bottomBar.add(rightButtons, BorderLayout.EAST);

        add(bottomBar, BorderLayout.SOUTH);
    }

    private void triggerComparison() {
        latest = null;
        try {
            String textA = ToolTask.snapshot(textAreaA);
            String textB = ToolTask.snapshot(textAreaB);
            statLabel.setText("正在比较…");
            tasks.submit(() -> DiffCalculator.calculateDetailed(textA, textB), result -> {
                latest = result;
                renderActiveView();
            }, error -> statLabel.setText("比较失败: " + error));
        } catch (IllegalArgumentException ex) {
            tasks.cancel();
            statLabel.setText(ex.getMessage());
        }
    }

    private record Rendered(StyledDocument first, StyledDocument second, String summary) {}

    private void renderActiveView() {
        if (latest == null) return;
        DiffCalculator.Result result = latest;
        boolean sideBySide = viewModeCombo.getSelectedIndex() != 0;
        boolean dark = isDarkTheme();
        tasks.submit(() -> render(result, sideBySide, dark), rendered -> {
            if (sideBySide) {
                sideAPane.setDocument(rendered.first());
                sideBPane.setDocument(rendered.second());
            } else unifiedDiffPane.setDocument(rendered.first());
            statLabel.setText(rendered.summary());
        }, error -> statLabel.setText("显示失败: " + error));
    }

    private static Rendered render(DiffCalculator.Result result, boolean side, boolean dark) throws javax.swing.text.BadLocationException {
        var first = new javax.swing.text.DefaultStyledDocument();
        var second = new javax.swing.text.DefaultStyledDocument();
        int additions = 0, deletions = 0, unchanged = 0;
        for (var line : result.lines()) {
            if (line.type == 1) additions++;
            else if (line.type == -1) deletions++;
            else unchanged++;
        }
        int shown = 0, chars = 0, aNo = 1, bNo = 1;
        for (var line : result.lines()) {
            com.cn.schrodinger.understatus.toolbox.core.ToolLimits.checkInterrupted();
            if (shown >= 1000 || chars + line.text.length() > 100_000) break;
            chars += line.text.length();
            shown++;
            if (side) {
                appendLine(first, line.type == 1 ? "     |\n" : aNo++ + " | " + line.text + "\n", line.type == -1 ? -1 : 0, dark);
                appendLine(second, line.type == -1 ? "     |\n" : bNo++ + " | " + line.text + "\n", line.type == 1 ? 1 : 0, dark);
            } else appendLine(first, shown + " | " + line.text + "\n", line.type, dark);
        }
        String summary = "+" + additions + " / -" + deletions + " / =" + unchanged;
        if (result.coarse()) summary += "；变更区过大，按整段删除/新增显示（非最小差异）";
        if (shown < result.lines().size()) {
            summary += "；仅显示前 " + shown + " 行（上限 1,000 行 / 100,000 字符）";
            appendLine(first, "\n[显示已截断]\n", 0, dark);
            if (side) appendLine(second, "\n[显示已截断]\n", 0, dark);
        }
        return new Rendered(first, second, summary);
    }

    private static void appendLine(StyledDocument document, String text, int type, boolean dark) throws javax.swing.text.BadLocationException {
        var attributes = new javax.swing.text.SimpleAttributeSet();
        Color foreground = type == 1 ? (dark ? new Color(82, 196, 26) : new Color(34, 139, 34))
                : type == -1 ? (dark ? new Color(255, 77, 79) : new Color(205, 38, 38))
                : dark ? new Color(200, 210, 225) : new Color(40, 50, 70);
        StyleConstants.setForeground(attributes, foreground);
        document.insertString(document.getLength(), text, attributes);
    }

    private boolean isDarkTheme() {
        Color bg = getBackground();
        if (bg == null) bg = UIManager.getColor("Panel.background");
        if (bg == null) return false;
        double lum = 0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue();
        return lum < 128;
    }
}
