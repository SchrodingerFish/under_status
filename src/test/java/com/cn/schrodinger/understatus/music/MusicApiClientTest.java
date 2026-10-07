package com.cn.schrodinger.understatus.music;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicApiClientTest {
    @Test void parsesEscapesNestedObjectsAndUnicodeWithoutStringScanning() throws Exception {
        MusicApiClient client = new MusicApiClient(uri -> """
                [{"id":"42","name":"quote \\" [bracket] 雪 ☃","artist":["A", "B"],
                  "album":"{record}", "pic_id":"cover", "lyric_id":"lyric"}]
                """, () -> "https://fixture.invalid/api?token=x");
        MusicSong song = client.search("雪", "tencent", 5).getFirst();
        assertEquals("quote \" [bracket] 雪 ☃", song.getName());
        assertEquals("A / B", song.getArtist());
        assertEquals("tencent", song.getSource());
        assertEquals("cover", song.getPicUrl());
    }

    @Test void directFallbackOnlyRunsAfterNeteaseFailure() throws Exception {
        List<URI> requests = new ArrayList<>();
        MusicApiClient client = new MusicApiClient(uri -> {
            requests.add(uri);
            if (uri.getHost().equals("fixture.invalid")) throw new IOException("HTTP 403");
            return """
                    {"result":{"songs":[{"id":99,"name":"N","artists":[{"name":"first"},{"name":"second"}],
                    "album":{"id":5,"name":"album [x]","picUrl":"https://cover.invalid/99"}}]}}
                    """;
        }, () -> "https://fixture.invalid/api");
        MusicSong song = client.search("N", "netease", 5).getFirst();
        assertEquals(2, requests.size());
        assertEquals("99", song.getId());
        assertEquals("first / second", song.getArtist());
        assertEquals("album [x]", song.getAlbum());
        assertTrue(song.getUrl().contains("id=99.mp3"));
    }

    @Test void otherSourceFailureIsVisibleAndNeverFallsBack() {
        List<URI> requests = new ArrayList<>();
        MusicApiClient client = new MusicApiClient(uri -> {
            requests.add(uri);
            throw new IOException("provider unavailable");
        }, () -> "https://fixture.invalid/api");
        assertThrows(IOException.class, () -> client.search("track", "tencent", 10));
        assertEquals(1, requests.size());
        assertEquals("fixture.invalid", requests.getFirst().getHost());
    }

    @Test void emptySearchDoesNotTriggerFallbackAndCountAndKeywordAreEncoded() throws Exception {
        List<URI> requests = new ArrayList<>();
        MusicApiClient client = new MusicApiClient(uri -> { requests.add(uri); return "[]"; },
                () -> "https://fixture.invalid/api?existing=1");
        assertTrue(client.search("A & B", "netease", 500).isEmpty());
        assertEquals(1, requests.size());
        assertTrue(requests.getFirst().toString().contains("existing=1&types=search"));
        assertTrue(requests.getFirst().toString().contains("name=A+%26+B"));
        assertTrue(requests.getFirst().toString().contains("count=50"));
    }

    @Test void numericTencentIdentifiersStayWithTencentForUrlCoverAndLyric() throws Exception {
        List<URI> requests = new ArrayList<>();
        MusicApiClient client = new MusicApiClient(uri -> {
            requests.add(uri);
            return uri.toString().contains("types=lyric") ? "{\"lyric\":\"[00:01.00]line\\n[00:02.00]雪\"}"
                    : "{\"url\":\"https://tencent.invalid/123\"}";
        }, () -> "https://fixture.invalid/api");
        assertEquals("https://tencent.invalid/123", client.fetchSongUrl("123", "tencent"));
        assertEquals("https://tencent.invalid/123", client.fetchPicUrl("123", "tencent"));
        assertTrue(client.fetchLyric("123", "tencent").contains("\n"));
        assertEquals(3, requests.size());
        assertTrue(requests.stream().allMatch(uri -> uri.getHost().equals("fixture.invalid")));
    }

    @Test void lyricFallbackUsesStructuredLrcObject() throws Exception {
        MusicApiClient client = new MusicApiClient(uri -> {
            if (uri.getHost().equals("fixture.invalid")) return "<html>blocked</html>";
            return "{\"lrc\":{\"version\":1,\"lyric\":\"[00:01.00]歌词\\n[00:02.00]下一行\"}}";
        }, () -> "https://fixture.invalid/api");
        assertEquals(2, LrcParser.parse(client.fetchLyric("123", "netease")).size());
    }

    @Test void invalidAndOversizedResponsesFailAndRetainFallbackCause() {
        MusicApiClient malformed = new MusicApiClient(uri -> "{\"unrelated\":[]}", () -> "https://fixture.invalid");
        Exception error = assertThrows(Exception.class, () -> malformed.search("x", "netease", 1));
        assertEquals(1, error.getSuppressed().length);
        MusicApiClient oversized = new MusicApiClient(uri -> " ".repeat(1_000_001) + "[]", () -> "https://fixture.invalid");
        assertThrows(IllegalArgumentException.class, () -> oversized.search("x", "tencent", 1));
    }

    @Test void lyricJsonFixtureParsesWithoutAnyNetwork() {
        MusicApiClient client = new MusicApiClient(uri -> { throw new AssertionError("No network expected"); }, () -> "");
        assertFalse(LrcParser.parse(client.fetchLyricFromJsonForTest(
                "{\"lyric\":\"[00:00.00]作曲 : 陈信义\\n[00:01.00]作词 : 娃娃\"}")).isEmpty());
    }
}
