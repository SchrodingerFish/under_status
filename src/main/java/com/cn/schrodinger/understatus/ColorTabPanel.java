package com.cn.schrodinger.understatus;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Color picker dialog panel integrating JColorChooser.
 * Keeps QuickNoteCalcDialog open by setting its pickingColor state flag.
 *
 * @author peter/antigravity
 */
public class ColorTabPanel extends JPanel {

    private JPanel colorPreviewPanel;
    private JTextField hexField;
    private JTextField rgbField;

    public ColorTabPanel() {
        initComponents();
    }

    private void initComponents() {
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 8, 8, 8);

        JButton pickBtn = new JButton("🎨 开启调色盘选择颜色 (Pick Color)");
        pickBtn.setFont(pickBtn.getFont().deriveFont(12f));
        pickBtn.addActionListener(e -> triggerColorPicker());
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 3;
        add(pickBtn, gbc);

        gbc.gridy = 1; gbc.gridwidth = 1;
        add(new JLabel("选中色块 (Preview):"), gbc);
        
        colorPreviewPanel = new JPanel();
        colorPreviewPanel.setBackground(Color.LIGHT_GRAY);
        colorPreviewPanel.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        colorPreviewPanel.setPreferredSize(new Dimension(80, 25));
        gbc.gridx = 1; gbc.gridwidth = 2;
        add(colorPreviewPanel, gbc);

        gbc.gridy = 2; gbc.gridx = 0; gbc.gridwidth = 1;
        add(new JLabel("十六进制 (Hex):"), gbc);
        hexField = new JTextField("#D3D3D3", 8);
        hexField.setEditable(false);
        gbc.gridx = 1;
        add(hexField, gbc);

        JButton copyHexBtn = new JButton("复制");
        copyHexBtn.addActionListener(e -> CommonUtils.copyToClipboard(hexField.getText()));
        gbc.gridx = 2;
        add(copyHexBtn, gbc);

        gbc.gridy = 3; gbc.gridx = 0;
        add(new JLabel("三原色 (RGB):"), gbc);
        rgbField = new JTextField("211, 211, 211", 8);
        rgbField.setEditable(false);
        gbc.gridx = 1;
        add(rgbField, gbc);

        JButton copyRgbBtn = new JButton("复制");
        copyRgbBtn.addActionListener(e -> CommonUtils.copyToClipboard(rgbField.getText()));
        gbc.gridx = 2;
        add(copyRgbBtn, gbc);
    }

    private void triggerColorPicker() {
        Color initialColor = colorPreviewPanel.getBackground();
        java.awt.Window parent = SwingUtilities.getWindowAncestor(this);
        
        if (parent instanceof QuickNoteCalcDialog) {
            ((QuickNoteCalcDialog) parent).isPickingColor = true;
        }
        
        try {
            Color selectedColor = JColorChooser.showDialog(this, "选择颜色 (Choose Color)", initialColor);
            if (selectedColor != null) {
                colorPreviewPanel.setBackground(selectedColor);
                String hex = String.format("#%02X%02X%02X", selectedColor.getRed(), selectedColor.getGreen(), selectedColor.getBlue());
                String rgb = String.format("%d, %d, %d", selectedColor.getRed(), selectedColor.getGreen(), selectedColor.getBlue());
                hexField.setText(hex);
                rgbField.setText(rgb);
            }
        } finally {
            if (parent instanceof QuickNoteCalcDialog) {
                ((QuickNoteCalcDialog) parent).isPickingColor = false;
            }
        }
    }
}
