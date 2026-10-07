package com.cn.schrodinger.understatus.music;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicSessionTest {
    static final class Store implements MusicSession.FavoritesStore {
        volatile String value = "";
        @Override public String load() { return value; }
        @Override public void save(String data) { value = data; }
    }

    @Test void slowSelectionCannotReplaceFastUrlCoverOrLyrics() throws Exception { selectionRace(false); }
    @Test void staleErrorsCannotReplaceNewSelectionStatus() throws Exception { selectionRace(true); }

    private void selectionRace(boolean failOld) throws Exception {
        CountDownLatch oldRequestsStarted = new CountDownLatch(3);
        CountDownLatch releaseOld = new CountDownLatch(1);
        AtomicInteger oldReturned = new AtomicInteger();
        MusicApiClient api = new MusicApiClient(uri -> { throw new AssertionError("Offline"); }, () -> "") {
            private void gate(String id) throws Exception {
                if ("A".equals(id)) {
                    oldRequestsStarted.countDown();
                    awaitUninterruptibly(releaseOld);
                    oldReturned.incrementAndGet();
                    if (failOld) throw new IOException("old request failed");
                }
            }
            @Override public String fetchSongUrl(String id, String source) throws Exception {
                gate(id);
                return "https://audio.invalid/" + id;
            }
            @Override public String fetchPicUrl(String id, String source) throws Exception {
                gate(id);
                return "https://cover.invalid/" + id;
            }
            @Override public String fetchLyric(String id, String source) throws Exception {
                gate(id);
                return "[00:00.00]lyric " + id;
            }
        };
        List<String> opened = new CopyOnWriteArrayList<>();
        MusicAudioPlayerTest.Frames backend = new MusicAudioPlayerTest.Frames();
        ImageIcon coverB = new ImageIcon();
        MusicAudioPlayer player = new MusicAudioPlayer(url -> { opened.add(url); return backend; });
        AtomicReference<MusicSession> session = new AtomicReference<>();
        AtomicReference<MusicSession.Snapshot> latest = new AtomicReference<>();
        CountDownLatch stalePublished = new CountDownLatch(1);
        SwingUtilities.invokeAndWait(() -> {
            session.set(new MusicSession(api, player, new Store(), url -> url.endsWith("/B") ? coverB : new ImageIcon()));
            session.get().subscribe(state -> {
                latest.set(state);
                if (state.message().contains("old request failed")
                        || (!state.lyrics().isEmpty() && state.song().getId().equals("B")
                        && state.lyrics().getFirst().getText().contains("lyric A"))) stalePublished.countDown();
            });
            session.get().select(song("A"));
        });
        try {
            assertTrue(oldRequestsStarted.await(2, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(() -> {
                MusicSong selected = song("B");
                session.get().select(selected);
                selected.setId("mutated-after-selection");
            });
            await(() -> latest.get() != null && latest.get().cover() == coverB
                    && !latest.get().lyrics().isEmpty() && player.isPlaying());
            releaseOld.countDown();
            await(() -> oldReturned.get() == 3);
            assertFalse(stalePublished.await(200, TimeUnit.MILLISECONDS));
            SwingUtilities.invokeAndWait(() -> {
                assertEquals("B", latest.get().song().getId());
                assertEquals("lyric B", latest.get().lyrics().getFirst().getText());
                assertEquals(coverB, latest.get().cover());
                assertTrue(latest.get().message().contains("B"));
            });
            assertEquals(List.of("https://audio.invalid/B"), opened);
        } finally {
            releaseOld.countDown();
            SwingUtilities.invokeAndWait(() -> session.get().close());
        }
    }

    @Test void twoViewsShareControlsCollectionsAndLastCloseReleasesPlayer() throws Exception {
        Store store = new Store();
        MusicAudioPlayerTest.Frames backend = new MusicAudioPlayerTest.Frames();
        MusicAudioPlayer player = new MusicAudioPlayer(url -> backend);
        MusicApiClient api = immediateApi();
        AtomicReference<MusicSession> session = new AtomicReference<>();
        AtomicReference<MusicSession.Subscription> first = new AtomicReference<>();
        AtomicReference<MusicSession.Subscription> second = new AtomicReference<>();
        AtomicReference<MusicSession.Snapshot> firstView = new AtomicReference<>();
        AtomicReference<MusicSession.Snapshot> secondView = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            session.set(new MusicSession(api, player, store, url -> new ImageIcon()));
            first.set(session.get().subscribe(firstView::set));
            second.set(session.get().subscribe(secondView::set));
            session.get().select(song("A"));
            session.get().addToPlaylist(song("B"));
            session.get().nextMode();
            assertEquals(firstView.get(), secondView.get());
            assertEquals(2, secondView.get().playlist().size());
        });
        try {
            await(() -> secondView.get().favoritesReady());
            SwingUtilities.invokeAndWait(() -> session.get().toggleFavorite(song("A")));
            await(() -> !secondView.get().favoritesBusy());
            // An independent persistent edit must survive the next queued mutation.
            store.value = MusicSong.serializeList(List.of(song("A"), song("external")));
            SwingUtilities.invokeAndWait(() -> session.get().toggleFavorite(song("B")));
            await(() -> !secondView.get().favoritesBusy());
            assertEquals(3, secondView.get().favorites().size());
            assertEquals(firstView.get(), secondView.get());
            await(player::isPlaying);
            SwingUtilities.invokeAndWait(() -> session.get().togglePause());
            await(() -> secondView.get().paused());
            assertTrue(firstView.get().paused());
            SwingUtilities.invokeAndWait(() -> {
                first.get().close();
                session.get().togglePause();
            });
            await(player::isPlaying);
            assertFalse(backend.closed.await(100, TimeUnit.MILLISECONDS), "Other view still owns playback");
            SwingUtilities.invokeAndWait(() -> second.get().close());
            assertTrue(backend.closed.await(2, TimeUnit.SECONDS));
            assertFalse(player.isPlaying());
        } finally { SwingUtilities.invokeAndWait(() -> session.get().close()); }
    }

    @Test void closingLastViewCancelsPendingSelectionAndReopenStartsFresh() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger opens = new AtomicInteger();
        MusicApiClient api = new MusicApiClient(uri -> "", () -> "") {
            @Override public String fetchSongUrl(String id, String source) {
                started.countDown();
                awaitUninterruptibly(release);
                return "https://audio.invalid/" + id;
            }
            @Override public String fetchPicUrl(String id, String source) { return ""; }
            @Override public String fetchLyric(String id, String source) { return ""; }
        };
        AtomicReference<MusicSession.Subscription> view = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            MusicSession session = new MusicSession(api,
                    new MusicAudioPlayer(url -> { opens.incrementAndGet(); return new MusicAudioPlayerTest.Frames(); }),
                    new Store(), url -> new ImageIcon());
            view.set(session.subscribe(state -> {}));
            session.select(song("A"));
        });
        assertTrue(started.await(2, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> view.get().close());
        release.countDown();
        SwingUtilities.invokeAndWait(() -> {
            MusicSession reopened = new MusicSession(immediateApi(), new MusicAudioPlayer(url ->
                    new MusicAudioPlayerTest.Frames()), new Store(), url -> new ImageIcon());
            assertNotNull(reopened);
            assertEquals(null, reopened.snapshot().song());
            reopened.close();
        });
        assertEquals(0, opens.get());
    }

    private static MusicApiClient immediateApi() {
        return new MusicApiClient(uri -> { throw new AssertionError("Offline"); }, () -> "") {
            @Override public String fetchSongUrl(String id, String source) { return "https://audio.invalid/" + id; }
            @Override public String fetchPicUrl(String id, String source) { return "https://cover.invalid/" + id; }
            @Override public String fetchLyric(String id, String source) { return "[00:00.00]lyric " + id; }
        };
    }
}
