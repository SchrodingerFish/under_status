package com.cn.schrodinger.understatus;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.accessibility.AccessibleContext;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiDefaultsTest {
    @Test void describesIconButtonAndRestoresKeyboardFocusCue() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JButton button = new JButton("⚙️");
            button.setFocusable(false);
            button.setFocusPainted(false);
            UiDefaults.describe(button, "设置", "打开 UnderStatus 设置");
            assertEquals("设置", button.getAccessibleContext().getAccessibleName());
            assertEquals("打开 UnderStatus 设置", button.getToolTipText());
            assertTrue(button.isFocusable());
            assertTrue(button.isFocusPainted());
        });
    }

    @Test void updatedTextAlternativeFiresAccessibleChangeWithoutAddingRepeatedListeners() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPanel panel = new JPanel();
            AtomicInteger changes = new AtomicInteger();
            panel.getAccessibleContext().addPropertyChangeListener(event -> {
                if (AccessibleContext.ACCESSIBLE_DESCRIPTION_PROPERTY.equals(event.getPropertyName())) changes.incrementAndGet();
            });
            UiDefaults.textAlternative(panel, "Weather", "No data");
            int listenerCount = panel.getFocusListeners().length;
            UiDefaults.textAlternative(panel, "Weather", "10:00 20°C");
            assertEquals("10:00 20°C", panel.getAccessibleContext().getAccessibleDescription());
            assertEquals(2, changes.get());
            assertEquals(listenerCount, panel.getFocusListeners().length);
            assertTrue(panel.isFocusable());
        });
    }

    @Test void themeColorsFollowUiManagerAndSemanticBadgesUseReadableText() {
        Object previous = UIManager.get("Label.foreground");
        try {
            UIManager.put("Label.foreground", Color.CYAN);
            assertEquals(Color.CYAN, UiDefaults.foreground());
            assertEquals(Color.BLACK, UiDefaults.onColor(Color.YELLOW));
            assertEquals(Color.WHITE, UiDefaults.onColor(Color.BLACK));
        } finally { UIManager.put("Label.foreground", previous); }
    }

    @Test void generatorLabelsMockCodesAndActualPasswordPolicy() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            GenTabPanel panel = new GenTabPanel();
            List<Component> components = descendants(panel);
            JTextArea result = components.stream().filter(JTextArea.class::isInstance)
                    .map(JTextArea.class::cast)
                    .filter(area -> UiDefaults.text("Generator.results").equals(area.getAccessibleContext().getAccessibleName()))
                    .findFirst().orElseThrow();
            JButton code = button(components, UiDefaults.text("Generator.mockCode"));
            code.doClick(0);
            assertTrue(result.getText().matches("[0-9]{6}"));
            assertEquals(UiDefaults.text("Generator.mockCodeHint"), result.getAccessibleContext().getAccessibleDescription());
            JButton password = button(components, UiDefaults.text("Generator.password"));
            password.doClick(0);
            assertEquals(16, result.getText().length());
            assertEquals(UiDefaults.text("Generator.passwordHint"), result.getAccessibleContext().getAccessibleDescription());
        });
    }

    @Test void paletteUsesKeyboardOperableNamedButtonsAndReadOnlyHslIsExplained() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ColorTabPanel panel = new ColorTabPanel();
            List<Component> components = descendants(panel);
            long swatches = components.stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                    .filter(button -> button.getAccessibleContext().getAccessibleName() != null)
                    .filter(button -> button.getAccessibleContext().getAccessibleName().startsWith("#"))
                    .filter(button -> button.isFocusable() && button.isFocusPainted()).count();
            assertEquals(10, swatches);
            assertTrue(components.stream().filter(javax.swing.JTextField.class::isInstance)
                    .map(javax.swing.JTextField.class::cast).anyMatch(field -> !field.isEditable()
                            && UiDefaults.text("Color.hslReadOnly").equals(field.getAccessibleContext().getAccessibleDescription())));
        });
    }

    private static JButton button(List<Component> components, String title) {
        return components.stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                .filter(button -> title.equals(button.getText())).findFirst().orElseThrow();
    }

    private static List<Component> descendants(Container parent) {
        List<Component> result = new ArrayList<>();
        for (Component child : parent.getComponents()) {
            result.add(child);
            if (child instanceof Container container) result.addAll(descendants(container));
        }
        return result;
    }
}
