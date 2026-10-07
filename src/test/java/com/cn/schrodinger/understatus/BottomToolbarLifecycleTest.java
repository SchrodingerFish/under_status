package com.cn.schrodinger.understatus;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cn.schrodinger.understatus.settings.SettingsRepository;
import com.cn.schrodinger.understatus.settings.SettingsStore;
import java.lang.reflect.Field;
import java.util.HashMap;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.junit.jupiter.api.Test;

class BottomToolbarLifecycleTest {
    @Test
    void remountStartsFreshResourcesAndUnmountStopsTimer() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            BottomToolbarView view = new BottomToolbarView(new SettingsRepository(SettingsStore.inMemory(new HashMap<>())));
            try {
                view.addNotify();
                Timer first = timer(view);
                assertTrue(first.isRunning());
                view.removeNotify();
                assertFalse(first.isRunning());
                view.addNotify();
                Timer second = timer(view);
                assertNotSame(first, second);
                assertTrue(second.isRunning());
                view.removeNotify();
                assertFalse(second.isRunning());
            } finally {
                view.stopUpdates();
            }
        });
    }

    private static Timer timer(BottomToolbarView view) {
        try {
            Field field = BottomToolbarView.class.getDeclaredField("updateTimer");
            field.setAccessible(true);
            return (Timer) field.get(view);
        } catch (ReflectiveOperationException ex) { throw new AssertionError(ex); }
    }
}
