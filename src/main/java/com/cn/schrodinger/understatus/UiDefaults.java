package com.cn.schrodinger.understatus;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import org.openide.util.NbBundle;

public final class UiDefaults {

    private UiDefaults() {}

    public static void describe(AbstractButton button, String name, String description) {
        button.getAccessibleContext().setAccessibleName(name == null || name.isBlank() ? description : name);
        button.getAccessibleContext().setAccessibleDescription(description);
        button.setToolTipText(description);
        button.setFocusPainted(true);
        button.setFocusable(true);
    }

    public static String text(String key, Object... arguments) {
        return NbBundle.getMessage(UiDefaults.class, key, arguments);
    }

    public static Color color(String key, Color fallback) {
        Color value = UIManager.getColor(key);
        return value != null ? value : fallback;
    }

    public static Color foreground() { return color("Label.foreground", Color.BLACK); }
    public static Color background() { return color("Panel.background", Color.WHITE); }
    public static Color border() { return color("Separator.foreground", foreground()); }
    public static Font font(int style, int size) {
        Font font = UIManager.getFont("Label.font");
        return (font == null ? new Font(Font.SANS_SERIF, style, size) : font).deriveFont(style, (float) size);
    }

    /** Choose readable text on a semantic data color without changing that color's meaning. */
    public static Color onColor(Color background) {
        double luminance = 0.2126 * linear(background.getRed()) + 0.7152 * linear(background.getGreen())
                + 0.0722 * linear(background.getBlue());
        return luminance > 0.179 ? Color.BLACK : Color.WHITE;
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    /** Updated only when data changes; Swing fires the accessible description change event. */
    public static void textAlternative(JComponent component, String name, String description) {
        component.getAccessibleContext().setAccessibleName(name);
        component.getAccessibleContext().setAccessibleDescription(description);
        component.setFocusable(true);
        if (component.getClientProperty("understatus.dataFocus") == null) {
            component.putClientProperty("understatus.dataFocus", Boolean.TRUE);
            component.setBorder(BorderFactory.createCompoundBorder(new AbstractBorder() {
                @Override public Insets getBorderInsets(Component c) { return new Insets(2, 2, 2, 2); }
                @Override public Insets getBorderInsets(Component c, Insets insets) {
                    insets.set(2, 2, 2, 2);
                    return insets;
                }
                @Override public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
                    if (c.hasFocus()) {
                        g.setColor(color("Component.focusColor", foreground()));
                        g.drawRect(x, y, width - 1, height - 1);
                        g.drawRect(x + 1, y + 1, width - 3, height - 3);
                    }
                }
            }, component.getBorder()));
            component.addFocusListener(new FocusAdapter() {
                @Override public void focusGained(FocusEvent event) { component.repaint(); }
                @Override public void focusLost(FocusEvent event) { component.repaint(); }
            });
        }
    }
}
