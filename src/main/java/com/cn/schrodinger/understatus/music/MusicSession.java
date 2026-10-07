package com.cn.schrodinger.understatus.music;

import com.cn.schrodinger.understatus.settings.SettingsRepository;
import com.cn.schrodinger.understatus.settings.ContentStore;
import java.awt.Image;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;

/**
 * One EDT-confined playback session shared by every music view.
 * The last subscription closes its player and cancels outstanding work.
 */
public final class MusicSession implements AutoCloseable, MusicAudioPlayer.PlayerListener {
    public interface FavoritesStore {
        String load();
        default ContentStore.ReadResult loadResult() { return new ContentStore.ReadResult(load(), ""); }
        void save(String value);
    }
    @FunctionalInterface public interface CoverLoader { ImageIcon load(String url) throws Exception; }
    public interface Subscription extends AutoCloseable { @Override void close(); }
    public record Snapshot(MusicSong song, boolean playing, boolean paused, long positionMs,
                           List<MusicSong> playlist, List<MusicSong> favorites, PlaybackMode mode,
                           List<LrcParser.LrcLine> lyrics, ImageIcon cover, String message,
                           boolean favoritesReady, boolean favoritesBusy, boolean favoritesRetryable,
                           String favoritesMessage) {}

    private static MusicSession shared;
    private static MusicFavorites sharedFavorites;
    private final MusicApiClient api;
    private final MusicAudioPlayer player;
    private final MusicFavorites favoritesState;
    private final Subscription favoritesSubscription;
    private final CoverLoader covers;
    private final ThreadPoolExecutor worker = (ThreadPoolExecutor) Executors.newFixedThreadPool(4, r -> {
        Thread thread = new Thread(r, "UnderStatus-MusicRequest");
        thread.setDaemon(true);
        return thread;
    });
    private final List<Consumer<Snapshot>> listeners = new ArrayList<>();
    private final List<Future<?>> requests = new ArrayList<>();
    private final List<MusicSong> playlist = new ArrayList<>();
    private final List<MusicSong> favorites = new ArrayList<>();
    private final Map<String, ImageIcon> coverCache = cache(64);
    private final Map<String, List<LrcParser.LrcLine>> lyricCache = cache(128);
    private final Random random = new Random();
    private PlaybackMode mode = PlaybackMode.LIST_LOOP;
    private MusicSong song;
    private List<LrcParser.LrcLine> lyrics = List.of();
    private ImageIcon cover;
    private String message = "";
    private long selection;
    private long positionMs;
    private boolean closed;

    public static MusicSession shared() {
        requireEdt();
        if (shared == null || shared.closed) {
            if (sharedFavorites == null) {
                sharedFavorites = new MusicFavorites(new FavoritesStore() {
                    @Override public String load() { return SettingsRepository.getDefault().loadFavorites(); }
                    @Override public ContentStore.ReadResult loadResult() {
                        return SettingsRepository.getDefault().loadFavoritesResult();
                    }
                    @Override public void save(String value) { SettingsRepository.getDefault().saveFavorites(value); }
                });
            }
            shared = new MusicSession(new MusicApiClient(), new MusicAudioPlayer(), sharedFavorites, MusicSession::loadCover);
        }
        return shared;
    }

    public MusicSession(MusicApiClient api, MusicAudioPlayer player, FavoritesStore store, CoverLoader covers) {
        this(api, player, new MusicFavorites(store), covers);
    }

    MusicSession(MusicApiClient api, MusicAudioPlayer player, MusicFavorites favoritesState, CoverLoader covers) {
        requireEdt();
        this.api = api;
        this.player = player;
        this.favoritesState = favoritesState;
        this.covers = covers;
        player.setListener(this);
        favoritesSubscription = favoritesState.subscribe(state -> {
            favorites.clear();
            favorites.addAll(state.songs());
            publish();
        });
    }

    public Subscription subscribe(Consumer<Snapshot> listener) {
        requireOpen();
        listeners.add(listener);
        listener.accept(snapshot());
        return new Subscription() {
            private boolean removed;
            @Override public void close() {
                requireEdt();
                if (removed) return;
                removed = true;
                listeners.remove(listener);
                if (listeners.isEmpty()) MusicSession.this.close();
            }
        };
    }

    public Snapshot snapshot() {
        requireEdt();
        MusicFavorites.State state = favoritesState.snapshot();
        return new Snapshot(song, player.isPlaying(), player.isPaused(), positionMs,
                List.copyOf(playlist), List.copyOf(favorites), mode, lyrics, cover, message,
                state.ready(), state.busy(), state.retryable(), state.message());
    }

    public void select(MusicSong input) {
        requireOpen();
        if (input == null) return;
        // Capture every mutable song field on the EDT, before any background operation.
        MusicSong target = copy(input);
        long token = ++selection;
        cancelRequests();
        player.stop();
        song = target;
        positionMs = 0;
        cover = null;
        lyrics = List.of();
        message = "正在加载: " + target.getName();
        if (!playlist.contains(target)) playlist.add(target);
        publish();

        requests.add(worker.submit(() -> {
            try {
                String url = target.getUrl().isBlank() ? api.fetchSongUrl(target.getId(), target.getSource()) : target.getUrl();
                accept(token, () -> {
                    player.play(target, url);
                    message = "正在播放: " + target.getName();
                    publish();
                });
            } catch (Exception ex) { accept(token, () -> { message = "播放链接获取失败: " + ex.getMessage(); publish(); }); }
        }));

        String key = target.getSource() + ":" + target.getId();
        List<LrcParser.LrcLine> cachedLyrics = lyricCache.get(key);
        if (cachedLyrics != null) {
            lyrics = cachedLyrics;
            publish();
        } else {
            requests.add(worker.submit(() -> {
                try {
                    String text = target.getLyric();
                    if (text.isBlank() || !text.contains("[")) {
                        text = api.fetchLyric(text.isBlank() ? target.getId() : text, target.getSource());
                    }
                    List<LrcParser.LrcLine> parsed = List.copyOf(LrcParser.parse(text));
                    accept(token, () -> { lyricCache.put(key, parsed); lyrics = parsed; publish(); });
                } catch (Exception ex) { accept(token, () -> { lyrics = List.of(); publish(); }); }
            }));
        }

        ImageIcon cachedCover = coverCache.get(key);
        if (cachedCover != null) {
            cover = cachedCover;
            publish();
        } else {
            requests.add(worker.submit(() -> {
                try {
                    String pic = target.getPicUrl();
                    String url = api.fetchPicUrl(pic.isBlank() ? target.getId() : pic, target.getSource());
                    ImageIcon icon = url.isBlank() ? null : covers.load(url);
                    accept(token, () -> {
                        if (icon != null) coverCache.put(key, icon);
                        cover = icon;
                        publish();
                    });
                } catch (Exception ex) { accept(token, () -> { cover = null; publish(); }); }
            }));
        }
    }

    public void togglePause() { requireOpen(); player.togglePause(); }
    public void nextMode() { requireOpen(); mode = mode.next(); publish(); }
    public void addToPlaylist(MusicSong value) {
        requireOpen();
        if (!playlist.contains(value)) playlist.add(copy(value));
        publish();
    }
    public void clearPlaylist() { requireOpen(); playlist.clear(); publish(); }
    public void playFavorites() {
        requireOpen();
        playlist.clear();
        favorites.forEach(value -> playlist.add(copy(value)));
        if (!playlist.isEmpty()) select(playlist.getFirst());
        else publish();
    }

    public void toggleFavorite(MusicSong value) {
        requireOpen();
        favoritesState.toggle(value);
    }

    public void retryFavorites() { requireOpen(); favoritesState.retry(); }

    public void next(boolean automatic) {
        requireOpen();
        if (playlist.isEmpty()) return;
        int index = playlist.indexOf(song);
        if (automatic && mode == PlaybackMode.SINGLE_LOOP && index >= 0) { select(playlist.get(index)); return; }
        int next = mode == PlaybackMode.RANDOM ? random.nextInt(playlist.size()) : index + 1;
        if (next >= playlist.size()) {
            if (automatic && mode != PlaybackMode.LIST_LOOP) return;
            next = 0;
        }
        select(playlist.get(next));
    }

    public void previous() {
        requireOpen();
        if (playlist.isEmpty()) return;
        int index = mode == PlaybackMode.RANDOM ? random.nextInt(playlist.size())
                : Math.floorMod(playlist.indexOf(song) - 1, playlist.size());
        select(playlist.get(index));
    }

    private void accept(long token, Runnable result) {
        SwingUtilities.invokeLater(() -> { if (!closed && token == selection) result.run(); });
    }

    private void publish() {
        Snapshot state = snapshot();
        for (Consumer<Snapshot> listener : List.copyOf(listeners)) listener.accept(state);
    }

    private void cancelRequests() {
        requests.forEach(request -> request.cancel(true));
        requests.clear();
        worker.purge();
    }

    @Override public void close() {
        requireEdt();
        if (closed) return;
        closed = true;
        selection++;
        cancelRequests();
        worker.shutdownNow();
        player.close();
        // Persistence owns pending edits and continues independently of the closed player/views.
        favoritesSubscription.close();
        listeners.clear();
        coverCache.clear();
        lyricCache.clear();
        if (shared == this) shared = null;
    }

    @Override public void onStatusChanged(boolean playing, MusicSong current) {
        if (!closed && current != null && current == song) {
            positionMs = player.getPositionMs();
            publish();
        }
    }
    @Override public void onProgress(long currentMs) {
        if (!closed) { positionMs = currentMs; publish(); }
    }
    @Override public void onSongFinished(MusicSong finished) {
        if (!closed && song == finished) next(true);
    }
    @Override public void onError(String error) {
        if (!closed) { message = error; publish(); }
    }

    private void requireOpen() { requireEdt(); if (closed) throw new IllegalStateException("音乐会话已关闭"); }
    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("音乐会话必须在 EDT 使用");
    }
    private static MusicSong copy(MusicSong value) {
        return new MusicSong(value.getId(), value.getName(), value.getArtist(), value.getAlbum(), value.getSource(),
                value.getUrl(), value.getPicUrl(), value.getLyric());
    }
    private static <T> Map<String, T> cache(int limit) {
        return new LinkedHashMap<>(16, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, T> eldest) { return size() > limit; }
        };
    }

    private static ImageIcon loadCover(String url) throws Exception {
        byte[] data = MusicHttp.get(URI.create(url), 4 * 1024 * 1024);
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("封面格式不支持");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                if (reader.getWidth(0) > 2048 || reader.getHeight(0) > 2048) throw new IOException("封面尺寸过大");
                Image image = reader.read(0).getScaledInstance(42, 42, Image.SCALE_SMOOTH);
                return new ImageIcon(image);
            } finally { reader.dispose(); }
        }
    }
}
