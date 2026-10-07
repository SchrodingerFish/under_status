package com.cn.schrodinger.understatus;

import com.cn.schrodinger.understatus.toolbox.core.JsonFormatter;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.UIManager;

/**
 * JSON prettify, minify, escape, and unescape formatting tab panel.
 * Features inline non-blocking status feedback and clipboard shortcuts.
 *
 * @author peter/antigravity
 */
public class JsonTabPanel extends JPanel {

    private JTextArea jsonTextArea;
    private JLabel statusLabel;

    public JsonTabPanel() {
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        jsonTextArea = new JTextArea();
        jsonTextArea.setFont(UIManager.getFont("TextArea.font").deriveFont(11f));
        jsonTextArea.setLineWrap(true);
        jsonTextArea.setWrapStyleWord(true);
        add(new JScrollPane(jsonTextArea), BorderLayout.CENTER);

        JPanel southPanel = new JPanel(new BorderLayout(4, 4));

        // Control Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton prettifyBtn = new JButton("美化 JSON");
        JButton minifyBtn = new JButton("压缩 JSON");
        JButton escapeBtn = new JButton("转义 (Escape)");
        JButton unescapeBtn = new JButton("反转义 (Unescape)");
        JButton pasteBtn = new JButton("📋 粘贴");
        JButton copyBtn = new JButton("复制");
        JButton clearBtn = new JButton("清空");

        prettifyBtn.addActionListener(e -> triggerFormatting(1));
        minifyBtn.addActionListener(e -> triggerFormatting(2));
        escapeBtn.addActionListener(e -> triggerFormatting(3));
        unescapeBtn.addActionListener(e -> triggerFormatting(4));

        pasteBtn.addActionListener(e -> {
            String clip = CommonUtils.getClipboardText();
            if (clip != null && !clip.isBlank()) {
                jsonTextArea.setText(clip);
                statusLabel.setText("已粘贴剪贴板内容");
                statusLabel.setForeground(new Color(40, 160, 80));
            }
        });

        copyBtn.addActionListener(e -> CommonUtils.copyToClipboard(jsonTextArea.getText()));
        clearBtn.addActionListener(e -> {
            jsonTextArea.setText("");
            statusLabel.setText(" ");
        });

        buttonPanel.add(prettifyBtn);
        buttonPanel.add(minifyBtn);
        buttonPanel.add(escapeBtn);
        buttonPanel.add(unescapeBtn);
        buttonPanel.add(pasteBtn);
        buttonPanel.add(copyBtn);
        buttonPanel.add(clearBtn);

        southPanel.add(buttonPanel, BorderLayout.CENTER);

        statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(11f));
        southPanel.add(statusLabel, BorderLayout.SOUTH);

        add(southPanel, BorderLayout.SOUTH);
    }

    private void triggerFormatting(int mode) {
        String input = jsonTextArea.getText().trim();
        if (input.isEmpty()) {
            statusLabel.setText("请输入或粘贴 JSON 内容");
            statusLabel.setForeground(new Color(220, 50, 50));
            return;
        }
        try {
            switch (mode) {
                case 1 -> {
                    jsonTextArea.setText(JsonFormatter.format(input));
                    statusLabel.setText("已完成美化格式化");
                    statusLabel.setForeground(new Color(40, 160, 80));
                }
                case 2 -> {
                    jsonTextArea.setText(JsonFormatter.minify(input));
                    statusLabel.setText("已完成压缩压缩行");
                    statusLabel.setForeground(new Color(40, 160, 80));
                }
                case 3 -> {
                    jsonTextArea.setText(JsonFormatter.escape(input));
                    statusLabel.setText("已转义为 Java 字符串文本");
                    statusLabel.setForeground(new Color(40, 160, 80));
                }
                case 4 -> {
                    jsonTextArea.setText(JsonFormatter.unescape(input));
                    statusLabel.setText("已去除转义字符");
                    statusLabel.setForeground(new Color(40, 160, 80));
                }
            }
        } catch (Exception ex) {
            statusLabel.setText("处理失败: " + ex.getMessage());
            statusLabel.setForeground(new Color(220, 50, 50));
        }
    }
}
