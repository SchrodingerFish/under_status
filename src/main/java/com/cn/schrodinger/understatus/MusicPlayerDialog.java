package com.cn.schrodinger.understatus;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
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
        setLayout(new BorderLayout());
        setMinimumSize(new Dimension(800, 550));
        setPreferredSize(new Dimension(900, 600));

        musicPanel = new MusicTabPanel();
        add(musicPanel, BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(parent);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                // Window closing handling if needed
            }
        });
    }

    public MusicTabPanel getMusicPanel() {
        return musicPanel;
    }
}
