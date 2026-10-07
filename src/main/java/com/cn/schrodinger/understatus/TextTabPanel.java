package com.cn.schrodinger.understatus;

import com.cn.schrodinger.understatus.toolbox.core.ToolTask;
import com.cn.schrodinger.understatus.toolbox.core.ToolLimits;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Modern developer text utility panel.
 * Features Case Conversions (camelCase, snake_case, kebab-case, PascalCase, UPPER, lower),
 * Text line cleaning (Trim, Remove empty lines, Deduplicate, Sort A-Z),
 * Find & Replace, and live text metrics (lines, characters, words).
 *
 * @author peter/antigravity
 */
public class TextTabPanel extends JPanel {

    private final ToolTask tasks = new ToolTask(this);
    private final ToolTask metricTasks = new ToolTask(this);
    private final JLabel operationStatus = new JLabel(" ");
    private JTextArea textInputArea;
    private JTextArea textOutputArea;
    private JTextField findField;
    private JTextField replaceField;
    private JLabel metricsLabel;
    private final javax.swing.Timer metricsDebounceTimer = new javax.swing.Timer(150, e -> updateMetrics());

    private static final Pattern WORD_SPLIT = Pattern.compile("[_\\-\\s]+");
    private static final Pattern LINE_SPLIT = Pattern.compile("\r\n|\r|\n");

    public TextTabPanel() {
        metricsDebounceTimer.setRepeats(false);
        initComponents();
        tasks.watch(textInputArea);
        tasks.watch(findField);
        tasks.watch(replaceField);
        metricTasks.watch(textInputArea);
        metricTasks.onShow(this::updateMetrics);
    }

    private void initComponents() {
        setLayout(new BorderLayout(6, 6));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // Center: Input & Output Split Pane
        textInputArea = new JTextArea("输入需要转换或清洗的文本 (Input text here)");
        textInputArea.setFont(UIManager.getFont("TextArea.font").deriveFont(11f));
        textInputArea.setLineWrap(true);
        textInputArea.setWrapStyleWord(true);
        textInputArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { metricsDebounceTimer.restart(); }
            @Override public void removeUpdate(DocumentEvent e) { metricsDebounceTimer.restart(); }
            @Override public void changedUpdate(DocumentEvent e) { metricsDebounceTimer.restart(); }
        });

        textOutputArea = new JTextArea();
        textOutputArea.setEditable(false);
        textOutputArea.setFont(UIManager.getFont("TextArea.font").deriveFont(11f));
        textOutputArea.setLineWrap(true);
        textOutputArea.setWrapStyleWord(true);

        JScrollPane inputScroll = new JScrollPane(textInputArea);
        inputScroll.setBorder(BorderFactory.createTitledBorder("输入文本 (Input)"));

        JScrollPane outputScroll = new JScrollPane(textOutputArea);
        outputScroll.setBorder(BorderFactory.createTitledBorder("转换结果 (Output)"));

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, inputScroll, outputScroll);
        splitPane.setDividerLocation(130);
        add(splitPane, BorderLayout.CENTER);

        // South: Control Center
        JPanel southContainer = new JPanel(new BorderLayout(4, 4));

        // Row 1: Case conversions & cleaners toolbar
        JPanel toolsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));

        JButton snakeBtn = new JButton("➔ snake_case");
        snakeBtn.setToolTipText("驼峰转下划线 (userProfile ➔ user_profile)");
        snakeBtn.addActionListener(e -> tasks.transform(textInputArea, textOutputArea, operationStatus, TextTabPanel::toSnakeCase));

        JButton camelBtn = new JButton("➔ camelCase");
        camelBtn.setToolTipText("下划线转小驼峰 (user_profile ➔ userProfile)");
        camelBtn.addActionListener(e -> tasks.transform(textInputArea, textOutputArea, operationStatus, TextTabPanel::toCamelCase));

        JButton pascalBtn = new JButton("➔ PascalCase");
        pascalBtn.setToolTipText("转大驼峰 (user_profile ➔ UserProfile)");
        pascalBtn.addActionListener(e -> tasks.transform(textInputArea, textOutputArea, operationStatus, TextTabPanel::toPascalCase));

        JButton kebabBtn = new JButton("➔ kebab-case");
        kebabBtn.setToolTipText("转短横线连字符 (userProfile ➔ user-profile)");
        kebabBtn.addActionListener(e -> tasks.transform(textInputArea, textOutputArea, operationStatus, TextTabPanel::toKebabCase));

        JButton upperBtn = new JButton("➔ 大写 (UPPER)");
        upperBtn.addActionListener(e -> tasks.transform(textInputArea, textOutputArea, operationStatus, value -> value.toUpperCase(java.util.Locale.ROOT)));

        JButton lowerBtn = new JButton("➔ 小写 (lower)");
        lowerBtn.addActionListener(e -> tasks.transform(textInputArea, textOutputArea, operationStatus, value -> value.toLowerCase(java.util.Locale.ROOT)));

        JButton trimBtn = new JButton("去除行首尾空格");
        trimBtn.addActionListener(e -> tasks.transform(textInputArea, textOutputArea, operationStatus, TextTabPanel::trimLines));

        JButton dedupBtn = new JButton("文本行去重");
        dedupBtn.addActionListener(e -> tasks.transform(textInputArea, textOutputArea, operationStatus, TextTabPanel::deduplicateLines));

        JButton sortBtn = new JButton("文本行 A-Z 排序");
        sortBtn.addActionListener(e -> tasks.transform(textInputArea, textOutputArea, operationStatus, TextTabPanel::sortLines));

        toolsPanel.add(snakeBtn);
        toolsPanel.add(camelBtn);
        toolsPanel.add(pascalBtn);
        toolsPanel.add(kebabBtn);
        toolsPanel.add(upperBtn);
        toolsPanel.add(lowerBtn);
        toolsPanel.add(trimBtn);
        toolsPanel.add(dedupBtn);
        toolsPanel.add(sortBtn);

        southContainer.add(toolsPanel, BorderLayout.NORTH);

        // Row 2: Find & Replace + Global actions
        JPanel bottomActionPanel = new JPanel(new BorderLayout(6, 0));

        JPanel findReplacePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        findReplacePanel.add(new JLabel("查找:"));
        findField = new JTextField(7);
        findReplacePanel.add(findField);

        findReplacePanel.add(new JLabel("替换为:"));
        replaceField = new JTextField(7);
        findReplacePanel.add(replaceField);

        JButton replaceBtn = new JButton("执行替换");
        replaceBtn.addActionListener(e -> triggerReplace());
        findReplacePanel.add(replaceBtn);

        bottomActionPanel.add(findReplacePanel, BorderLayout.WEST);

        JPanel rightBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        JButton pasteBtn = new JButton("📋 粘贴");
        pasteBtn.addActionListener(e -> {
            String clip = CommonUtils.getClipboardText();
            if (clip != null && !clip.isBlank()) {
                textInputArea.setText(clip);
            }
        });

        JButton useOutputBtn = new JButton("🔄 结果设为输入");
        useOutputBtn.addActionListener(e -> {
            String out = textOutputArea.getText();
            if (!out.isEmpty()) {
                textInputArea.setText(out);
            }
        });

        JButton copyBtn = new JButton("复制输出");
        copyBtn.addActionListener(e -> CommonUtils.copyToClipboard(textOutputArea.getText()));

        JButton clearBtn = new JButton("清空");
        clearBtn.addActionListener(e -> {
            textInputArea.setText("");
            textOutputArea.setText("");
        });

        rightBtns.add(pasteBtn);
        rightBtns.add(useOutputBtn);
        rightBtns.add(copyBtn);
        rightBtns.add(clearBtn);

        bottomActionPanel.add(rightBtns, BorderLayout.EAST);
        southContainer.add(bottomActionPanel, BorderLayout.CENTER);

        // Row 3: Metrics bar
        metricsLabel = new JLabel("📊 行数: 1 | 字符数: 0 | 单词数: 0");
        metricsLabel.setFont(metricsLabel.getFont().deriveFont(11f));
        metricsLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        JPanel footer = new JPanel(new GridLayout(2, 1));
        footer.add(metricsLabel);
        footer.add(operationStatus);
        southContainer.add(footer, BorderLayout.SOUTH);

        add(southContainer, BorderLayout.SOUTH);
        updateMetrics();
    }

    @Override public void removeNotify() {
        metricsDebounceTimer.stop();
        tasks.cancel();
        metricTasks.cancel();
        super.removeNotify();
    }

    private void updateMetrics() {
        try {
            String text = ToolTask.snapshot(textInputArea);
            metricTasks.submit(() -> {
                int lines = text.isEmpty() ? 0 : 1, words = 0;
                boolean inWord = false;
                for (int i = 0; i < text.length(); i++) {
                    char ch = text.charAt(i);
                    if (ch == '\n') lines++;
                    if (Character.isWhitespace(ch)) inWord = false;
                    else if (!inWord) { inWord = true; words++; }
                }
                return "行数: " + lines + " | 字符数: " + text.length() + " | 单词数: " + words;
            }, metricsLabel::setText, metricsLabel::setText);
        } catch (IllegalArgumentException ex) { metricsLabel.setText(ex.getMessage()); }
    }

    private void triggerReplace() {
        try {
            String find = ToolTask.snapshot(findField);
            String replacement = ToolTask.snapshot(replaceField);
            tasks.transform(textInputArea, textOutputArea, operationStatus, input -> {
                if (find.isEmpty()) return input;
                StringBuilder out = new StringBuilder();
                int offset = 0, match;
                while ((match = input.indexOf(find, offset)) >= 0) {
                    ToolLimits.checkInterrupted();
                    if ((long) out.length() + match - offset + replacement.length() > ToolLimits.MAX_OUTPUT) {
                        throw new IllegalArgumentException("替换结果超过 500,000 字符限制");
                    }
                    out.append(input, offset, match).append(replacement);
                    offset = match + find.length();
                }
                if ((long) out.length() + input.length() - offset > ToolLimits.MAX_OUTPUT) throw new IllegalArgumentException("替换结果超过限制");
                return out.append(input, offset, input.length()).toString();
            });
        } catch (IllegalArgumentException ex) { tasks.cancel(); operationStatus.setText(ex.getMessage()); }
    }

    private static String toSnakeCase(String input) {
        if (input == null) return "";
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (i > 0 && ch >= 'A' && ch <= 'Z') {
                char previous = input.charAt(i - 1);
                boolean lowerBefore = previous >= 'a' && previous <= 'z' || previous >= '0' && previous <= '9';
                boolean capitalBefore = previous >= 'A' && previous <= 'Z';
                boolean lowerAfter = i + 1 < input.length() && (input.charAt(i + 1) >= 'a' && input.charAt(i + 1) <= 'z'
                        || input.charAt(i + 1) >= '0' && input.charAt(i + 1) <= '9');
                if (lowerBefore || capitalBefore && lowerAfter) result.append('_');
            }
            result.append(ch == '-' ? '_' : ch);
        }
        return result.toString().toLowerCase(java.util.Locale.ROOT);
    }

    private static String toCamelCase(String input) {
        if (input == null || input.isEmpty()) return "";
        String[] lines = LINE_SPLIT.split(input, -1);
        StringBuilder sb = new StringBuilder(input.length() + 32);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) sb.append('\n');
            sb.append(convertLineToCamel(lines[i]));
        }
        return sb.toString();
    }

    private static String convertLineToCamel(String line) {
        String[] parts = WORD_SPLIT.split(line);
        if (parts.length == 0) return "";
        StringBuilder sb = new StringBuilder(line.length());
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.isEmpty()) {
                sb.append(p.toLowerCase());
            } else {
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    private static String toPascalCase(String input) {
        if (input == null || input.isEmpty()) return "";
        String[] lines = LINE_SPLIT.split(input, -1);
        StringBuilder sb = new StringBuilder(input.length() + 32);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) sb.append('\n');
            String camel = convertLineToCamel(lines[i]);
            if (!camel.isEmpty()) {
                sb.append(Character.toUpperCase(camel.charAt(0))).append(camel.substring(1));
            }
        }
        return sb.toString();
    }

    private static String toKebabCase(String input) {
        if (input == null) return "";
        return toSnakeCase(input).replace('_', '-');
    }

    private static String trimLines(String input) {
        if (input == null || input.isEmpty()) return "";
        String[] lines = LINE_SPLIT.split(input, -1);
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines[i].trim());
        }
        return sb.toString();
    }

    private static String deduplicateLines(String input) {
        if (input == null || input.isEmpty()) return "";
        String[] lines = LINE_SPLIT.split(input);
        LinkedHashSet<String> set = new LinkedHashSet<>(Arrays.asList(lines));
        return String.join("\n", set);
    }

    private static String sortLines(String input) {
        if (input == null || input.isEmpty()) return "";
        String[] lines = LINE_SPLIT.split(input);
        Arrays.sort(lines);
        return String.join("\n", lines);
    }
}
