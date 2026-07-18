package com.cn.schrodinger.understatus;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * UUID and Secure Alphanumeric Password generators panel.
 *
 * @author peter/antigravity
 */
public class GenTabPanel extends JPanel {

    private JTextField genResultField;

    public GenTabPanel() {
        initComponents();
    }

    private void initComponents() {
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 10, 10, 10);

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 3;
        genResultField = new JTextField(20);
        genResultField.setEditable(false);
        genResultField.setFont(genResultField.getFont().deriveFont(12f));
        add(genResultField, gbc);

        gbc.gridy = 1; gbc.gridwidth = 1;
        
        JButton uuidBtn = new JButton("生成 UUID");
        uuidBtn.addActionListener(e -> genResultField.setText(java.util.UUID.randomUUID().toString()));
        gbc.gridx = 0; add(uuidBtn, gbc);

        JButton pwdBtn = new JButton("随机密码 (16位)");
        pwdBtn.addActionListener(e -> genResultField.setText(CommonUtils.generateRandomString(16)));
        gbc.gridx = 1; add(pwdBtn, gbc);

        JButton copyBtn = new JButton("复制到剪贴板");
        copyBtn.addActionListener(e -> CommonUtils.copyToClipboard(genResultField.getText()));
        gbc.gridx = 2; add(copyBtn, gbc);
    }
}
