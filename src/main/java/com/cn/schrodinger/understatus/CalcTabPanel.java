package com.cn.schrodinger.understatus;

import com.cn.schrodinger.understatus.toolbox.core.MathParser;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

/**
 * GUI-style Calculator Panel with a standard 5x4 layout.
 * Supports numerical and operator button triggers and direct keyboard input linkage.
 *
 * @author peter/antigravity
 */
public class CalcTabPanel extends JPanel {

    private JLabel historyLabel;
    private JTextField screenField;

    public CalcTabPanel() {
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Screen area
        JPanel screenPanel = new JPanel(new GridLayout(2, 1, 2, 2));
        screenPanel.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        historyLabel = new JLabel(" ");
        historyLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        historyLabel.setFont(historyLabel.getFont().deriveFont(11f));
        historyLabel.setForeground(Color.GRAY);
        screenPanel.add(historyLabel);

        screenField = new JTextField("");
        screenField.setHorizontalAlignment(JTextField.RIGHT);
        screenField.setFont(screenField.getFont().deriveFont(18f).deriveFont(java.awt.Font.BOLD));
        // Action listener handles pressing enter on the keyboard
        screenField.addActionListener(e -> evaluate());
        screenPanel.add(screenField);

        add(screenPanel, BorderLayout.NORTH);

        // Buttons Grid
        JPanel gridPanel = new JPanel(new GridLayout(5, 4, 6, 6));

        String[] btnLabels = {
            "C", "(", ")", "⌫",
            "7", "8", "9", "/",
            "4", "5", "6", "*",
            "1", "2", "3", "-",
            "0", ".", "=", "+"
        };

        for (String label : btnLabels) {
            JButton btn = new JButton(label);
            btn.setFont(btn.getFont().deriveFont(13f).deriveFont(java.awt.Font.BOLD));
            btn.setFocusPainted(false);
            
            // Distinct highlights for action buttons
            if (label.equals("=") || label.equals("C") || label.equals("⌫")) {
                btn.setForeground(new Color(0, 120, 215));
            }

            btn.addActionListener(e -> handleButtonPress(label));
            gridPanel.add(btn);
        }

        add(gridPanel, BorderLayout.CENTER);

        // Autorequest focus when Calc tab is shown to support instant keyboard entry
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                screenField.requestFocusInWindow();
            }
        });
    }

    private void handleButtonPress(String label) {
        if (label.equals("C")) {
            screenField.setText("");
            historyLabel.setText(" ");
        } else if (label.equals("⌫")) {
            String txt = screenField.getText();
            if (!txt.isEmpty()) {
                screenField.setText(txt.substring(0, txt.length() - 1));
            }
        } else if (label.equals("=")) {
            evaluate();
        } else {
            screenField.setText(screenField.getText() + label);
        }
        screenField.requestFocusInWindow();
    }

    private void evaluate() {
        String input = screenField.getText().trim();
        if (input.isEmpty()) {
            return;
        }
        try {
            double result = MathParser.eval(input);
            historyLabel.setText(input + " =");
            if (result == (long) result) {
                screenField.setText(String.valueOf((long) result));
            } else {
                screenField.setText(String.format("%.4f", result).replaceAll("0+$", "").replaceAll("\\.$", ""));
            }
        } catch (Exception ex) {
            historyLabel.setText(input + " =");
            screenField.setText("计算错误 (Error)");
        }
    }
}
