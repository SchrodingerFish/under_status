package com.cn.schrodinger.understatus;

import com.cn.schrodinger.understatus.toolbox.core.DiffCalculator;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;

/**
 * Enhanced Diff Tab Panel.
 * Supports Unified Diff and Side-by-Side Diff, line number counters,
 * diff additions/deletions statistics, and Swap A/B action.
 *
 * @author peter/antigravity
 */
public class DiffTabPanel extends JPanel {

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
    private SwingWorker<List<DiffCalculator.DiffLine>, Void> comparison;
    private List<DiffCalculator.DiffLine> preview = List.of();

    public DiffTabPanel() {
        initComponents();
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
            renderPreview();
            diffViewerContainer.removeAll();
            if (viewModeCombo.getSelectedIndex() == 0) {
                diffViewerContainer.add(unifiedScrollPane, BorderLayout.CENTER);
            } else {
                diffViewerContainer.add(sideBySidePanel, BorderLayout.CENTER);
            }
            diffViewerContainer.revalidate();
            diffViewerContainer.repaint();
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
            if (comparison != null) comparison.cancel(true);
            preview = List.of();
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
        String textA = textAreaA.getText();
        String textB = textAreaB.getText();

        if (comparison != null) comparison.cancel(true);
        statLabel.setText("正在对比… 大差异区域按整段替换；预览最多 2000 行/20 万字符");
        comparison = new SwingWorker<>() {
            @Override protected List<DiffCalculator.DiffLine> doInBackground() {
                return DiffCalculator.calculateDiff(textA, textB);
            }
            @Override protected void done() {
                if (isCancelled() || comparison != this) return;
                try {
                    List<DiffCalculator.DiffLine> diffs = get();
                    int count = 0, chars = 0;
                    while (count < diffs.size() && count < 2000 && chars + diffs.get(count).text.length() <= 200_000) {
                        chars += diffs.get(count++).text.length();
                    }
                    preview = List.copyOf(diffs.subList(0, count));
                    renderPreview();
                    long additions = diffs.stream().filter(d -> d.type == 1).count();
                    long deletions = diffs.stream().filter(d -> d.type == -1).count();
                    statLabel.setText("+" + additions + " / -" + deletions + "；预览 " + count + "/" + diffs.size()
                            + " 行（大差异按整段替换；复制仅含预览）");
                } catch (Exception ex) {
                    statLabel.setText("对比失败: " + (ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage()));
                }
            }
        };
        comparison.execute();
    }

    private void renderPreview() {
        if (viewModeCombo.getSelectedIndex() == 0) renderUnifiedDiff(preview);
        else renderSideBySideDiff(preview);
    }

    @Override public void removeNotify() {
        if (comparison != null) comparison.cancel(true);
        super.removeNotify();
    }

    private void renderUnifiedDiff(List<DiffCalculator.DiffLine> diffs) {
        unifiedDiffPane.setText("");
        StyledDocument doc = unifiedDiffPane.getStyledDocument();
        boolean isDark = isDarkTheme();

        Style defStyle = unifiedDiffPane.addStyle("def", null);
        StyleConstants.setForeground(defStyle, isDark ? new Color(200, 210, 225) : new Color(40, 50, 70));

        Style addStyle = unifiedDiffPane.addStyle("add", null);
        StyleConstants.setForeground(addStyle, isDark ? new Color(82, 196, 26) : new Color(34, 139, 34));
        StyleConstants.setBackground(addStyle, isDark ? new Color(30, 60, 30, 160) : new Color(230, 255, 230));

        Style delStyle = unifiedDiffPane.addStyle("del", null);
        StyleConstants.setForeground(delStyle, isDark ? new Color(255, 77, 79) : new Color(205, 38, 38));
        StyleConstants.setBackground(delStyle, isDark ? new Color(70, 30, 30, 160) : new Color(255, 230, 230));

        try {
            int lineNo = 1;
            Style currentStyle = null;
            StringBuilder batchText = new StringBuilder();

            for (DiffCalculator.DiffLine line : diffs) {
                Style style = defStyle;
                if (line.type == 1) {
                    style = addStyle;
                } else if (line.type == -1) {
                    style = delStyle;
                }

                // If style changed, flush existing batch
                if (currentStyle != null && style != currentStyle) {
                    doc.insertString(doc.getLength(), batchText.toString(), currentStyle);
                    batchText.setLength(0);
                }
                currentStyle = style;
                batchText.append(String.format("%4d | %s\n", lineNo++, line.text));
            }
            if (batchText.length() > 0 && currentStyle != null) {
                doc.insertString(doc.getLength(), batchText.toString(), currentStyle);
            }
        } catch (Exception ignored) {}
    }

    private void renderSideBySideDiff(List<DiffCalculator.DiffLine> diffs) {
        sideAPane.setText("");
        sideBPane.setText("");
        StyledDocument docA = sideAPane.getStyledDocument();
        StyledDocument docB = sideBPane.getStyledDocument();
        boolean isDark = isDarkTheme();

        Style defStyleA = sideAPane.addStyle("defA", null);
        StyleConstants.setForeground(defStyleA, isDark ? new Color(200, 210, 225) : new Color(40, 50, 70));
        Style delStyleA = sideAPane.addStyle("delA", null);
        StyleConstants.setForeground(delStyleA, isDark ? new Color(255, 77, 79) : new Color(205, 38, 38));
        StyleConstants.setBackground(delStyleA, isDark ? new Color(70, 30, 30, 160) : new Color(255, 230, 230));

        Style defStyleB = sideBPane.addStyle("defB", null);
        StyleConstants.setForeground(defStyleB, isDark ? new Color(200, 210, 225) : new Color(40, 50, 70));
        Style addStyleB = sideBPane.addStyle("addB", null);
        StyleConstants.setForeground(addStyleB, isDark ? new Color(82, 196, 26) : new Color(34, 139, 34));
        StyleConstants.setBackground(addStyleB, isDark ? new Color(30, 60, 30, 160) : new Color(230, 255, 230));

        try {
            int aNo = 1, bNo = 1;
            StringBuilder bufA = new StringBuilder();
            StringBuilder bufB = new StringBuilder();
            Style lastStyleA = null;
            Style lastStyleB = null;

            for (DiffCalculator.DiffLine line : diffs) {
                Style styleA, styleB;
                String textA, textB;

                if (line.type == -1) { // Only in A
                    styleA = delStyleA;
                    textA = String.format("%4d | %s\n", aNo++, line.text);
                    styleB = defStyleB;
                    textB = "     |\n";
                } else if (line.type == 1) { // Only in B
                    styleA = defStyleA;
                    textA = "     |\n";
                    styleB = addStyleB;
                    textB = String.format("%4d | %s\n", bNo++, line.text);
                } else { // Unchanged
                    styleA = defStyleA;
                    textA = String.format("%4d | %s\n", aNo++, line.text);
                    styleB = defStyleB;
                    textB = String.format("%4d | %s\n", bNo++, line.text);
                }

                if (lastStyleA != null && styleA != lastStyleA) {
                    docA.insertString(docA.getLength(), bufA.toString(), lastStyleA);
                    bufA.setLength(0);
                }
                lastStyleA = styleA;
                bufA.append(textA);

                if (lastStyleB != null && styleB != lastStyleB) {
                    docB.insertString(docB.getLength(), bufB.toString(), lastStyleB);
                    bufB.setLength(0);
                }
                lastStyleB = styleB;
                bufB.append(textB);
            }

            if (bufA.length() > 0 && lastStyleA != null) {
                docA.insertString(docA.getLength(), bufA.toString(), lastStyleA);
            }
            if (bufB.length() > 0 && lastStyleB != null) {
                docB.insertString(docB.getLength(), bufB.toString(), lastStyleB);
            }
        } catch (Exception ignored) {}
    }

    private boolean isDarkTheme() {
        Color bg = getBackground();
        if (bg == null) bg = UIManager.getColor("Panel.background");
        if (bg == null) return false;
        double lum = 0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue();
        return lum < 128;
    }
}
