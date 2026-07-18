package com.cn.schrodinger.understatus;

import com.cn.schrodinger.understatus.toolbox.core.EncodingConverter;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.UIManager;

/**
 * Text Encoding Converter UI tab panel.
 * Converts Unicode escapes and ASCII values.
 *
 * @author peter/antigravity
 */
public class EncodingTabPanel extends JPanel {

    private JTextArea inputArea;
    private JTextArea outputArea;

    public EncodingTabPanel() {
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        inputArea = new JTextArea("输入需要转换的内容...");
        inputArea.setFont(UIManager.getFont("TextArea.font").deriveFont(11f));
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);

        outputArea = new JTextArea();
        outputArea.setFont(UIManager.getFont("TextArea.font").deriveFont(11f));
        outputArea.setLineWrap(true);
        outputArea.setWrapStyleWord(true);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(inputArea), new JScrollPane(outputArea));
        splitPane.setDividerLocation(120);
        add(splitPane, BorderLayout.CENTER);

        // Control Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        
        JButton uniEncBtn = new JButton("➔ Unicode");
        uniEncBtn.addActionListener(e -> outputArea.setText(EncodingConverter.stringToUnicode(inputArea.getText())));

        JButton uniDecBtn = new JButton("Unicode ➔");
        uniDecBtn.addActionListener(e -> outputArea.setText(EncodingConverter.unicodeToString(inputArea.getText())));

        JButton asciiEncBtn = new JButton("➔ ASCII");
        asciiEncBtn.addActionListener(e -> outputArea.setText(EncodingConverter.stringToAscii(inputArea.getText())));

        JButton asciiDecBtn = new JButton("ASCII ➔");
        asciiDecBtn.addActionListener(e -> outputArea.setText(EncodingConverter.asciiToString(inputArea.getText())));

        JButton clearBtn = new JButton("清空");
        clearBtn.addActionListener(e -> {
            inputArea.setText("");
            outputArea.setText("");
        });

        JButton copyBtn = new JButton("复制结果");
        copyBtn.addActionListener(e -> CommonUtils.copyToClipboard(outputArea.getText()));

        buttonPanel.add(uniEncBtn);
        buttonPanel.add(uniDecBtn);
        buttonPanel.add(asciiEncBtn);
        buttonPanel.add(asciiDecBtn);
        buttonPanel.add(clearBtn);
        buttonPanel.add(copyBtn);

        add(buttonPanel, BorderLayout.SOUTH);
    }
}
