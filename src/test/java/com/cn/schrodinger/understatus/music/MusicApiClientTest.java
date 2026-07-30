package com.cn.schrodinger.understatus.music;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
}
