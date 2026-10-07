package com.cn.schrodinger.understatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cn.schrodinger.understatus.music.MusicApiClient;
import com.cn.schrodinger.understatus.music.MusicAudioPlayer;
import com.cn.schrodinger.understatus.music.MusicSong;
import com.cn.schrodinger.understatus.settings.SettingsRepository;
import com.cn.schrodinger.understatus.settings.SettingsStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class MusicTabPanelTest {
    @Test void delayedUrlResolutionCannotOverrideNewSongOrRestartClosedPlayer() throws Exception {
        List<Runnable> queued = new ArrayList<>();
        FakePlayer player = new FakePlayer();
        MusicTabPanel[] panel = new MusicTabPanel[1];
        MusicSong a = song("A"), b = song("B");
        SwingUtilities.invokeAndWait(() -> {
            panel[0] = new MusicTabPanel(new FakeApi(), player,
                    new SettingsRepository(SettingsStore.inMemory(new HashMap<>())), queued::add);
            panel[0].playSong(a);
            panel[0].playSong(b);
        });
        try {
            queued.get(3).run(); // B URL completes before A URL.
            queued.get(0).run();
            SwingUtilities.invokeAndWait(() -> {});
            assertEquals(List.of("B"), player.played);
            SwingUtilities.invokeAndWait(() -> { panel[0].playSong(song("C")); panel[0].close(); });
            queued.get(6).run();
            SwingUtilities.invokeAndWait(() -> {});
            assertEquals(List.of("B"), player.played);
            assertTrue(player.closed);
        } finally { SwingUtilities.invokeAndWait(panel[0]::close); }
    }

    private static MusicSong song(String id) { return new MusicSong(id, id, "artist", "album", "netease", "", "", ""); }

    private static final class FakeApi extends MusicApiClient {
        @Override public String fetchSongUrl(String id, String source) { return "https://example.invalid/" + id; }
        @Override public String fetchPicUrl(String id, String source) { return ""; }
        @Override public String fetchLyric(String id, String source) { return ""; }
    }

    private static final class FakePlayer extends MusicAudioPlayer {
        final List<String> played = new ArrayList<>();
        boolean closed;
        @Override public synchronized void play(MusicSong song, String url) { played.add(song.getId()); }
        @Override public synchronized void stop() {}
        @Override public synchronized void close() { closed = true; super.close(); }
    }
}
