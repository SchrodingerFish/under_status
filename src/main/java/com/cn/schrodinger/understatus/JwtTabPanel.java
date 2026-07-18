package com.cn.schrodinger.understatus;

import com.cn.schrodinger.understatus.toolbox.core.JwtDecoder;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * JWT token Decoder UI tab panel.
 * Decodes Header and Payload claims on-the-fly.
 *
 * @author peter/antigravity
 */
public class JwtTabPanel extends JPanel {

    private JTextArea inputArea;
    private JTextArea outputArea;

    public JwtTabPanel() {
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // North: Token Input
        inputArea = new JTextArea("在此贴入 JWT Token...");
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setFont(UIManager.getFont("TextArea.font").deriveFont(11f));
        inputArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { runJwtDecode(); }
            @Override
            public void removeUpdate(DocumentEvent e) { runJwtDecode(); }
            @Override
            public void changedUpdate(DocumentEvent e) { runJwtDecode(); }
        });

        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setPreferredSize(new Dimension(0, 80));
        add(inputScroll, BorderLayout.NORTH);

        // Center: Decoded Payload Results
        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setFont(UIManager.getFont("TextArea.font").deriveFont(11f));
        add(new JScrollPane(outputArea), BorderLayout.CENTER);

        // South: Control Bar
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton clearBtn = new JButton("清空");
        JButton copyBtn = new JButton("复制解码内容");

        clearBtn.addActionListener(e -> {
            inputArea.setText("");
            outputArea.setText("");
        });
        copyBtn.addActionListener(e -> CommonUtils.copyToClipboard(outputArea.getText()));

        buttonPanel.add(clearBtn);
        buttonPanel.add(copyBtn);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void runJwtDecode() {
        String token = inputArea.getText().trim();
        if (token.isEmpty() || token.equals("在此贴入 JWT Token...")) {
            outputArea.setText("");
            return;
        }
        outputArea.setText(JwtDecoder.decodeJwt(token));
    }
}
