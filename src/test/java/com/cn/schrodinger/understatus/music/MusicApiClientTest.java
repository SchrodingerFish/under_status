package com.cn.schrodinger.understatus.music;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class MusicApiClientTest {

    @Test
    void testDirectNetEaseSearchFallback() throws Exception {
        MusicApiClient client = new MusicApiClient();
        List<MusicSong> songs = client.search("海阔天空", "netease", 5);

        assertNotNull(songs);
        assertFalse(songs.isEmpty(), "Search results should not be empty using NetEase fallback");
        assertNotNull(songs.get(0).getName());
        assertNotNull(songs.get(0).getUrl());
    }

    @Test
    void testFetchLyric() throws Exception {
        MusicApiClient client = new MusicApiClient();
        String lyric = client.fetchLyric("1357375695", "netease");

        assertNotNull(lyric);
        assertFalse(lyric.isBlank(), "Lyric should not be blank for valid NetEase song ID");
        assertTrue(lyric.contains("海阔天空") || lyric.contains("黄家驹") || lyric.contains("[00:"),
                "Lyric should contain LRC timestamps or text content");
    }
}
