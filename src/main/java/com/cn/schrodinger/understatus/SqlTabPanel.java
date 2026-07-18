package com.cn.schrodinger.understatus;

import com.cn.schrodinger.understatus.toolbox.core.SqlFormatter;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.UIManager;

/**
 * SQL Prettify and Minify formatting tab panel.
 *
 * @author peter/antigravity
 */
public class SqlTabPanel extends JPanel {

    private JTextArea sqlTextArea;

    public SqlTabPanel() {
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        sqlTextArea = new JTextArea();
        sqlTextArea.setFont(UIManager.getFont("TextArea.font").deriveFont(11f));
        add(new JScrollPane(sqlTextArea), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton prettifyBtn = new JButton("美化 SQL");
        JButton minifyBtn = new JButton("压缩 SQL");
        JButton clearBtn = new JButton("清空");
        JButton copyBtn = new JButton("复制");

        prettifyBtn.addActionListener(e -> triggerSqlFormatting(true));
        minifyBtn.addActionListener(e -> triggerSqlFormatting(false));
        clearBtn.addActionListener(e -> sqlTextArea.setText(""));
        copyBtn.addActionListener(e -> CommonUtils.copyToClipboard(sqlTextArea.getText()));

        buttonPanel.add(prettifyBtn);
        buttonPanel.add(minifyBtn);
        buttonPanel.add(clearBtn);
        buttonPanel.add(copyBtn);

        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void triggerSqlFormatting(boolean prettify) {
        String input = sqlTextArea.getText().trim();
        if (input.isEmpty()) return;
        
        if (prettify) {
            sqlTextArea.setText(SqlFormatter.format(input));
        } else {
            sqlTextArea.setText(SqlFormatter.minify(input));
        }
    }
}
