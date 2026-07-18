package com.cn.schrodinger.understatus;

import com.cn.schrodinger.understatus.toolbox.core.XmlFormatter;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.UIManager;

/**
 * XML prettify and minify formatting tab panel.
 *
 * @author peter/antigravity
 */
public class XmlTabPanel extends JPanel {

    private JTextArea xmlTextArea;

    public XmlTabPanel() {
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        xmlTextArea = new JTextArea();
        xmlTextArea.setFont(UIManager.getFont("TextArea.font").deriveFont(11f));
        add(new JScrollPane(xmlTextArea), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton prettifyBtn = new JButton("美化 XML");
        JButton minifyBtn = new JButton("压缩 XML");
        JButton clearBtn = new JButton("清空");
        JButton copyBtn = new JButton("复制");

        prettifyBtn.addActionListener(e -> triggerXmlFormatting(true));
        minifyBtn.addActionListener(e -> triggerXmlFormatting(false));
        clearBtn.addActionListener(e -> xmlTextArea.setText(""));
        copyBtn.addActionListener(e -> CommonUtils.copyToClipboard(xmlTextArea.getText()));

        buttonPanel.add(prettifyBtn);
        buttonPanel.add(minifyBtn);
        buttonPanel.add(clearBtn);
        buttonPanel.add(copyBtn);

        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void triggerXmlFormatting(boolean prettify) {
        String input = xmlTextArea.getText().trim();
        if (input.isEmpty()) return;
        
        if (prettify) {
            xmlTextArea.setText(XmlFormatter.format(input));
        } else {
            xmlTextArea.setText(XmlFormatter.minify(input));
        }
    }
}
