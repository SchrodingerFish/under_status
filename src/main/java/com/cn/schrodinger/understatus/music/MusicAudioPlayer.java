package com.cn.schrodinger.understatus.music;

import javazoom.jl.player.Player;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
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

    private volatile Player currentJlayerPlayer;
    private InputStream currentAudioStream;
    private HttpURLConnection currentHttpConn;
    private MusicSong currentSong;
    private volatile boolean isPlaying;
    private volatile boolean isPaused;
    private volatile boolean userStopped;
    private volatile PlayerListener listener;
    private volatile boolean closed;
    private javax.swing.Timer progressTimer;

    private long playGeneration = 0;
    private long playbackStartTimestamp = 0;

    public MusicAudioPlayer() {
        initProgressTimer();
    }

    public void setListener(PlayerListener listener) {
        this.listener = listener;
    }

    private void initProgressTimer() {
        progressTimer = new javax.swing.Timer(150, e -> {
            if (isPlaying && listener != null) {
                long pos = -1;
                if (currentJlayerPlayer != null) {
                    try {
                        pos = currentJlayerPlayer.getPosition();
                    } catch (Exception ignored) {
                    }
                }
                if (pos <= 0 && playbackStartTimestamp > 0) {
                    pos = System.currentTimeMillis() - playbackStartTimestamp;
                }
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
        if (closed) return;
        this.playGeneration++;
        final long generation = this.playGeneration;

        stopInternal(false);

        if (song == null || audioUrl == null || audioUrl.isBlank()) {
            notifyError("音频播放地址为空或无法获取");
            return;
        }

        this.currentSong = song;
        this.isPlaying = true;
        this.isPaused = false;
        this.userStopped = false;
        this.playbackStartTimestamp = System.currentTimeMillis();

        notifyStatusChanged();
        progressTimer.start();

        final MusicSong targetSong = song;
        executor.submit(() -> {
            synchronized (MusicAudioPlayer.this) {
                if (generation != playGeneration || userStopped) {
                    return;
                }
            }

            try {
                BufferedInputStream bufferedStream = openAudioStream(audioUrl, generation);
                Player player;

                synchronized (MusicAudioPlayer.this) {
                    if (generation != playGeneration || userStopped || currentSong != targetSong) {
                        try {
                            bufferedStream.close();
                        } catch (Exception ignored) {
                        }
                        return;
                    }
                    currentAudioStream = bufferedStream;
                    player = createPlayer(bufferedStream);
                    currentJlayerPlayer = player;
                    playbackStartTimestamp = System.currentTimeMillis();
                }

                int frames = 0;
                while (true) {
                    synchronized (MusicAudioPlayer.this) {
                        while (generation == playGeneration && isPaused && !closed) MusicAudioPlayer.this.wait();
                        if (generation != playGeneration || userStopped || closed) return;
                    }
                    if (!player.play(1)) break;
                    frames++;
                }

                synchronized (MusicAudioPlayer.this) {
                    boolean finishedNaturally = generation == playGeneration && !userStopped && currentSong == targetSong;
                    if (finishedNaturally) {
                        boolean pausedAtEnd = isPaused;
                        stopInternal(false);
                        if (frames > 0 && !pausedAtEnd && listener != null) {
                            SwingUtilities.invokeLater(() -> {
                                synchronized (MusicAudioPlayer.this) {
                                    if (!closed && generation == playGeneration && listener != null) listener.onSongFinished(targetSong);
                                }
                            });
                        } else if (frames == 0) {
                            notifyError("音频无法解析或音轨无效");
                        }
                    }
                }
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Error playing audio stream", ex);
                synchronized (MusicAudioPlayer.this) {
                    boolean failedWhilePlaying = generation == playGeneration && !userStopped && currentSong == targetSong;
                    if (failedWhilePlaying) {
                        stopInternal(false);
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
        progressTimer.stop();
        notifyStatusChanged();
    }

    public synchronized void resume() {
        if (!closed && currentSong != null && isPaused) {
            isPaused = false;
            isPlaying = true;
            notifyAll();
            progressTimer.start();
            notifyStatusChanged();
        }
    }

    public synchronized void stop() {
        playGeneration++;
        stopInternal(true);
    }

    private void stopInternal(boolean explicitUserStop) {
        userStopped = explicitUserStop;
        isPlaying = false;
        isPaused = false;
        notifyAll();
        progressTimer.stop();

        stopJlayerOnly();

        if (explicitUserStop) currentSong = null;
        notifyStatusChanged();
    }

    private void stopJlayerOnly() {
        if (currentHttpConn != null) {
            try {
                currentHttpConn.disconnect();
            } catch (Exception ex) {
                LOGGER.log(Level.FINE, "Error disconnecting HTTP connection", ex);
            }
            currentHttpConn = null;
        }
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
            final long generation = playGeneration;
            SwingUtilities.invokeLater(() -> {
                synchronized (MusicAudioPlayer.this) {
                    if (!closed && generation == playGeneration && listener != null) listener.onStatusChanged(status, song);
                }
            });
        }
    }

    private void notifyError(String msg) {
        if (listener != null) {
            final long generation = playGeneration;
            SwingUtilities.invokeLater(() -> {
                synchronized (MusicAudioPlayer.this) {
                    if (!closed && generation == playGeneration && listener != null) listener.onError(msg);
                }
            });
        }
    }

    protected Player createPlayer(InputStream stream) throws javazoom.jl.decoder.JavaLayerException {
        return new Player(stream);
    }

    protected BufferedInputStream openAudioStream(String audioUrlStr, long generation) throws Exception {
        String currentUrl = audioUrlStr;
        for (int attempt = 0; attempt < 5; attempt++) {
            URL url = URI.create(currentUrl).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            synchronized (this) {
                if (generation != playGeneration || closed) {
                    conn.disconnect();
                    throw new java.util.concurrent.CancellationException();
                }
                currentHttpConn = conn;
            }

            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept", "*/*");
            conn.setRequestProperty("Referer", "https://music.163.com/");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setInstanceFollowRedirects(false);

            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP
                    || code == 307 || code == 308) {
                String location = conn.getHeaderField("Location");
                if (location != null && !location.isBlank()) {
                    currentUrl = URI.create(currentUrl).resolve(location).toString();
                    conn.disconnect();
                    continue;
                }
            }

            if (code >= 400) {
                conn.disconnect();
                throw new Exception("HTTP " + code);
            }

            return new BufferedInputStream(conn.getInputStream(), 64 * 1024);
        }
        throw new Exception("Too many redirects for " + audioUrlStr);
    }

    public synchronized void close() {
        closed = true;
        listener = null;
        stop();
        executor.shutdownNow();
    }
}
