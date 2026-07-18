package com.cn.schrodinger.understatus;

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

    private JTextField cronField;
    private JTextArea outputArea;

    public CronTabPanel() {
        initComponents();
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

        add(headerPanel, BorderLayout.NORTH);

        // Center: Output Results
        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setFont(UIManager.getFont("TextArea.font").deriveFont(11f));
        add(new JScrollPane(outputArea), BorderLayout.CENTER);

        // South: Control Bar
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton clearBtn = new JButton("清空");
        JButton copyBtn = new JButton("复制解析");

        clearBtn.addActionListener(e -> {
            cronField.setText("");
            outputArea.setText("");
        });
        copyBtn.addActionListener(e -> CommonUtils.copyToClipboard(outputArea.getText()));

        buttonPanel.add(clearBtn);
        buttonPanel.add(copyBtn);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void runCronExplain() {
        String cron = cronField.getText().trim();
        if (cron.isEmpty()) {
            outputArea.setText("请输入 Cron 表达式");
            return;
        }
        outputArea.setText(CronExplainer.explainCron(cron));
    }
}
