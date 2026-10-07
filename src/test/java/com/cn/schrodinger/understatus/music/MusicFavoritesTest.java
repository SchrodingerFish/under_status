package com.cn.schrodinger.understatus.music;

import com.cn.schrodinger.understatus.settings.ContentStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static com.cn.schrodinger.understatus.music.MusicAudioPlayerTest.await;
import static com.cn.schrodinger.understatus.music.MusicAudioPlayerTest.awaitUninterruptibly;
import static com.cn.schrodinger.understatus.music.MusicAudioPlayerTest.song;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicFavoritesTest {
    @Test void malformedStoredRowBlocksEditingAndDoesNotOverwriteAnyContent() throws Exception {
        AtomicReference<String> disk = new AtomicReference<>(song("valid").serialize() + "\ninvalid row");
        AtomicInteger saves = new AtomicInteger();
        MusicSession.FavoritesStore store = new MusicSession.FavoritesStore() {
            @Override public String load() { return disk.get(); }
            @Override public void save(String value) { saves.incrementAndGet(); disk.set(value); }
        };
        AtomicReference<MusicSession> session = new AtomicReference<>();
        AtomicReference<MusicSession.Snapshot> view = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            session.set(session(new MusicFavorites(store)));
            session.get().subscribe(view::set);
        });
        try {
            await(() -> !view.get().favoritesBusy());
            assertTrue(view.get().favoritesRetryable());
            assertFalse(view.get().favoritesReady());
            assertTrue(view.get().favoritesMessage().contains("加载失败"));
            SwingUtilities.invokeAndWait(() -> session.get().toggleFavorite(song("new")));
            assertEquals(0, saves.get());
            assertTrue(disk.get().endsWith("invalid row"));
            disk.set(song("valid").serialize());
            SwingUtilities.invokeAndWait(() -> session.get().retryFavorites());
            await(() -> !view.get().favoritesBusy());
            assertTrue(view.get().favoritesReady());
            assertEquals(List.of(song("valid")), view.get().favorites());
        } finally { SwingUtilities.invokeAndWait(() -> session.get().close()); }
    }

    @Test void recoveryWarningComesFromTheReadResultAndClearsAfterSaving() throws Exception {
        AtomicReference<String> disk = new AtomicReference<>(MusicSong.serializeList(List.of(song("backup"))));
        MusicSession.FavoritesStore store = new MusicSession.FavoritesStore() {
            @Override public String load() { return disk.get(); }
            @Override public ContentStore.ReadResult loadResult() {
                return new ContentStore.ReadResult(disk.get(), "Favorites recovered from backup");
            }
            @Override public void save(String value) { disk.set(value); }
        };
        AtomicReference<MusicSession> session = new AtomicReference<>();
        AtomicReference<MusicSession.Snapshot> view = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            session.set(session(new MusicFavorites(store)));
            session.get().subscribe(view::set);
        });
        try {
            await(() -> view.get().favoritesReady());
            assertEquals("Favorites recovered from backup", view.get().favoritesMessage());
            assertFalse(view.get().favoritesRetryable());
            SwingUtilities.invokeAndWait(() -> session.get().toggleFavorite(song("new")));
            await(() -> !view.get().favoritesBusy());
            assertEquals("", view.get().favoritesMessage());
            assertEquals(List.of(song("backup"), song("new")), view.get().favorites());
        } finally { SwingUtilities.invokeAndWait(() -> session.get().close()); }
    }

    @Test void failedInitialLoadDoesNotAbortAttachmentAndBlocksMutationUntilRetrySucceeds() throws Exception {
        AtomicBoolean fail = new AtomicBoolean(true);
        AtomicInteger saves = new AtomicInteger();
        AtomicReference<String> disk = new AtomicReference<>(MusicSong.serializeList(List.of(song("existing"))));
        MusicSession.FavoritesStore store = new MusicSession.FavoritesStore() {
            @Override public String load() {
                assertFalse(SwingUtilities.isEventDispatchThread());
                if (fail.get()) throw new UncheckedIOException(new IOException("unreadable fixture"));
                return disk.get();
            }
            @Override public void save(String value) {
                assertFalse(SwingUtilities.isEventDispatchThread());
                saves.incrementAndGet();
                disk.set(value);
            }
        };
        AtomicReference<MusicSession> session = new AtomicReference<>();
        AtomicReference<MusicSession.Snapshot> view = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            session.set(session(new MusicFavorites(store)));
            session.get().subscribe(view::set);
        });
        try {
            await(() -> view.get().favoritesRetryable());
            assertFalse(view.get().favoritesReady());
            assertTrue(view.get().favoritesMessage().contains("加载失败"));
            SwingUtilities.invokeAndWait(() -> session.get().toggleFavorite(song("A")));
            assertEquals(0, saves.get());
            assertTrue(view.get().favorites().isEmpty());
            fail.set(false);
            SwingUtilities.invokeAndWait(() -> session.get().retryFavorites());
            await(() -> view.get().favoritesReady());
            assertEquals(List.of(song("existing")), view.get().favorites());
            SwingUtilities.invokeAndWait(() -> session.get().toggleFavorite(song("A")));
            await(() -> !view.get().favoritesBusy());
            assertEquals(List.of(song("existing"), song("A")), MusicSong.deserializeList(disk.get()));
        } finally { SwingUtilities.invokeAndWait(() -> session.get().close()); }
    }

    @Test void slowSerializedSaveDoesNotBlockEdtAndSurvivesLastViewCloseAndReopen() throws Exception {
        CountDownLatch saveStarted = new CountDownLatch(1);
        CountDownLatch releaseSave = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        AtomicReference<String> disk = new AtomicReference<>("");
        MusicSession.FavoritesStore store = new MusicSession.FavoritesStore() {
            @Override public String load() {
                assertFalse(SwingUtilities.isEventDispatchThread());
                return disk.get();
            }
            @Override public void save(String value) {
                assertFalse(SwingUtilities.isEventDispatchThread());
                maxActive.accumulateAndGet(active.incrementAndGet(), Math::max);
                saveStarted.countDown();
                awaitUninterruptibly(releaseSave);
                disk.set(value);
                active.decrementAndGet();
            }
        };
        AtomicReference<MusicFavorites> persistence = new AtomicReference<>();
        AtomicReference<MusicSession> first = new AtomicReference<>();
        AtomicReference<MusicSession.Subscription> firstView = new AtomicReference<>();
        AtomicReference<MusicSession.Snapshot> state = new AtomicReference<>();
        AtomicReference<MusicSession> reopened = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            persistence.set(new MusicFavorites(store));
            first.set(session(persistence.get()));
            firstView.set(first.get().subscribe(state::set));
        });
        try {
            await(() -> state.get().favoritesReady());
            SwingUtilities.invokeAndWait(() -> first.get().toggleFavorite(song("A")));
            assertTrue(saveStarted.await(2, TimeUnit.SECONDS));
            CountDownLatch edtReturned = new CountDownLatch(1);
            SwingUtilities.invokeLater(() -> {
                first.get().toggleFavorite(song("B"));
                firstView.get().close();
                reopened.set(session(persistence.get()));
                reopened.get().subscribe(state::set);
                edtReturned.countDown();
            });
            assertTrue(edtReturned.await(1, TimeUnit.SECONDS), "A stalled save must not block controls or close");
            assertEquals(List.of(song("A"), song("B")), state.get().favorites());
            assertTrue(state.get().favoritesBusy());
            releaseSave.countDown();
            await(() -> !state.get().favoritesBusy());
            assertEquals(List.of(song("A"), song("B")), MusicSong.deserializeList(disk.get()));
            assertEquals(1, maxActive.get());
        } finally {
            releaseSave.countDown();
            SwingUtilities.invokeAndWait(() -> {
                first.get().close();
                if (reopened.get() != null) reopened.get().close();
            });
        }
    }

    @Test void failedSaveRetainsOptimisticEditAcrossReopenAndRetryIsIdempotent() throws Exception {
        AtomicBoolean failAfterWrite = new AtomicBoolean(true);
        AtomicReference<String> disk = new AtomicReference<>("");
        MusicSession.FavoritesStore store = new MusicSession.FavoritesStore() {
            @Override public String load() { return disk.get(); }
            @Override public void save(String value) {
                disk.set(value);
                if (failAfterWrite.getAndSet(false)) throw new UncheckedIOException(new IOException("force failed"));
            }
        };
        AtomicReference<MusicFavorites> persistence = new AtomicReference<>();
        AtomicReference<MusicSession> current = new AtomicReference<>();
        AtomicReference<MusicSession.Snapshot> view = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            persistence.set(new MusicFavorites(store));
            current.set(session(persistence.get()));
            current.get().subscribe(view::set);
        });
        try {
            await(() -> view.get().favoritesReady());
            SwingUtilities.invokeAndWait(() -> current.get().toggleFavorite(song("A")));
            await(() -> view.get().favoritesRetryable());
            assertEquals(List.of(song("A")), view.get().favorites());
            assertTrue(view.get().favoritesMessage().contains("未保存修改已保留"));
            SwingUtilities.invokeAndWait(() -> {
                current.get().close();
                current.set(session(persistence.get()));
                current.get().subscribe(view::set);
                assertTrue(view.get().favoritesRetryable());
                assertEquals(List.of(song("A")), view.get().favorites());
                current.get().retryFavorites();
            });
            await(() -> !view.get().favoritesBusy());
            assertFalse(view.get().favoritesRetryable());
            assertEquals(List.of(song("A")), MusicSong.deserializeList(disk.get()));
            assertEquals(List.of(song("A")), view.get().favorites());
        } finally { SwingUtilities.invokeAndWait(() -> current.get().close()); }
    }

    private static MusicSession session(MusicFavorites favorites) {
        return new MusicSession(new MusicApiClient(uri -> { throw new AssertionError("Offline"); }, () -> ""),
                new MusicAudioPlayer(url -> new MusicAudioPlayerTest.Frames()), favorites, url -> new ImageIcon());
    }
}
