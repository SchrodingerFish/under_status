package com.cn.schrodinger.understatus;

import java.awt.Component;
import org.openide.awt.StatusLineElementProvider;
import org.openide.util.lookup.ServiceProvider;

/**
 * Service provider that registers and inserts the custom bottom status bar component.
 * Positioned to the right of standard elements using position 9999.
 *
 * @author peter/antigravity
 */
@ServiceProvider(service = StatusLineElementProvider.class, position = 9999)
public class BottomToolbarProvider implements StatusLineElementProvider {

    private final BottomToolbarView panel = new BottomToolbarView();

    @Override
    public Component getStatusLineElement() {
        return panel;
    }
}
