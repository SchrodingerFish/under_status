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

    @Test
    void testParseGdstudioLyricJson() {
        String json = "{\n"
                + "    \"lyric\": \"[00:00.00] 作曲 : 陈信义\\n[00:01.00] 作词 : 娃娃\\n[00:28.90]真情像草原广阔\",\n"
                + "    \"tlyric\": \"\",\n"
                + "    \"from\": \"music.gdstudio.xyz\"\n"
                + "}";

        List<LrcParser.LrcLine> parsed = LrcParser.parse(
                new MusicApiClient().fetchLyricFromJsonForTest(json));

        assertFalse(parsed.isEmpty(), "GDStudio lyric JSON should parse correctly into LrcLine list");
        assertTrue(parsed.get(0).getText().contains("陈信义") || parsed.get(1).getText().contains("娃娃"),
                "Lyric text should be extracted properly");
    }
}
