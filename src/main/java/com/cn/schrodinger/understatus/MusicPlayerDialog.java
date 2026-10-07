package com.cn.schrodinger.understatus;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import javax.swing.JDialog;

/**
 * Standalone Music Player window for convenient desktop management.
 */
public class MusicPlayerDialog extends JDialog {

    private static MusicPlayerDialog instance;
    private final MusicTabPanel musicPanel;

    public static synchronized void showDialog(Frame parent) {
        if (instance == null || !instance.isDisplayable()) {
            instance = new MusicPlayerDialog(parent);
        }
        instance.setVisible(true);
        instance.toFront();
        instance.requestFocus();
    }

    public static synchronized MusicPlayerDialog getInstance() {
        return instance;
    }

    private MusicPlayerDialog(Frame parent) {
        super(parent, "🎵 独立音乐播放器 (Music Player)", false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        setMinimumSize(new Dimension(800, 550));
        setPreferredSize(new Dimension(900, 600));

        musicPanel = new MusicTabPanel();
        add(musicPanel, BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(parent);

        getRootPane().registerKeyboardAction(
                e -> dispose(),
                javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }

    public MusicTabPanel getMusicPanel() {
        return musicPanel;
    }

    @Override public void dispose() {
        if (musicPanel != null) musicPanel.close();
        super.dispose();
    }
}
