package com.cn.schrodinger.understatus.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.Player;
import org.junit.jupiter.api.Test;

class MusicAudioPlayerTest {
    @Test void pauseKeepsDecoderAndResumeContinuesNextFrame() throws Exception {
        ControlledPlayer decoder = new ControlledPlayer();
        AtomicInteger opens = new AtomicInteger();
        MusicAudioPlayer engine = new MusicAudioPlayer() {
            @Override protected BufferedInputStream openAudioStream(String url, long generation) {
                opens.incrementAndGet();
                return new BufferedInputStream(new ByteArrayInputStream(new byte[0]));
            }
            @Override protected Player createPlayer(InputStream input) { return decoder; }
        };
        try {
            MusicSong song = new MusicSong("1", "song", "artist", "album", "netease", "test", "", "");
            engine.play(song, "test");
            assertTrue(decoder.entered.tryAcquire(2, TimeUnit.SECONDS));
            engine.pause();
            assertTrue(engine.isPaused());
            assertFalse(decoder.closed);
            decoder.release.release();
            assertFalse(decoder.entered.tryAcquire(100, TimeUnit.MILLISECONDS));
            engine.resume();
            assertTrue(decoder.entered.tryAcquire(2, TimeUnit.SECONDS));
            assertEquals(1, opens.get());
            assertEquals(2, decoder.frames.get());
        } finally { engine.close(); }
        assertTrue(decoder.closed);
    }

    private static final class ControlledPlayer extends Player {
        final Semaphore entered = new Semaphore(0);
        final Semaphore release = new Semaphore(0);
        final AtomicInteger frames = new AtomicInteger();
        volatile boolean closed;
        ControlledPlayer() throws JavaLayerException {
            super(new ByteArrayInputStream(new byte[0]), new SilentDevice());
        }
        @Override public boolean play(int count) throws JavaLayerException {
            frames.incrementAndGet();
            entered.release();
            try { release.acquire(); }
            catch (InterruptedException ex) { Thread.currentThread().interrupt(); return false; }
            return !closed;
        }
        @Override public synchronized void close() { closed = true; release.release(); super.close(); }
    }

    private static final class SilentDevice implements AudioDevice {
        @Override public void open(Decoder decoder) {}
        @Override public boolean isOpen() { return true; }
        @Override public void write(short[] samples, int offset, int length) {}
        @Override public void close() {}
        @Override public void flush() {}
        @Override public int getPosition() { return 0; }
    }
}
