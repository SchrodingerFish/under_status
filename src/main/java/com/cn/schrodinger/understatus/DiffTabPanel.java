package com.cn.schrodinger.understatus;

import com.cn.schrodinger.understatus.toolbox.core.DiffCalculator;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.UIManager;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * Text Comparison Diff panel using LCS algorithm.
 * Displays line additions in green and deletions in red.
 *
 * @author peter/antigravity
 */
public class DiffTabPanel extends JPanel {

    private JTextArea textAreaA;
    private JTextArea textAreaB;
    private JTextPane diffPane;

    public DiffTabPanel() {
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // Top comparison split: Input Text A and Input Text B
        JPanel inputsPanel = new JPanel(new GridLayout(1, 2, 5, 5));
        
        textAreaA = new JTextArea("粘贴原始文本 A (Text A)");
        textAreaA.setFont(UIManager.getFont("TextArea.font").deriveFont(11f));
        inputsPanel.add(new JScrollPane(textAreaA));

        textAreaB = new JTextArea("粘贴比对文本 B (Text B)");
        textAreaB.setFont(UIManager.getFont("TextArea.font").deriveFont(11f));
        inputsPanel.add(new JScrollPane(textAreaB));

        // Bottom unified difference viewer JTextPane
        diffPane = new JTextPane();
        diffPane.setEditable(false);
        diffPane.setFont(UIManager.getFont("TextPane.font").deriveFont(11f));
        JScrollPane diffScrollPane = new JScrollPane(diffPane);

        // Split panel
        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, inputsPanel, diffScrollPane);
        mainSplit.setDividerLocation(100);
        add(mainSplit, BorderLayout.CENTER);

        // Control buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton compareBtn = new JButton("➔ 开始对比 (Compare)");
        JButton clearBtn = new JButton("清空");
        JButton copyBtn = new JButton("复制对比结果");

        compareBtn.addActionListener(e -> triggerComparison());
        clearBtn.addActionListener(e -> {
            textAreaA.setText("");
            textAreaB.setText("");
            diffPane.setText("");
        });
        copyBtn.addActionListener(e -> CommonUtils.copyToClipboard(diffPane.getText()));

        buttonPanel.add(compareBtn);
        buttonPanel.add(clearBtn);
        buttonPanel.add(copyBtn);

        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void triggerComparison() {
        String textA = textAreaA.getText();
        String textB = textAreaB.getText();
        
        List<DiffCalculator.DiffLine> diffs = DiffCalculator.calculateDiff(textA, textB);
        renderDiff(diffs);
    }

    private void renderDiff(List<DiffCalculator.DiffLine> diffs) {
        diffPane.setText("");
        StyledDocument doc = diffPane.getStyledDocument();
        
        // Define Styles
        Style defStyle = diffPane.addStyle("def", null);
        StyleConstants.setForeground(defStyle, UIManager.getColor("TextPane.foreground"));
        
        Style addStyle = diffPane.addStyle("add", null);
        StyleConstants.setForeground(addStyle, new Color(46, 125, 50)); // Forest Green

        Style delStyle = diffPane.addStyle("del", null);
        StyleConstants.setForeground(delStyle, new Color(198, 40, 40)); // Dark Red

        try {
            for (DiffCalculator.DiffLine line : diffs) {
                Style style = defStyle;
                if (line.type == 1) {
                    style = addStyle;
                } else if (line.type == -1) {
                    style = delStyle;
                }
                doc.insertString(doc.getLength(), line.text + "\n", style);
            }
        } catch (Exception ex) {
            java.util.logging.Logger.getLogger(DiffTabPanel.class.getName()).log(java.util.logging.Level.FINE, "Diff calculation failed", ex);
        }
    }
}
