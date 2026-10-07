package com.cn.schrodinger.understatus;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;

/**
 * Enhanced Real-time Regular Expression Tester panel.
 * Features common regex preset templates, capture group extraction explorer,
 * real-time regex replace preview, and case-insensitive/multiline flag toggles.
 *
 * @author peter/antigravity
 */
public class RegexTabPanel extends JPanel {

    private JTextField regexField;
    private JComboBox<PresetItem> presetComboBox;
    private JCheckBox caseInsensitiveCheck;
    private JCheckBox multilineCheck;
    private JCheckBox replaceCheck;

    private JTextField replaceField;
    private JPanel replaceBar;

    private JTextArea testTextArea;
    private JTextArea resultArea; // Replaced text or groups breakdown
    private JSplitPane centerSplit;

    private JLabel statusLabel;
    private final Highlighter.HighlightPainter highlightPainter;

    private static final class PresetItem {
        final String label;
        final String regex;
        final String sample;

        PresetItem(String label, String regex, String sample) {
            this.label = label;
            this.regex = regex;
            this.sample = sample;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final javax.swing.Timer regexDebounceTimer;

    public RegexTabPanel() {
        this.regexDebounceTimer = new javax.swing.Timer(120, e -> runRegex());
        this.regexDebounceTimer.setRepeats(false);
        this.highlightPainter = new DefaultHighlighter.DefaultHighlightPainter(new Color(255, 230, 130));
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // Header Panel: Preset Combobox + Regex Input + Options
        JPanel topContainer = new JPanel(new BorderLayout(4, 4));

        // Top Row: Presets & Flags
        JPanel flagsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));

        PresetItem[] presets = new PresetItem[]{
            new PresetItem("常用正则模板 (选择自动填入)...", "", ""),
            new PresetItem("📱 手机号码", "1[3-9]\\d{9}", "联系电话：13800138000，备用：18912345678"),
            new PresetItem("📧 电子邮箱", "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}", "客服邮箱：support@service.com，技术：tech@corp.cn"),
            new PresetItem("🌐 IPv4 地址", "(?:[0-9]{1,3}\\.){3}[0-9]{1,3}", "本地服务器：192.168.1.1，网关：10.0.0.254"),
            new PresetItem("🪪 身份证号 (18位)", "[1-9]\\d{5}(?:18|19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]", "证件号：110101199003072345"),
            new PresetItem("🔗 网址 URL", "https?://[\\w\\-]+(\\.[\\w\\-]+)+[/#?]?.*", "访问官网：https://github.com/schrodingerfish/under_status 查看源码"),
            new PresetItem("📅 日期 YYYY-MM-DD", "\\d{4}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\\d|3[01])", "发布日期：2026-10-07，更新于 2026-12-31"),
            new PresetItem("🔢 纯整数/小数", "-?\\d+(?:\\.\\d+)?", "商品价格为 99.8 元，折扣 -15.5 元，总计 84.3 元"),
            new PresetItem("🔤 纯中文字符", "[\\u4e00-\\u9fa5]+", "Hello 世界！欢迎使用 UnderStatus 插件！")
        };

        presetComboBox = new JComboBox<>(presets);
        presetComboBox.setFont(new Font("SansSerif", Font.PLAIN, 11));
        presetComboBox.addActionListener(e -> {
            PresetItem sel = (PresetItem) presetComboBox.getSelectedItem();
            if (sel != null && !sel.regex.isEmpty()) {
                regexField.setText(sel.regex);
                if (!sel.sample.isEmpty()) {
                    testTextArea.setText(sel.sample);
                }
            }
        });
        flagsPanel.add(presetComboBox);

        caseInsensitiveCheck = new JCheckBox("忽略大小写 (?i)");
        caseInsensitiveCheck.setFont(new Font("SansSerif", Font.PLAIN, 11));
        caseInsensitiveCheck.addActionListener(e -> runRegex());
        flagsPanel.add(caseInsensitiveCheck);

        multilineCheck = new JCheckBox("多行模式 (?m)");
        multilineCheck.setFont(new Font("SansSerif", Font.PLAIN, 11));
        multilineCheck.addActionListener(e -> runRegex());
        flagsPanel.add(multilineCheck);

        replaceCheck = new JCheckBox("启用替换 (Replace)");
        replaceCheck.setFont(new Font("SansSerif", Font.PLAIN, 11));
        replaceCheck.addActionListener(e -> toggleReplaceMode());
        flagsPanel.add(replaceCheck);

        topContainer.add(flagsPanel, BorderLayout.NORTH);

        // Regex Input Row
        JPanel regexInputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 4, 2, 4);

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0;
        regexInputPanel.add(new JLabel("正则表达式 (Regex):"), gbc);

        regexField = new JTextField("(\\d+)");
        regexField.setFont(new Font("Monospaced", Font.PLAIN, 13));
        regexField.getDocument().addDocumentListener(createDocListener());
        gbc.gridx = 1; gbc.weightx = 1.0;
        regexInputPanel.add(regexField, gbc);

        // Replace Input Row (Collapsible)
        replaceBar = new JPanel(new GridBagLayout());
        GridBagConstraints rgbc = new GridBagConstraints();
        rgbc.fill = GridBagConstraints.HORIZONTAL;
        rgbc.insets = new Insets(2, 4, 2, 4);

        rgbc.gridx = 0; rgbc.gridy = 0; rgbc.weightx = 0.0;
        replaceBar.add(new JLabel("替换为 (Replace With):"), rgbc);

        replaceField = new JTextField("");
        replaceField.setFont(new Font("Monospaced", Font.PLAIN, 13));
        replaceField.getDocument().addDocumentListener(createDocListener());
        rgbc.gridx = 1; rgbc.weightx = 1.0;
        replaceBar.add(replaceField, rgbc);
        replaceBar.setVisible(false);

        JPanel inputStack = new JPanel(new BorderLayout());
        inputStack.add(regexInputPanel, BorderLayout.NORTH);
        inputStack.add(replaceBar, BorderLayout.SOUTH);
        topContainer.add(inputStack, BorderLayout.SOUTH);

        add(topContainer, BorderLayout.NORTH);

        // Center Split: Top Test Text Area, Bottom Group/Replace Result Area
        testTextArea = new JTextArea("在此处输入测试文本。例如包含数字 123 或者是 4567 的段落。");
        testTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        testTextArea.setLineWrap(true);
        testTextArea.setWrapStyleWord(true);
        testTextArea.getDocument().addDocumentListener(createDocListener());

        resultArea = new JTextArea();
        resultArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        resultArea.setLineWrap(true);
        resultArea.setWrapStyleWord(true);
        resultArea.setEditable(false);

        JPanel topTextPanel = new JPanel(new BorderLayout());
        topTextPanel.setBorder(BorderFactory.createTitledBorder("测试源文本 (Source Text)"));
        topTextPanel.add(new JScrollPane(testTextArea), BorderLayout.CENTER);

        JPanel bottomResultPanel = new JPanel(new BorderLayout());
        bottomResultPanel.setBorder(BorderFactory.createTitledBorder("匹配捕获组 / 替换结果 (Matches & Groups / Result)"));
        bottomResultPanel.add(new JScrollPane(resultArea), BorderLayout.CENTER);

        JButton copyResultBtn = new JButton("复制结果");
        copyResultBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        copyResultBtn.addActionListener(e -> CommonUtils.copyToClipboard(resultArea.getText()));
        JPanel resultActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        resultActions.add(copyResultBtn);
        bottomResultPanel.add(resultActions, BorderLayout.SOUTH);

        centerSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topTextPanel, bottomResultPanel);
        centerSplit.setResizeWeight(0.5);
        centerSplit.setDividerLocation(130);
        add(centerSplit, BorderLayout.CENTER);

        // Footer status info
        JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        statusLabel = new JLabel("准备就绪");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        footerPanel.add(statusLabel);
        add(footerPanel, BorderLayout.SOUTH);

        // Initial match
        runRegex();
    }

    private DocumentListener createDocListener() {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { scheduleRegex(); }
            @Override
            public void removeUpdate(DocumentEvent e) { scheduleRegex(); }
            @Override
            public void changedUpdate(DocumentEvent e) { scheduleRegex(); }
        };
    }

    private void toggleReplaceMode() {
        replaceBar.setVisible(replaceCheck.isSelected());
        revalidate();
        repaint();
        runRegex();
    }

    private javax.swing.SwingWorker<com.cn.schrodinger.understatus.toolbox.core.RegexEvaluator.Result, Void> regexWorker;

    private void runRegex() {
        if (regexWorker != null) regexWorker.cancel(true);
        testTextArea.getHighlighter().removeAllHighlights();
        String regex = regexField.getText();
        String text = testTextArea.getText();
        if (regex.isEmpty()) {
            statusLabel.setText("请输入正则表达式");
            resultArea.setText("");
            return;
        }
        int flags = (caseInsensitiveCheck.isSelected() ? Pattern.CASE_INSENSITIVE : 0)
                | (multilineCheck.isSelected() ? Pattern.MULTILINE : 0);
        String replacement = replaceCheck.isSelected() ? replaceField.getText() : null;
        statusLabel.setText("正在匹配…");
        regexWorker = new javax.swing.SwingWorker<>() {
            @Override protected com.cn.schrodinger.understatus.toolbox.core.RegexEvaluator.Result doInBackground() {
                return com.cn.schrodinger.understatus.toolbox.core.RegexEvaluator.evaluate(regex, text, flags, replacement);
            }
            @Override protected void done() {
                if (isCancelled() || regexWorker != this) return;
                try {
                    var result = get();
                    Highlighter highlighter = testTextArea.getHighlighter();
                    highlighter.removeAllHighlights();
                    for (var match : result.matches()) highlighter.addHighlight(match.start(), match.end(), highlightPainter);
                    resultArea.setText(result.output());
                    statusLabel.setText("匹配: " + result.matches().size() + (result.truncated() ? "（已截断）" : ""));
                    statusLabel.setForeground(UIManager.getColor("Label.foreground"));
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    statusLabel.setText("匹配失败: " + cause.getMessage());
                    statusLabel.setForeground(Color.RED);
                    resultArea.setText("");
                }
            }
        };
        regexWorker.execute();
    }

    private void scheduleRegex() {
        if (regexWorker != null) regexWorker.cancel(true);
        regexDebounceTimer.restart();
    }

    @Override public void removeNotify() {
        regexDebounceTimer.stop();
        if (regexWorker != null) regexWorker.cancel(true);
        super.removeNotify();
    }
}
