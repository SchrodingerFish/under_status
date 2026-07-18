package com.cn.schrodinger.understatus;

import javax.swing.AbstractButton;

public final class UiDefaults {

    private UiDefaults() {}

    public static void describe(AbstractButton button, String name, String description) {
        button.getAccessibleContext().setAccessibleName(name == null || name.isBlank() ? description : name);
        button.getAccessibleContext().setAccessibleDescription(description);
        button.setToolTipText(description);
    }
}
