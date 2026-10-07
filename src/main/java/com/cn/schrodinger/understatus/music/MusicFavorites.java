package com.cn.schrodinger.understatus.music;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/**
 * Serialized, asynchronous favorites persistence independent of playback/view lifetime.
 * Failed operations remain in memory and are retried as idempotent desired-state edits.
 */
final class MusicFavorites {
    record State(List<MusicSong> songs, boolean ready, boolean busy, boolean retryable, String message) {}
    private record Mutation(MusicSong song, boolean present) {}

    private final MusicSession.FavoritesStore store;
    private final ThreadPoolExecutor storage = new ThreadPoolExecutor(1, 1, 1, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(), task -> {
                Thread thread = new Thread(task, "UnderStatus-FavoritesStorage");
                thread.setDaemon(true);
                return thread;
            });
    private final List<Consumer<State>> listeners = new ArrayList<>();
    private final List<MusicSong> songs = new ArrayList<>();
    private final List<Mutation> pending = new ArrayList<>();
    private boolean ready;
    private boolean busy;
    private boolean retryable;
    private String message = "";

    MusicFavorites(MusicSession.FavoritesStore store) {
        requireEdt();
        this.store = store;
        storage.allowCoreThreadTimeOut(true);
        retry();
    }

    MusicSession.Subscription subscribe(Consumer<State> listener) {
        requireEdt();
        listeners.add(listener);
        listener.accept(snapshot());
        return () -> { requireEdt(); listeners.remove(listener); };
    }

    State snapshot() {
        requireEdt();
        return new State(List.copyOf(songs), ready, busy, retryable, message);
    }

    void toggle(MusicSong value) {
        requireEdt();
        if (!ready || value == null) return;
        MusicSong captured = copy(value);
        Mutation mutation = new Mutation(captured, !songs.contains(captured));
        pending.add(mutation);
        apply(songs, mutation);
        // Optimistic state stays available, including after a failed save.
        if (!busy) retry();
        else publish();
    }

    void retry() {
        requireEdt();
        if (busy) return;
        busy = true;
        retryable = false;
        boolean loading = !ready;
        List<Mutation> batch = List.copyOf(pending);
        message = loading ? "正在加载收藏…" : "正在保存收藏…";
        publish();
        storage.execute(() -> {
            try {
                var loaded = store.loadResult();
                List<MusicSong> fresh = decodeStored(loaded.value());
                for (Mutation mutation : batch) apply(fresh, mutation);
                if (!batch.isEmpty()) store.save(MusicSong.serializeList(fresh));
                String warning = batch.isEmpty() ? loaded.warning() : "";
                SwingUtilities.invokeLater(() -> {
                    pending.subList(0, batch.size()).clear();
                    songs.clear();
                    songs.addAll(fresh);
                    for (Mutation mutation : pending) apply(songs, mutation);
                    ready = true;
                    busy = false;
                    message = warning;
                    if (!pending.isEmpty()) retry();
                    else publish();
                });
            } catch (RuntimeException failure) {
                SwingUtilities.invokeLater(() -> {
                    busy = false;
                    retryable = true;
                    message = (loading ? "收藏加载失败，修改已禁用；请重试: "
                            : "收藏保存失败，未保存修改已保留；请重试: ") + failure.getMessage();
                    publish();
                });
            }
        });
    }

    private static List<MusicSong> decodeStored(String data) {
        List<MusicSong> result = new ArrayList<>();
        for (String row : data.split("\\R")) {
            if (row.isBlank()) continue;
            MusicSong song = MusicSong.deserialize(row.trim());
            if (song == null) throw new IllegalStateException("收藏数据格式损坏，原内容已保留");
            result.add(song);
        }
        return result;
    }

    private static void apply(List<MusicSong> target, Mutation mutation) {
        target.remove(mutation.song());
        if (mutation.present()) target.add(copy(mutation.song()));
    }

    private void publish() {
        State state = snapshot();
        for (Consumer<State> listener : List.copyOf(listeners)) listener.accept(state);
    }

    private static MusicSong copy(MusicSong value) {
        return new MusicSong(value.getId(), value.getName(), value.getArtist(), value.getAlbum(), value.getSource(),
                value.getUrl(), value.getPicUrl(), value.getLyric());
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("收藏状态必须在 EDT 使用");
    }
}
