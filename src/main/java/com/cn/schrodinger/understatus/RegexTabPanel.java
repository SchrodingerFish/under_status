package com.cn.schrodinger.understatus;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;

/**
 * Real-time Regular Expression Tester tab panel.
 * Highlights matching segments using custom highlighter painter.
 *
 * @author peter/antigravity
 */
public class RegexTabPanel extends JPanel {

    private JTextField regexField;
    private JTextArea testTextArea;
    private JLabel statusLabel;
    private final Highlighter.HighlightPainter highlightPainter;

    public RegexTabPanel() {
        // Soft yellow highlighting
        this.highlightPainter = new DefaultHighlighter.DefaultHighlightPainter(new Color(255, 235, 156));
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // Regex input header
        JPanel headerPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 4, 4, 4);

        gbc.gridx = 0; gbc.gridy = 0;
        headerPanel.add(new JLabel("正则表达式 (Regex):"), gbc);

        regexField = new JTextField("(\\d+)");
        regexField.setFont(regexField.getFont().deriveFont(12f));
        regexField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { runRegex(); }
            @Override
            public void removeUpdate(DocumentEvent e) { runRegex(); }
            @Override
            public void changedUpdate(DocumentEvent e) { runRegex(); }
        });
        gbc.gridx = 1; gbc.weightx = 1.0;
        headerPanel.add(regexField, gbc);

        add(headerPanel, BorderLayout.NORTH);

        // Test Text Area
        testTextArea = new JTextArea("在此处输入需要匹配的文本。例如包含数字 123 或者是 4567 的段落。");
        testTextArea.setFont(UIManager.getFont("TextArea.font").deriveFont(11f));
        testTextArea.setLineWrap(true);
        testTextArea.setWrapStyleWord(true);
        testTextArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { runRegex(); }
            @Override
            public void removeUpdate(DocumentEvent e) { runRegex(); }
            @Override
            public void changedUpdate(DocumentEvent e) { runRegex(); }
        });
        
        add(new JScrollPane(testTextArea), BorderLayout.CENTER);

        // Footer status info
        JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        statusLabel = new JLabel("准备就绪");
        statusLabel.setFont(statusLabel.getFont().deriveFont(11f));
        footerPanel.add(statusLabel);

        add(footerPanel, BorderLayout.SOUTH);

        // Run initial match
        runRegex();
    }

    private void runRegex() {
        Highlighter highlighter = testTextArea.getHighlighter();
        highlighter.removeAllHighlights();

        String regex = regexField.getText().trim();
        if (regex.isEmpty()) {
            statusLabel.setText("请输入正则表达式");
            statusLabel.setForeground(UIManager.getColor("Label.foreground"));
            return;
        }

        try {
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(testTextArea.getText());
            int matches = 0;
            while (matcher.find()) {
                highlighter.addHighlight(matcher.start(), matcher.end(), highlightPainter);
                matches++;
            }
            statusLabel.setText(String.format("匹配段数: %d 段 (Matches: %d)", matches, matches));
            statusLabel.setForeground(UIManager.getColor("Label.foreground"));
        } catch (java.util.regex.PatternSyntaxException ex) {
            statusLabel.setText("正则语法错误 (Regex Syntax Error)");
            statusLabel.setForeground(Color.RED);
        } catch (Exception ex) {
            statusLabel.setText("匹配失败: " + ex.getMessage());
            statusLabel.setForeground(Color.RED);
        }
    }
}
