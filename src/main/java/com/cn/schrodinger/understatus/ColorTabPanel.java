package com.cn.schrodinger.understatus;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
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
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Modern bidirectional Color Picker & Converter tab panel.
 * Supports real-time editing of Hex/RGB and a read-only HSL conversion,
 * JColorChooser integration, and a developer quick-palette swatch bar.
 *
 * @author peter/antigravity
 */
public class ColorTabPanel extends JPanel {

    private JPanel colorPreviewPanel;
    private JTextField hexField;
    private JTextField rgbField;
    private JTextField hslField;
    private JLabel statusLabel;

    private boolean isUpdating = false;

    private static final String[][] PALETTE_SWATCHES = {
        {"#3B82F6", "科技蓝 (Primary Blue)"},
        {"#10B981", "翡翠绿 (Emerald Green)"},
        {"#F59E0B", "琥珀黄 (Amber Warning)"},
        {"#EF4444", "珊瑚红 (Rose Red)"},
        {"#8B5CF6", "创意紫 (Violet Purple)"},
        {"#06B6D4", "极光青 (Aurora Cyan)"},
        {"#64748B", "石板灰 (Slate Gray)"},
        {"#1E293B", "暗夜深蓝 (Navy Dark)"},
        {"#FFFFFF", "纯净白 (Clean White)"},
        {"#000000", "极夜黑 (Clean Black)"}
    };

    public ColorTabPanel() {
        initComponents();
        applyColor(new Color(59, 130, 246), true, true, true);
    }

    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // Center Container
        JPanel centerPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        // Row 0: Palette Swatches header
        JPanel swatchesBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        swatchesBar.setBorder(BorderFactory.createTitledBorder("常用开发色彩预设 (Quick Palette)"));

        for (String[] swatch : PALETTE_SWATCHES) {
            Color c = parseHex(swatch[0]);
            JButton tile = new JButton();
            tile.setPreferredSize(new Dimension(28, 28));
            tile.setMargin(new Insets(0, 0, 0, 0));
            tile.setBackground(c);
            tile.setOpaque(true);
            tile.setBorder(BorderFactory.createLineBorder(UiDefaults.border(), 1));
            String name = swatch[0] + " - " + swatch[1];
            String hint = UiDefaults.text("Color.swatchHint", swatch[0]);
            UiDefaults.describe(tile, name, hint);
            UiDefaults.textAlternative(tile, name, hint);
            tile.addActionListener(e -> {
                applyColor(c, true, true, true);
                CommonUtils.copyToClipboard(swatch[0]);
            });
            swatchesBar.add(tile);
        }
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 3;
        centerPanel.add(swatchesBar, gbc);

        // Row 1: Pick Button & Preview block
        JButton pickBtn = new JButton(UiDefaults.text("Color.choose"));
        pickBtn.setFont(pickBtn.getFont().deriveFont(12f));
        pickBtn.addActionListener(e -> triggerColorPicker());
        gbc.gridy = 1; gbc.gridx = 0; gbc.gridwidth = 1;
        centerPanel.add(pickBtn, gbc);

        colorPreviewPanel = new JPanel();
        colorPreviewPanel.setBackground(new Color(59, 130, 246));
        colorPreviewPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiDefaults.border(), 1),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));
        colorPreviewPanel.setPreferredSize(new Dimension(100, 32));
        gbc.gridx = 1; gbc.gridwidth = 2;
        centerPanel.add(colorPreviewPanel, gbc);

        // Row 2: Hex Field (Editable)
        gbc.gridy = 2; gbc.gridx = 0; gbc.gridwidth = 1;
        centerPanel.add(new JLabel("十六进制 (Hex):"), gbc);

        hexField = new JTextField("#3B82F6", 10);
        hexField.getAccessibleContext().setAccessibleName("Hex");
        hexField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { onHexChanged(); }
            @Override public void removeUpdate(DocumentEvent e) { onHexChanged(); }
            @Override public void changedUpdate(DocumentEvent e) { onHexChanged(); }
        });
        gbc.gridx = 1; gbc.weightx = 1.0;
        centerPanel.add(hexField, gbc);

        JButton copyHexBtn = new JButton(UiDefaults.text("Action.copy"));
        UiDefaults.describe(copyHexBtn, UiDefaults.text("Color.copyValue", "Hex"), UiDefaults.text("Color.copyValue", "Hex"));
        copyHexBtn.addActionListener(e -> CommonUtils.copyToClipboard(hexField.getText()));
        gbc.gridx = 2; gbc.weightx = 0.0;
        centerPanel.add(copyHexBtn, gbc);

        // Row 3: RGB Field (Editable)
        gbc.gridy = 3; gbc.gridx = 0;
        centerPanel.add(new JLabel("三原色 (RGB):"), gbc);

        rgbField = new JTextField("59, 130, 246", 10);
        rgbField.getAccessibleContext().setAccessibleName("RGB");
        rgbField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { onRgbChanged(); }
            @Override public void removeUpdate(DocumentEvent e) { onRgbChanged(); }
            @Override public void changedUpdate(DocumentEvent e) { onRgbChanged(); }
        });
        gbc.gridx = 1; gbc.weightx = 1.0;
        centerPanel.add(rgbField, gbc);

        JButton copyRgbBtn = new JButton(UiDefaults.text("Action.copy"));
        UiDefaults.describe(copyRgbBtn, UiDefaults.text("Color.copyValue", "RGB"), UiDefaults.text("Color.copyValue", "RGB"));
        copyRgbBtn.addActionListener(e -> CommonUtils.copyToClipboard(rgbField.getText()));
        gbc.gridx = 2; gbc.weightx = 0.0;
        centerPanel.add(copyRgbBtn, gbc);

        // Row 4: HSL Field
        gbc.gridy = 4; gbc.gridx = 0;
        centerPanel.add(new JLabel("色相/饱和度 (HSL):"), gbc);

        hslField = new JTextField("hsl(217, 91%, 60%)", 10);
        hslField.getAccessibleContext().setAccessibleName("HSL");
        hslField.setEditable(false);
        hslField.getAccessibleContext().setAccessibleDescription(UiDefaults.text("Color.hslReadOnly"));
        gbc.gridx = 1; gbc.weightx = 1.0;
        centerPanel.add(hslField, gbc);

        JButton copyHslBtn = new JButton(UiDefaults.text("Action.copy"));
        UiDefaults.describe(copyHslBtn, UiDefaults.text("Color.copyValue", "HSL"), UiDefaults.text("Color.copyValue", "HSL"));
        copyHslBtn.addActionListener(e -> CommonUtils.copyToClipboard(hslField.getText()));
        gbc.gridx = 2; gbc.weightx = 0.0;
        centerPanel.add(copyHslBtn, gbc);

        // Row 5: Status hint
        statusLabel = new JLabel(UiDefaults.text("Color.inputHint"));
        statusLabel.setFont(statusLabel.getFont().deriveFont(11f));
        statusLabel.setForeground(UiDefaults.foreground());
        gbc.gridy = 5; gbc.gridx = 0; gbc.gridwidth = 3;
        centerPanel.add(statusLabel, gbc);

        add(centerPanel, BorderLayout.CENTER);
    }

    private void onHexChanged() {
        if (isUpdating) return;
        String text = hexField.getText().trim();
        Color c = parseHex(text);
        if (c != null) {
            applyColor(c, false, true, true);
            statusLabel.setText(UiDefaults.text("Color.valid", "Hex"));
            statusLabel.setForeground(UiDefaults.foreground());
        }
    }

    private void onRgbChanged() {
        if (isUpdating) return;
        String text = rgbField.getText().trim();
        Color c = parseRgb(text);
        if (c != null) {
            applyColor(c, true, false, true);
            statusLabel.setText(UiDefaults.text("Color.valid", "RGB"));
            statusLabel.setForeground(UiDefaults.foreground());
        }
    }

    private void applyColor(Color c, boolean updateHex, boolean updateRgb, boolean updateHsl) {
        if (c == null) return;
        isUpdating = true;
        try {
            colorPreviewPanel.setBackground(c);
            colorPreviewPanel.getAccessibleContext().setAccessibleName(UiDefaults.text("Color.preview"));
            colorPreviewPanel.getAccessibleContext().setAccessibleDescription(String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue()));
            if (updateHex) {
                hexField.setText(String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue()));
            }
            if (updateRgb) {
                rgbField.setText(String.format("%d, %d, %d", c.getRed(), c.getGreen(), c.getBlue()));
            }
            if (updateHsl) {
                hslField.setText(formatHsl(c));
            }
        } finally {
            isUpdating = false;
        }
    }

    private static String formatHsl(Color c) {
        float r = c.getRed() / 255f;
        float g = c.getGreen() / 255f;
        float b = c.getBlue() / 255f;

        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float h = 0f, s = 0f, l = (max + min) / 2f;

        if (max != min) {
            float d = max - min;
            s = l > 0.5f ? d / (2f - max - min) : d / (max + min);
            if (max == r) {
                h = (g - b) / d + (g < b ? 6f : 0f);
            } else if (max == g) {
                h = (b - r) / d + 2f;
            } else {
                h = (r - g) / d + 4f;
            }
            h /= 6f;
        }

        return String.format("hsl(%d, %d%%, %d%%)", Math.round(h * 360f), Math.round(s * 100f), Math.round(l * 100f));
    }

    private static Color parseHex(String text) {
        if (text == null) return null;
        String clean = text.trim().replace("#", "");
        if (clean.length() == 3) {
            clean = "" + clean.charAt(0) + clean.charAt(0)
                       + clean.charAt(1) + clean.charAt(1)
                       + clean.charAt(2) + clean.charAt(2);
        }
        if (clean.length() == 6) {
            try {
                int r = Integer.parseInt(clean.substring(0, 2), 16);
                int g = Integer.parseInt(clean.substring(2, 4), 16);
                int b = Integer.parseInt(clean.substring(4, 6), 16);
                return new Color(r, g, b);
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private static Color parseRgb(String text) {
        if (text == null) return null;
        String clean = text.trim().replaceAll("(?i)rgb[a]?\\(", "").replace(")", "");
        String[] parts = clean.split("[,\\s]+");
        if (parts.length >= 3) {
            try {
                int r = Math.max(0, Math.min(255, Integer.parseInt(parts[0])));
                int g = Math.max(0, Math.min(255, Integer.parseInt(parts[1])));
                int b = Math.max(0, Math.min(255, Integer.parseInt(parts[2])));
                return new Color(r, g, b);
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private void triggerColorPicker() {
        Color initialColor = colorPreviewPanel.getBackground();
        java.awt.Window parent = SwingUtilities.getWindowAncestor(this);

        if (parent instanceof QuickNoteCalcDialog dlg) {
            dlg.isPickingColor = true;
        }

        try {
            Color selectedColor = JColorChooser.showDialog(this, UiDefaults.text("Color.choose"), initialColor);
            if (selectedColor != null) {
                applyColor(selectedColor, true, true, true);
            }
        } finally {
            if (parent instanceof QuickNoteCalcDialog dlg) {
                dlg.isPickingColor = false;
            }
        }
    }
}
