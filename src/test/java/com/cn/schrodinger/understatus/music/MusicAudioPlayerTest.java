package com.cn.schrodinger.understatus.music;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicAudioPlayerTest {
    static final class Frames implements MusicAudioPlayer.AudioBackend {
        final Semaphore permits = new Semaphore(0);
        final AtomicInteger reads = new AtomicInteger();
        final AtomicInteger position = new AtomicInteger();
        final CountDownLatch closed = new CountDownLatch(1);
        @Override public boolean playFrame() throws Exception {
            reads.incrementAndGet();
            permits.acquire();
            if (closed.getCount() == 0) return false;
            position.addAndGet(200);
            return true;
        }
        @Override public long positionMs() { return position.get(); }
        @Override public void close() { closed.countDown(); permits.release(); }
    }

    @Test void pauseFreezesProgressAndResumeKeepsSameDecoderAndPosition() throws Exception {
        Frames backend = new Frames();
        AtomicInteger opens = new AtomicInteger();
        try (MusicAudioPlayer player = new MusicAudioPlayer(url -> { opens.incrementAndGet(); return backend; })) {
            player.play(song("A"), "https://audio.invalid/A");
            await(() -> backend.reads.get() == 1);
            backend.permits.release();
            await(() -> player.getPositionMs() == 200 && backend.reads.get() == 2);
            player.pause();
            long pausedPosition = player.getPositionMs();
            backend.permits.release(); // Complete the one frame that was already in flight.
            await(() -> backend.position.get() == 400);
            assertEquals(pausedPosition, player.getPositionMs());
            assertTrue(player.isPaused());
            assertFalse(player.isPlaying());
            assertFalse(backend.closed.await(100, TimeUnit.MILLISECONDS));
            assertEquals(2, backend.reads.get(), "No further frame reads while paused");
            player.resume();
            await(() -> backend.reads.get() == 3);
            backend.permits.release();
            await(() -> player.getPositionMs() == 600);
            assertEquals(1, opens.get(), "Resume must not reopen the URL");
        }
        assertTrue(backend.closed.await(2, TimeUnit.SECONDS));
    }

    @Test void closeDoesNotBlockEdt() throws Exception {
        Frames frames = new Frames();
        CountDownLatch closeStarted = new CountDownLatch(1);
        CountDownLatch releaseClose = new CountDownLatch(1);
        MusicAudioPlayer player = new MusicAudioPlayer(url -> new MusicAudioPlayer.AudioBackend() {
            @Override public boolean playFrame() throws Exception { return frames.playFrame(); }
            @Override public long positionMs() { return frames.positionMs(); }
            @Override public void close() throws Exception {
                closeStarted.countDown();
                releaseClose.await();
                frames.close();
            }
        });
        try {
            player.play(song("A"), "https://audio.invalid/A");
            await(() -> frames.reads.get() == 1);
            CountDownLatch edtReturned = new CountDownLatch(1);
            SwingUtilities.invokeLater(() -> { player.close(); edtReturned.countDown(); });
            assertTrue(edtReturned.await(1, TimeUnit.SECONDS));
            assertTrue(closeStarted.await(1, TimeUnit.SECONDS));
            assertNull(player.getCurrentSong());
            assertFalse(player.isPlaying());
        } finally { releaseClose.countDown(); player.close(); }
    }

    @Test void delayedBackendOpenCannotReplaceNewSelectionAndIsClosed() throws Exception {
        CountDownLatch openingA = new CountDownLatch(1);
        CountDownLatch releaseA = new CountDownLatch(1);
        Frames a = new Frames();
        Frames b = new Frames();
        MusicAudioPlayer player = new MusicAudioPlayer(url -> {
            if (url.endsWith("/A")) {
                openingA.countDown();
                awaitUninterruptibly(releaseA);
                return a;
            }
            return b;
        });
        try {
            player.play(song("A"), "https://audio.invalid/A");
            assertTrue(openingA.await(2, TimeUnit.SECONDS));
            player.play(song("B"), "https://audio.invalid/B");
            await(() -> b.reads.get() == 1);
            releaseA.countDown();
            assertTrue(a.closed.await(2, TimeUnit.SECONDS));
            assertEquals("B", player.getCurrentSong().getId());
            assertEquals(0, a.reads.get());
        } finally { releaseA.countDown(); player.close(); }
    }

    @Test void closeInvalidatesCallbacksAlreadyQueuedOnEdt() throws Exception {
        AtomicInteger callbacks = new AtomicInteger();
        MusicAudioPlayer player = new MusicAudioPlayer(url -> new Frames());
        SwingUtilities.invokeAndWait(() -> {
            player.setListener(new MusicAudioPlayer.PlayerListener() {
                @Override public void onStatusChanged(boolean playing, MusicSong song) { callbacks.incrementAndGet(); }
                @Override public void onProgress(long position) { callbacks.incrementAndGet(); }
                @Override public void onSongFinished(MusicSong song) { callbacks.incrementAndGet(); }
                @Override public void onError(String message) { callbacks.incrementAndGet(); }
            });
            player.play(song("A"), "https://audio.invalid/A");
            player.close();
        });
        SwingUtilities.invokeAndWait(() -> assertEquals(0, callbacks.get()));
    }

    static MusicSong song(String id) { return new MusicSong(id, id, "artist", "album", "tencent"); }
    static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5);
        assertTrue(condition.getAsBoolean(), "Condition did not become true");
    }
    static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try { latch.await(); break; } catch (InterruptedException ex) { interrupted = true; }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }
}
