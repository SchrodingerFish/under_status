package com.cn.schrodinger.understatus;

import com.cn.schrodinger.understatus.toolbox.core.JsonFormatter;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.UIManager;

/**
 * JSON prettify and minify formatting tab panel.
 *
 * @author peter/antigravity
 */
public class JsonTabPanel extends JPanel {

    private JTextArea jsonTextArea;

    public JsonTabPanel() {
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        jsonTextArea = new JTextArea();
        jsonTextArea.setFont(UIManager.getFont("TextArea.font").deriveFont(11f));
        add(new JScrollPane(jsonTextArea), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton prettifyBtn = new JButton("美化 JSON");
        JButton minifyBtn = new JButton("压缩 JSON");
        JButton clearBtn = new JButton("清空");
        JButton copyBtn = new JButton("复制");

        prettifyBtn.addActionListener(e -> triggerJsonFormatting(true));
        minifyBtn.addActionListener(e -> triggerJsonFormatting(false));
        clearBtn.addActionListener(e -> jsonTextArea.setText(""));
        copyBtn.addActionListener(e -> CommonUtils.copyToClipboard(jsonTextArea.getText()));

        buttonPanel.add(prettifyBtn);
        buttonPanel.add(minifyBtn);
        buttonPanel.add(clearBtn);
        buttonPanel.add(copyBtn);

        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void triggerJsonFormatting(boolean prettify) {
        String input = jsonTextArea.getText().trim();
        if (input.isEmpty()) return;
        try {
            if (prettify) {
                jsonTextArea.setText(JsonFormatter.format(input));
            } else {
                jsonTextArea.setText(JsonFormatter.minify(input));
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "JSON格式不合法: " + ex.getMessage(), "格式化失败", JOptionPane.ERROR_MESSAGE);
        }
    }
}
