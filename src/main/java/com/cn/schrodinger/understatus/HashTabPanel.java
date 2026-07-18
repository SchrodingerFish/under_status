package com.cn.schrodinger.understatus;

import com.cn.schrodinger.understatus.toolbox.core.HashCalculator;

import java.awt.BorderLayout;
import java.awt.Dimension;
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
 * Real-time MD5, SHA-1, SHA-256, and SHA-512 Hash digest generator.
 *
 * @author peter/antigravity
 */
public class HashTabPanel extends JPanel {

    private JTextArea inputTextArea;
    private JTextField md5Field;
    private JTextField sha1Field;
    private JTextField sha256Field;
    private JTextField sha512Field;

    public HashTabPanel() {
        initComponents();
        runHash();
    }

    private void initComponents() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // Top Input
        inputTextArea = new JTextArea("输入需要哈希的文本 (Text to hash)");
        inputTextArea.setFont(UIManager.getFont("TextArea.font").deriveFont(11f));
        inputTextArea.setLineWrap(true);
        inputTextArea.setWrapStyleWord(true);
        inputTextArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { runHash(); }
            @Override
            public void removeUpdate(DocumentEvent e) { runHash(); }
            @Override
            public void changedUpdate(DocumentEvent e) { runHash(); }
        });

        JScrollPane scrollPane = new JScrollPane(inputTextArea);
        scrollPane.setPreferredSize(new Dimension(0, 110));
        add(scrollPane, BorderLayout.CENTER);

        // Bottom Digest Grid
        JPanel digestPanel = new JPanel(new GridBagLayout());
        digestPanel.setBorder(BorderFactory.createTitledBorder("哈希计算结果 (Digests)"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 4, 4, 4);

        // MD5
        gbc.gridx = 0; gbc.gridy = 0;
        digestPanel.add(new JLabel("MD5:"), gbc);
        md5Field = createUneditableField();
        gbc.gridx = 1; gbc.weightx = 1.0;
        digestPanel.add(md5Field, gbc);
        JButton copyMd5Btn = new JButton("复制");
        copyMd5Btn.addActionListener(e -> CommonUtils.copyToClipboard(md5Field.getText()));
        gbc.gridx = 2; gbc.weightx = 0.0;
        digestPanel.add(copyMd5Btn, gbc);

        // SHA-1
        gbc.gridx = 0; gbc.gridy = 1;
        digestPanel.add(new JLabel("SHA-1:"), gbc);
        sha1Field = createUneditableField();
        gbc.gridx = 1; gbc.weightx = 1.0;
        digestPanel.add(sha1Field, gbc);
        JButton copySha1Btn = new JButton("复制");
        copySha1Btn.addActionListener(e -> CommonUtils.copyToClipboard(sha1Field.getText()));
        gbc.gridx = 2; gbc.weightx = 0.0;
        digestPanel.add(copySha1Btn, gbc);

        // SHA-256
        gbc.gridx = 0; gbc.gridy = 2;
        digestPanel.add(new JLabel("SHA-256:"), gbc);
        sha256Field = createUneditableField();
        gbc.gridx = 1; gbc.weightx = 1.0;
        digestPanel.add(sha256Field, gbc);
        JButton copySha256Btn = new JButton("复制");
        copySha256Btn.addActionListener(e -> CommonUtils.copyToClipboard(sha256Field.getText()));
        gbc.gridx = 2; gbc.weightx = 0.0;
        digestPanel.add(copySha256Btn, gbc);

        // SHA-512
        gbc.gridx = 0; gbc.gridy = 3;
        digestPanel.add(new JLabel("SHA-512:"), gbc);
        sha512Field = createUneditableField();
        gbc.gridx = 1; gbc.weightx = 1.0;
        digestPanel.add(sha512Field, gbc);
        JButton copySha512Btn = new JButton("复制");
        copySha512Btn.addActionListener(e -> CommonUtils.copyToClipboard(sha512Field.getText()));
        gbc.gridx = 2; gbc.weightx = 0.0;
        digestPanel.add(copySha512Btn, gbc);

        add(digestPanel, BorderLayout.SOUTH);
    }

    private JTextField createUneditableField() {
        JTextField f = new JTextField();
        f.setEditable(false);
        f.setFont(UIManager.getFont("TextField.font").deriveFont(11f));
        return f;
    }

    private void runHash() {
        String input = inputTextArea.getText();
        md5Field.setText(HashCalculator.calculateHash(input, "MD5"));
        sha1Field.setText(HashCalculator.calculateHash(input, "SHA-1"));
        sha256Field.setText(HashCalculator.calculateHash(input, "SHA-256"));
        sha512Field.setText(HashCalculator.calculateHash(input, "SHA-512"));
    }
}
