package com.cn.schrodinger.understatus;

/** Coordinates initial status bar state without making the NetBeans provider own UI details. */
public final class StatusBarController {

    private final BottomToolbarView view;
    private boolean started;

    public StatusBarController(BottomToolbarView view) {
        this.view = java.util.Objects.requireNonNull(view);
    }

    public void start() {
        if (started) return;
        started = true;
        view.loadSettings();
    }

    public void close() {
        started = false;
    }
}
