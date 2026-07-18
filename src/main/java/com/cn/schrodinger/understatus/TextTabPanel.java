package com.cn.schrodinger.understatus;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.UIManager;

/**
 * Text processing panel supporting Case Conversion and Find-and-Replace operations.
 *
 * @author peter/antigravity
 */
public class TextTabPanel extends JPanel {

    private JTextArea textInputArea;
    private JTextArea textOutputArea;
    private JTextField findField;
    private JTextField replaceField;

    public TextTabPanel() {
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JPanel inputOutputPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        
        textInputArea = new JTextArea("输入原始文本 (Input text here)");
        textInputArea.setFont(UIManager.getFont("TextArea.font").deriveFont(11f));
        textInputArea.setLineWrap(true);
        textInputArea.setWrapStyleWord(true);
        inputOutputPanel.add(new JScrollPane(textInputArea));

        textOutputArea = new JTextArea();
        textOutputArea.setEditable(false);
        textOutputArea.setFont(UIManager.getFont("TextArea.font").deriveFont(11f));
        textOutputArea.setLineWrap(true);
        textOutputArea.setWrapStyleWord(true);
        inputOutputPanel.add(new JScrollPane(textOutputArea));

        add(inputOutputPanel, BorderLayout.CENTER);

        JPanel controlPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 4, 4, 4);

        JButton upperBtn = new JButton("➔ 大写 (UPPER)");
        upperBtn.addActionListener(e -> textOutputArea.setText(textInputArea.getText().toUpperCase()));
        gbc.gridx = 0; gbc.gridy = 0;
        controlPanel.add(upperBtn, gbc);

        JButton lowerBtn = new JButton("➔ 小写 (lower)");
        lowerBtn.addActionListener(e -> textOutputArea.setText(textInputArea.getText().toLowerCase()));
        gbc.gridx = 1; gbc.gridy = 0;
        controlPanel.add(lowerBtn, gbc);

        JButton copyTextBtn = new JButton("复制输出");
        copyTextBtn.addActionListener(e -> CommonUtils.copyToClipboard(textOutputArea.getText()));
        gbc.gridx = 2; gbc.gridy = 0;
        controlPanel.add(copyTextBtn, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        controlPanel.add(new JLabel("查找 (Find):"), gbc);
        findField = new JTextField(8);
        gbc.gridx = 1;
        controlPanel.add(findField, gbc);

        JButton replaceBtn = new JButton("替换 (Replace)");
        replaceBtn.addActionListener(e -> triggerStringReplacement());
        gbc.gridx = 2;
        controlPanel.add(replaceBtn, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        controlPanel.add(new JLabel("替换为 (With):"), gbc);
        replaceField = new JTextField(8);
        gbc.gridx = 1;
        controlPanel.add(replaceField, gbc);

        add(controlPanel, BorderLayout.SOUTH);
    }

    private void triggerStringReplacement() {
        String input = textInputArea.getText();
        String find = findField.getText();
        String replace = replaceField.getText();
        if (find.isEmpty()) {
            textOutputArea.setText(input);
            return;
        }
        try {
            String result = input.replace(find, replace);
            textOutputArea.setText(result);
        } catch (Exception ex) {
            textOutputArea.setText("替换失败: " + ex.getMessage());
        }
    }
}
