package com.cn.schrodinger.understatus.music;

import com.cn.schrodinger.understatus.core.JsonSupport;
import com.cn.schrodinger.understatus.settings.SettingsRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

/** Bounded music API client. Provider fallback never changes the requested source. */
public class MusicApiClient {
    public static final String DEFAULT_API_URL = "https://music-api.gdstudio.xyz/api.php";
    private static final String NETEASE = "https://music.163.com";

    @FunctionalInterface
    public interface Transport {
        String get(URI uri) throws Exception;
    }

    private final Transport transport;
    private final Supplier<String> host;

    public MusicApiClient() {
        this(uri -> new String(MusicHttp.get(uri, JsonSupport.MAX_INPUT_LENGTH), StandardCharsets.UTF_8),
                () -> SettingsRepository.getDefault().loadMusicApiHost());
    }

    public MusicApiClient(Transport transport, Supplier<String> host) {
        this.transport = Objects.requireNonNull(transport);
        this.host = Objects.requireNonNull(host);
    }

    public List<MusicSong> search(String keyword, String source, int count) throws Exception {
        if (keyword == null || keyword.isBlank()) return List.of();
        String provider = source(source);
        int limit = Math.max(1, Math.min(50, count));
        try {
            JsonNode root = get(api("types=search&name=" + encode(keyword.trim())
                    + "&source=" + encode(provider) + "&count=" + limit));
            if (!root.isArray()) throw new IOException("音乐搜索响应不是数组");
            return parseSongs(root, provider, false, limit);
        } catch (Exception primary) {
            if (!"netease".equals(provider) || Thread.currentThread().isInterrupted()) throw primary;
            try {
                JsonNode root = get(URI.create(NETEASE + "/api/search/get/web?s=" + encode(keyword.trim())
                        + "&type=1&offset=0&total=true&limit=" + limit));
                JsonNode songs = root.path("result").path("songs");
                if (songs.isMissingNode() && root.path("result").path("songCount").asInt(-1) == 0) return List.of();
                if (!songs.isArray()) throw new IOException("网易云搜索响应无效");
                return parseSongs(songs, provider, true, limit);
            } catch (Exception fallback) {
                fallback.addSuppressed(primary);
                throw fallback;
            }
        }
    }

    private List<MusicSong> parseSongs(JsonNode array, String provider, boolean direct, int limit) {
        List<MusicSong> songs = new ArrayList<>();
        for (JsonNode item : array) {
            String id = item.path("id").asText("");
            String name = item.path("name").asText("");
            if (id.isBlank() || name.isBlank()) continue;
            JsonNode album = item.path("album");
            String artist = artists(item.path(direct ? "artists" : "artist"));
            songs.add(new MusicSong(id, name, artist,
                    album.isObject() ? album.path("name").asText("") : album.asText(""), provider,
                    direct ? directAudio(id) : item.path("url").asText(""),
                    direct ? album.path("picUrl").asText("") : item.path("pic_id").asText(""),
                    item.path("lyric_id").asText("")));
            if (songs.size() >= limit) break;
        }
        return songs;
    }

    private String artists(JsonNode value) {
        if (!value.isArray()) return value.asText("");
        List<String> names = new ArrayList<>();
        for (JsonNode artist : value) names.add(artist.isObject() ? artist.path("name").asText("") : artist.asText(""));
        return String.join(" / ", names);
    }

    public String fetchSongUrl(String id, String source) throws Exception {
        if (id == null || id.isBlank()) return "";
        String provider = source(source);
        if ("netease".equals(provider)) return directAudio(id);
        return httpUrl(get(api("types=url&id=" + encode(id) + "&source=" + encode(provider) + "&br=320"))
                .path("url").asText(""));
    }

    public String fetchPicUrl(String id, String source) throws Exception {
        if (id == null || id.isBlank()) return "";
        if (id.startsWith("https://") || id.startsWith("http://")) return httpUrl(id);
        String provider = source(source);
        try {
            return httpUrl(get(api("types=pic&id=" + encode(id) + "&source=" + encode(provider)))
                    .path("url").asText(""));
        } catch (Exception primary) {
            if (!"netease".equals(provider) || Thread.currentThread().isInterrupted()) throw primary;
            JsonNode root = get(URI.create(NETEASE + "/api/song/detail/?id=" + encode(id)
                    + "&ids=" + encode("[" + id + "]")));
            return httpUrl(root.path("songs").path(0).path("album").path("picUrl").asText(""));
        }
    }

    public String fetchLyric(String id, String source) throws Exception {
        if (id == null || id.isBlank()) return "";
        String provider = source(source);
        try {
            return lyric(get(api("types=lyric&id=" + encode(id) + "&source=" + encode(provider))));
        } catch (Exception primary) {
            if (!"netease".equals(provider) || Thread.currentThread().isInterrupted()) throw primary;
            return lyric(get(URI.create(NETEASE + "/api/song/lyric?id=" + encode(id) + "&lv=-1&kv=-1&tv=-1")));
        }
    }

    String fetchLyricFromJsonForTest(String json) { return lyric(JsonSupport.parse(json)); }

    private String lyric(JsonNode root) {
        if (!root.isObject()) throw new IllegalArgumentException("歌词响应无效");
        if (root.has("lyric")) return root.path("lyric").asText("");
        if (root.has("lrc")) return root.path("lrc").path("lyric").asText("");
        if (root.path("nolyric").asBoolean(false) || root.path("uncollected").asBoolean(false)) return "";
        throw new IllegalArgumentException("歌词响应缺少歌词字段");
    }

    private JsonNode get(URI uri) throws Exception {
        MusicHttp.validate(uri);
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        return JsonSupport.parse(transport.get(uri));
    }

    private URI api(String query) {
        String configured = host.get();
        String base = configured == null || configured.isBlank() ? DEFAULT_API_URL : configured.trim();
        URI uri = URI.create(base);
        MusicHttp.validate(uri);
        if (uri.getFragment() != null) throw new IllegalArgumentException("音乐 API 地址不能含片段");
        return URI.create(base + (uri.getRawQuery() == null ? "?" : "&") + query);
    }

    private static String source(String value) { return value == null || value.isBlank() ? "netease" : value.toLowerCase(Locale.ROOT); }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String directAudio(String id) { return NETEASE + "/song/media/outer/url?id=" + encode(id) + ".mp3"; }
    private static String httpUrl(String value) {
        if (!value.isBlank()) MusicHttp.validate(URI.create(value));
        return value;
    }
}
