package com.cn.schrodinger.understatus;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
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

    private JTextArea textInputArea;
    private JTextArea textOutputArea;
    private JTextField findField;
    private JTextField replaceField;
    private JLabel metricsLabel;
    private final javax.swing.Timer metricsDebounceTimer = new javax.swing.Timer(150, e -> updateMetrics());

    private static final Pattern CAMEL_SPLIT_1 = Pattern.compile("([a-z0-9])([A-Z])");
    private static final Pattern CAMEL_SPLIT_2 = Pattern.compile("([A-Z]+)([A-Z][a-z0-9])");
    private static final Pattern WORD_SPLIT = Pattern.compile("[_\\-\\s]+");
    private static final Pattern LINE_SPLIT = Pattern.compile("\r\n|\r|\n");

    public TextTabPanel() {
        metricsDebounceTimer.setRepeats(false);
        initComponents();
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
        snakeBtn.addActionListener(e -> textOutputArea.setText(toSnakeCase(textInputArea.getText())));

        JButton camelBtn = new JButton("➔ camelCase");
        camelBtn.setToolTipText("下划线转小驼峰 (user_profile ➔ userProfile)");
        camelBtn.addActionListener(e -> textOutputArea.setText(toCamelCase(textInputArea.getText())));

        JButton pascalBtn = new JButton("➔ PascalCase");
        pascalBtn.setToolTipText("转大驼峰 (user_profile ➔ UserProfile)");
        pascalBtn.addActionListener(e -> textOutputArea.setText(toPascalCase(textInputArea.getText())));

        JButton kebabBtn = new JButton("➔ kebab-case");
        kebabBtn.setToolTipText("转短横线连字符 (userProfile ➔ user-profile)");
        kebabBtn.addActionListener(e -> textOutputArea.setText(toKebabCase(textInputArea.getText())));

        JButton upperBtn = new JButton("➔ 大写 (UPPER)");
        upperBtn.addActionListener(e -> textOutputArea.setText(textInputArea.getText().toUpperCase()));

        JButton lowerBtn = new JButton("➔ 小写 (lower)");
        lowerBtn.addActionListener(e -> textOutputArea.setText(textInputArea.getText().toLowerCase()));

        JButton trimBtn = new JButton("去除行首尾空格");
        trimBtn.addActionListener(e -> textOutputArea.setText(trimLines(textInputArea.getText())));

        JButton dedupBtn = new JButton("文本行去重");
        dedupBtn.addActionListener(e -> textOutputArea.setText(deduplicateLines(textInputArea.getText())));

        JButton sortBtn = new JButton("文本行 A-Z 排序");
        sortBtn.addActionListener(e -> textOutputArea.setText(sortLines(textInputArea.getText())));

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
        southContainer.add(metricsLabel, BorderLayout.SOUTH);

        add(southContainer, BorderLayout.SOUTH);
        updateMetrics();
    }

    private void updateMetrics() {
        String text = textInputArea.getText();
        int charCount = text.length();
        int lineCount = text.isEmpty() ? 0 : 1;
        int wordCount = 0;
        boolean inWord = false;
        for (int i = 0; i < charCount; i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                lineCount++;
            }
            if (Character.isWhitespace(c)) {
                inWord = false;
            } else if (!inWord) {
                inWord = true;
                wordCount++;
            }
        }
        metricsLabel.setText(String.format("📊 输入统计 · 行数: %d | 字符数: %d | 单词数: %d", lineCount, charCount, wordCount));
    }

    private void triggerReplace() {
        String input = textInputArea.getText();
        String find = findField.getText();
        String replace = replaceField.getText();
        if (find.isEmpty()) {
            textOutputArea.setText(input);
            return;
        }
        textOutputArea.setText(input.replace(find, replace));
    }

    private static String toSnakeCase(String input) {
        if (input == null || input.isEmpty()) return "";
        String[] lines = LINE_SPLIT.split(input, -1);
        StringBuilder sb = new StringBuilder(input.length() + 32);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) sb.append('\n');
            String s = CAMEL_SPLIT_1.matcher(lines[i]).replaceAll("$1_$2");
            s = CAMEL_SPLIT_2.matcher(s).replaceAll("$1_$2");
            s = s.replace('-', '_');
            sb.append(s.toLowerCase());
        }
        return sb.toString();
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
