package com.cn.schrodinger.understatus.music;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import javazoom.jl.player.Player;

/** Frame-based streaming player. Pause preserves the decoder and stream position. */
public class MusicAudioPlayer implements AutoCloseable {
    public interface PlayerListener {
        void onStatusChanged(boolean playing, MusicSong song);
        void onProgress(long currentMs);
        void onSongFinished(MusicSong song);
        void onError(String message);
    }

    /** Implementations must allow close to interrupt a blocked frame on another thread. */
    public interface AudioBackend extends AutoCloseable {
        boolean playFrame() throws Exception;
        long positionMs();
        @Override void close() throws Exception;
    }

    @FunctionalInterface
    public interface BackendFactory {
        AudioBackend open(String url) throws Exception;
    }

    private static final class Playback {
        final MusicSong song;
        final long generation;
        final AtomicBoolean backendClosed = new AtomicBoolean();
        volatile AudioBackend backend;
        volatile Thread thread;
        Playback(MusicSong song, long generation) { this.song = song; this.generation = generation; }
        void closeBackend() {
            AudioBackend resource = backend;
            if (resource != null && backendClosed.compareAndSet(false, true)) {
                try { resource.close(); } catch (Exception ignored) { /* Already detached from playback. */ }
            }
        }
    }

    private final BackendFactory factory;
    private PlayerListener listener;
    private Playback current;
    private boolean playing;
    private boolean paused;
    private boolean closed;
    private long generation;
    private long positionMs;

    public MusicAudioPlayer() { this(MusicAudioPlayer::openJLayer); }

    public MusicAudioPlayer(BackendFactory factory) { this.factory = Objects.requireNonNull(factory); }

    public synchronized void setListener(PlayerListener listener) { this.listener = listener; }
    public synchronized MusicSong getCurrentSong() { return current == null ? null : current.song; }
    public synchronized boolean isPlaying() { return playing; }
    public synchronized boolean isPaused() { return paused; }
    public synchronized long getPositionMs() { return positionMs; }

    public synchronized void play(MusicSong song, String url) {
        if (closed) throw new IllegalStateException("播放器已关闭");
        detach();
        positionMs = 0;
        if (song == null || url == null || url.isBlank()) {
            publish(generation, l -> l.onError("音频播放地址为空或无法获取"));
            return;
        }
        Playback playback = new Playback(song, generation);
        current = playback;
        playing = true;
        status();
        playback.thread = Thread.ofVirtual().name("UnderStatus-Audio").unstarted(() -> decode(playback, url));
        playback.thread.start();
    }

    private void decode(Playback playback, String url) {
        try {
            // Opening, reading and closing are deliberately outside the player monitor.
            playback.backend = factory.open(url);
            synchronized (this) {
                if (current != playback || closed) return;
            }
            long lastReported = -150;
            while (true) {
                synchronized (this) {
                    while (current == playback && paused && !closed) wait();
                    if (current != playback || closed) return;
                }
                boolean hasFrame = playback.backend.playFrame();
                long position = playback.backend.positionMs();
                synchronized (this) {
                    if (current != playback || closed) return;
                    if (!hasFrame) {
                        playing = false;
                        paused = false;
                        status();
                        publish(playback.generation, l -> l.onSongFinished(playback.song));
                        return;
                    }
                    // An in-flight frame may finish at the pause boundary. Displayed progress stays frozen.
                    if (!paused) {
                        positionMs = Math.max(positionMs, position);
                        if (positionMs - lastReported >= 150) {
                            long progress = positionMs;
                            publish(playback.generation, l -> {
                                if (isPlaying()) l.onProgress(progress);
                            });
                            lastReported = positionMs;
                        }
                    }
                }
            }
        } catch (Exception ex) {
            synchronized (this) {
                if (current == playback && !closed) {
                    playing = false;
                    paused = false;
                    status();
                    publish(playback.generation, l -> l.onError("音频播放失败: " + ex.getMessage()));
                }
            }
        } finally {
            playback.closeBackend();
        }
    }

    public synchronized void togglePause() {
        if (playing) pause();
        else if (paused) resume();
    }

    public synchronized void pause() {
        if (!playing) return;
        playing = false;
        paused = true;
        status();
    }

    public synchronized void resume() {
        if (!paused || current == null || closed) return;
        paused = false;
        playing = true;
        notifyAll();
        status();
    }

    public synchronized void stop() {
        detach();
        positionMs = 0;
        status();
    }

    private void detach() {
        generation++;
        Playback old = current;
        current = null;
        playing = false;
        paused = false;
        notifyAll();
        if (old != null) {
            if (old.thread != null) old.thread.interrupt();
            Thread.ofVirtual().name("UnderStatus-AudioClose").start(old::closeBackend);
        }
    }

    private void status() {
        boolean state = playing;
        MusicSong song = getCurrentSong();
        publish(generation, l -> l.onStatusChanged(state, song));
    }

    private void publish(long token, Consumer<PlayerListener> action) {
        PlayerListener target = listener;
        SwingUtilities.invokeLater(() -> {
            synchronized (MusicAudioPlayer.this) {
                if (closed || token != generation || target == null || target != listener) return;
            }
            action.accept(target);
        });
    }

    @Override public synchronized void close() {
        if (closed) return;
        detach();
        closed = true;
        listener = null;
    }

    private static AudioBackend openJLayer(String url) throws Exception {
        InputStream stream = new BufferedInputStream(MusicHttp.open(URI.create(url)), 64 * 1024);
        try {
            Player player = new Player(stream);
            return new AudioBackend() {
                @Override public boolean playFrame() throws Exception { return player.play(1); }
                @Override public long positionMs() { return player.getPosition(); }
                @Override public void close() throws Exception {
                    try { player.close(); } finally { stream.close(); }
                }
            };
        } catch (Exception ex) {
            stream.close();
            throw ex;
        }
    }
}
