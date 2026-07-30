package com.cn.schrodinger.understatus.music;

import javazoom.jl.player.Player;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;

/**
 * Audio player thread manager backed by JLayer for background MP3 streaming.
 */
public class MusicAudioPlayer {

    private static final Logger LOGGER = Logger.getLogger(MusicAudioPlayer.class.getName());
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    public interface PlayerListener {
        void onStatusChanged(boolean playing, MusicSong song);
        void onProgress(long currentMs);
        void onSongFinished(MusicSong song);
        void onError(String message);
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "UnderStatus-AudioPlayer");
        t.setDaemon(true);
        return t;
    });

    private Player currentJlayerPlayer;
    private InputStream currentAudioStream;
    private MusicSong currentSong;
    private volatile boolean isPlaying;
    private volatile boolean isPaused;
    private volatile boolean userStopped;
    private PlayerListener listener;
    private javax.swing.Timer progressTimer;

    public MusicAudioPlayer() {
        initProgressTimer();
    }

    public void setListener(PlayerListener listener) {
        this.listener = listener;
    }

    private void initProgressTimer() {
        progressTimer = new javax.swing.Timer(500, e -> {
            if (isPlaying && currentJlayerPlayer != null && listener != null) {
                long pos = currentJlayerPlayer.getPosition();
                listener.onProgress(pos);
            }
        });
    }

    public synchronized MusicSong getCurrentSong() {
        return currentSong;
    }

    public synchronized boolean isPlaying() {
        return isPlaying;
    }

    public synchronized boolean isPaused() {
        return isPaused;
    }

    /**
     * Play given song audio stream.
     */
    public synchronized void play(MusicSong song, String audioUrl) {
        stopInternal(false);

        if (song == null || audioUrl == null || audioUrl.isBlank()) {
            notifyError("音频播放地址为空或无法获取");
            return;
        }

        this.currentSong = song;
        this.isPlaying = true;
        this.isPaused = false;
        this.userStopped = false;

        notifyStatusChanged();
        progressTimer.start();

        executor.submit(() -> {
            try {
                URL url = URI.create(audioUrl).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", USER_AGENT);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                conn.setInstanceFollowRedirects(true);

                InputStream rawStream = conn.getInputStream();
                BufferedInputStream bufferedStream = new BufferedInputStream(rawStream, 64 * 1024);

                synchronized (MusicAudioPlayer.this) {
                    currentAudioStream = bufferedStream;
                    currentJlayerPlayer = new Player(bufferedStream);
                }

                currentJlayerPlayer.play();

                // Playback finished normally
                synchronized (MusicAudioPlayer.this) {
                    boolean finishedNaturally = isPlaying && !userStopped && !isPaused;
                    stopInternal(false);
                    if (finishedNaturally && listener != null) {
                        final MusicSong completed = song;
                        SwingUtilities.invokeLater(() -> listener.onSongFinished(completed));
                    }
                }
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Error playing audio stream", ex);
                synchronized (MusicAudioPlayer.this) {
                    boolean failedWhilePlaying = isPlaying && !userStopped;
                    stopInternal(false);
                    if (failedWhilePlaying) {
                        notifyError("音频播放失败: " + ex.getMessage());
                    }
                }
            }
        });
    }

    /**
     * Pause or resume playback.
     */
    public synchronized void togglePause() {
        if (currentSong == null) {
            return;
        }
        if (isPlaying) {
            pause();
        } else if (isPaused) {
            resume();
        }
    }

    public synchronized void pause() {
        if (!isPlaying || currentSong == null) {
            return;
        }
        isPaused = true;
        isPlaying = false;
        stopJlayerOnly();
        progressTimer.stop();
        notifyStatusChanged();
    }

    public synchronized void resume() {
        if (currentSong != null && (isPaused || !isPlaying)) {
            String url = currentSong.getUrl();
            if (url != null && !url.isBlank()) {
                play(currentSong, url);
            }
        }
    }

    public synchronized void stop() {
        stopInternal(true);
    }

    private void stopInternal(boolean explicitUserStop) {
        userStopped = explicitUserStop;
        isPlaying = false;
        isPaused = false;
        progressTimer.stop();

        stopJlayerOnly();

        if (explicitUserStop) {
            currentSong = null;
            notifyStatusChanged();
        }
    }

    private void stopJlayerOnly() {
        if (currentJlayerPlayer != null) {
            try {
                currentJlayerPlayer.close();
            } catch (Exception ex) {
                LOGGER.log(Level.FINE, "Error closing JLayer player", ex);
            }
            currentJlayerPlayer = null;
        }
        if (currentAudioStream != null) {
            try {
                currentAudioStream.close();
            } catch (Exception ex) {
                LOGGER.log(Level.FINE, "Error closing audio stream", ex);
            }
            currentAudioStream = null;
        }
    }

    private void notifyStatusChanged() {
        if (listener != null) {
            final boolean status = isPlaying;
            final MusicSong song = currentSong;
            SwingUtilities.invokeLater(() -> listener.onStatusChanged(status, song));
        }
    }

    private void notifyError(String msg) {
        if (listener != null) {
            SwingUtilities.invokeLater(() -> listener.onError(msg));
        }
    }

    public void close() {
        stop();
        executor.shutdownNow();
    }
}
