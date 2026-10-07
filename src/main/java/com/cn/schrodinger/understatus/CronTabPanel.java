package com.cn.schrodinger.understatus;

import com.cn.schrodinger.understatus.toolbox.core.ToolTask;
import com.cn.schrodinger.understatus.toolbox.core.CronExplainer;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Cron Expression Explainer UI tab panel.
 * Performs real-time translation of schedule strings.
 *
 * @author peter/antigravity
 */
public class CronTabPanel extends JPanel {
    private final ToolTask tasks = new ToolTask(this);

    private JTextField cronField;
    private JTextArea outputArea;

    public CronTabPanel() {
        initComponents();
        tasks.watch(cronField);
        tasks.onShow(this::runCronExplain);
        runCronExplain();
    }

    private void initComponents() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // North: Input Header
        JPanel headerPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 4, 4, 4);

        gbc.gridx = 0; gbc.gridy = 0;
        headerPanel.add(new JLabel("Cron 表达式:"), gbc);

        cronField = new JTextField("0 */5 12-18 * * ?");
        cronField.setFont(cronField.getFont().deriveFont(12f));
        cronField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { runCronExplain(); }
            @Override
            public void removeUpdate(DocumentEvent e) { runCronExplain(); }
            @Override
            public void changedUpdate(DocumentEvent e) { runCronExplain(); }
        });
        gbc.gridx = 1; gbc.weightx = 1.0;
        headerPanel.add(cronField, gbc);

        // Presets selector
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0;
        headerPanel.add(new JLabel("常用预设:"), gbc);

        String[] presets = {
            "选择常用预设...",
            "每 5 分钟 (0 */5 * * * ?)",
            "每 10 分钟 (0 */10 * * * ?)",
            "每 30 分钟 (0 */30 * * * ?)",
            "每小时整点 (0 0 * * * ?)",
            "每天上午 9 点 (0 0 9 * * ?)",
            "每天中午 12 点 (0 0 12 * * ?)",
            "每个工作日上午 9 点 (0 0 9 ? * MON-FRI)",
            "每周一上午 10 点 (0 0 10 ? * MON)",
            "每月 1 号凌晨 0 点 (0 0 0 1 * ?)"
        };
        javax.swing.JComboBox<String> presetCombo = new javax.swing.JComboBox<>(presets);
        presetCombo.addActionListener(e -> {
            int idx = presetCombo.getSelectedIndex();
            if (idx == 1) cronField.setText("0 */5 * * * ?");
            else if (idx == 2) cronField.setText("0 */10 * * * ?");
            else if (idx == 3) cronField.setText("0 */30 * * * ?");
            else if (idx == 4) cronField.setText("0 0 * * * ?");
            else if (idx == 5) cronField.setText("0 0 9 * * ?");
            else if (idx == 6) cronField.setText("0 0 12 * * ?");
            else if (idx == 7) cronField.setText("0 0 9 ? * MON-FRI");
            else if (idx == 8) cronField.setText("0 0 10 ? * MON");
            else if (idx == 9) cronField.setText("0 0 0 1 * ?");
        });
        gbc.gridx = 1; gbc.weightx = 1.0;
        headerPanel.add(presetCombo, gbc);

        add(headerPanel, BorderLayout.NORTH);

        // Center: Output Results
        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setFont(UIManager.getFont("TextArea.font").deriveFont(11f));
        add(new JScrollPane(outputArea), BorderLayout.CENTER);

        // South: Control Bar
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton pasteBtn = new JButton("📋 粘贴");
        JButton clearBtn = new JButton("清空");
        JButton copyBtn = new JButton("复制解析");

        pasteBtn.addActionListener(e -> {
            String clip = CommonUtils.getClipboardText();
            if (clip != null && !clip.isBlank()) {
                cronField.setText(clip.trim());
            }
        });
        clearBtn.addActionListener(e -> {
            cronField.setText("");
            outputArea.setText("");
        });
        copyBtn.addActionListener(e -> CommonUtils.copyToClipboard(outputArea.getText()));

        buttonPanel.add(pasteBtn);
        buttonPanel.add(clearBtn);
        buttonPanel.add(copyBtn);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void runCronExplain() {
        try {
            String input = ToolTask.snapshot(cronField);
            tasks.submit(() -> CronExplainer.explainCron(input), outputArea::setText, error -> outputArea.setText("处理失败: " + error));
        } catch (IllegalArgumentException ex) {
            tasks.cancel();
            outputArea.setText(ex.getMessage());
        }
    }
}
