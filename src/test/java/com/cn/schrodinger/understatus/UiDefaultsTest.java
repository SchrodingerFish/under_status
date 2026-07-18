package com.cn.schrodinger.understatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.swing.JButton;
import org.junit.jupiter.api.Test;

class UiDefaultsTest {

    @Test
    void describesIconButtonForAssistiveTechnology() {
        JButton button = new JButton("⚙️");
        UiDefaults.describe(button, "设置", "打开 UnderStatus 设置");
        assertEquals("设置", button.getAccessibleContext().getAccessibleName());
        assertEquals("打开 UnderStatus 设置", button.getToolTipText());
    }
}
