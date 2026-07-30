package com.cn.schrodinger.understatus.music;

import com.cn.schrodinger.understatus.settings.SettingsRepository;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Client for fetching music search results, audio stream URLs, album covers, and lyrics.
 * Supports GDStudio API with automatic fallback to direct NetEase Cloud Music API to bypass Cloudflare 403 blocks.
 */
public class MusicApiClient {

    private static final Logger LOGGER = Logger.getLogger(MusicApiClient.class.getName());

    public static final String DEFAULT_API_URL = "https://music-api.gdstudio.xyz/api.php";
    private static final String DIRECT_NETEASE_SEARCH_URL = "https://music.163.com/api/search/get/web";
    private static final String DIRECT_NETEASE_LYRIC_URL = "https://music.163.com/api/song/lyric";
    private static final String DIRECT_NETEASE_PLAY_URL = "https://music.163.com/song/media/outer/url?id=";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
    private static final int TIMEOUT_MS = 4000;

    public MusicApiClient() {
    }

    private String getApiUrl() {
        String configured = SettingsRepository.getDefault().load().musicApiHost();
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        return DEFAULT_API_URL;
    }

    /**
     * Search songs by keyword and source.
     */
    public List<MusicSong> search(String keyword, String source, int count) throws Exception {
        if (keyword == null || keyword.isBlank()) {
            return new ArrayList<>();
        }
        String sourceParam = (source == null || source.isBlank()) ? "netease" : source.toLowerCase();

        // Strategy 1: Try configured / GDStudio API
        try {
            String encodedKw = URLEncoder.encode(keyword.trim(), StandardCharsets.UTF_8);
            String targetUrl = getApiUrl() + "?types=search&name=" + encodedKw + "&source=" + sourceParam
                    + "&count=" + Math.max(1, Math.min(50, count));

            String json = executeGet(targetUrl);
            if (json.startsWith("[") && json.contains("id")) {
                List<MusicSong> list = parseSearchResult(json, sourceParam);
                if (!list.isEmpty()) {
                    return list;
                }
            }
        } catch (Exception ex) {
            LOGGER.log(Level.INFO, "GDStudio API search failed, falling back to direct NetEase API: {0}", ex.getMessage());
        }

        // Strategy 2: Fallback to direct NetEase Cloud Music API
        return searchDirectNetEase(keyword, count);
    }

    /**
     * Direct NetEase Cloud Music search API fallback.
     */
    private List<MusicSong> searchDirectNetEase(String keyword, int count) throws Exception {
        String encodedKw = URLEncoder.encode(keyword.trim(), StandardCharsets.UTF_8);
        String targetUrl = DIRECT_NETEASE_SEARCH_URL + "?csrf_token=&hlpretag=&hlposttag=&s="
                + encodedKw + "&type=1&offset=0&total=true&limit=" + Math.max(1, Math.min(50, count));

        String json = executeGet(targetUrl);
        List<MusicSong> list = new ArrayList<>();

        if (json == null || json.isBlank() || !json.contains("\"songs\"")) {
            return list;
        }

        int songsIdx = json.indexOf("\"songs\"");
        if (songsIdx == -1) {
            return list;
        }
        int arrStart = json.indexOf('[', songsIdx);
        if (arrStart == -1) {
            return list;
        }

        int depth = 0;
        int arrEnd = -1;
        for (int i = arrStart; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) {
                    arrEnd = i;
                    break;
                }
            }
        }

        if (arrEnd == -1) {
            return list;
        }

        String songsArrayJson = json.substring(arrStart, arrEnd + 1);
        List<String> objects = splitJsonObjectsInArray(songsArrayJson);

        for (String objJson : objects) {
            try {
                String id = extractTopLevelJsonField(objJson, "id");
                String name = extractTopLevelJsonField(objJson, "name");
                String artist = extractNetEaseArtists(objJson);
                String album = extractNetEaseAlbum(objJson);
                String url = DIRECT_NETEASE_PLAY_URL + id + ".mp3";

                if (!id.isBlank() && !name.isBlank()) {
                    MusicSong song = new MusicSong(id, name, artist, album, "netease", url, "", "");
                    list.add(song);
                }
            } catch (Exception ex) {
                LOGGER.log(Level.FINE, "Failed to parse NetEase song item", ex);
            }
        }
        return list;
    }

    /**
     * Resolve direct audio playback URL for a song.
     */
    public String fetchSongUrl(String songId, String source) throws Exception {
        if (songId == null || songId.isBlank()) {
            return "";
        }
        String sourceParam = (source == null || source.isBlank()) ? "netease" : source.toLowerCase();

        // Instant direct NetEase audio stream URL (Zero network delay)
        if ("netease".equals(sourceParam) || isNumeric(songId)) {
            return DIRECT_NETEASE_PLAY_URL + songId + ".mp3";
        }

        // Try GDStudio API for non-NetEase sources
        try {
            String targetUrl = getApiUrl() + "?types=url&id=" + URLEncoder.encode(songId, StandardCharsets.UTF_8)
                    + "&source=" + sourceParam + "&br=320";
            String json = executeGet(targetUrl);
            String url = parseSingleUrlField(json, "url");
            if (url.startsWith("http")) {
                return url;
            }
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "GDStudio fetchSongUrl failed", ex);
        }

        return "";
    }

    /**
     * Resolve album picture cover URL for a song.
     */
    public String fetchPicUrl(String picIdOrSongId, String source) throws Exception {
        if (picIdOrSongId == null || picIdOrSongId.isBlank()) {
            return "";
        }
        if (picIdOrSongId.startsWith("http")) {
            return picIdOrSongId;
        }
        String sourceParam = (source == null || source.isBlank()) ? "netease" : source.toLowerCase();

        try {
            String targetUrl = getApiUrl() + "?types=pic&id=" + URLEncoder.encode(picIdOrSongId, StandardCharsets.UTF_8)
                    + "&source=" + sourceParam;
            String json = executeGet(targetUrl);
            return parseSingleUrlField(json, "url");
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "GDStudio fetchPicUrl failed", ex);
            return "";
        }
    }

    /**
     * Resolve LRC lyrics content for a song.
     */
    public String fetchLyric(String lyricIdOrSongId, String source) throws Exception {
        if (lyricIdOrSongId == null || lyricIdOrSongId.isBlank()) {
            return "";
        }
        String sourceParam = (source == null || source.isBlank()) ? "netease" : source.toLowerCase();

        // Fast path: Direct NetEase Lyric API
        if ("netease".equals(sourceParam) || isNumeric(lyricIdOrSongId)) {
            try {
                String targetUrl = DIRECT_NETEASE_LYRIC_URL + "?id=" + URLEncoder.encode(lyricIdOrSongId, StandardCharsets.UTF_8)
                        + "&lv=1&kv=1&tv=-1";
                String json = executeGet(targetUrl);
                String parsed = extractNestedLyricField(json);
                if (!parsed.isBlank()) {
                    return parsed;
                }
            } catch (Exception ex) {
                LOGGER.log(Level.FINE, "Direct NetEase fetchLyric failed, trying GDStudio API", ex);
            }
        }

        // Try GDStudio / Meting API
        try {
            String targetUrl = getApiUrl() + "?types=lrc&id=" + URLEncoder.encode(lyricIdOrSongId, StandardCharsets.UTF_8)
                    + "&source=" + sourceParam;
            String response = executeGet(targetUrl);
            String parsed = extractNestedLyricField(response);
            if (!parsed.isBlank()) {
                return parsed;
            }
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "GDStudio fetchLyric failed", ex);
        }

        return "";
    }

    private String executeGet(String urlStr) throws Exception {
        String currentUrl = urlStr;
        for (int attempt = 0; attempt < 5; attempt++) {
            URL url = URI.create(currentUrl).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Accept", "application/json, text/plain, */*");
            conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            String referer = currentUrl.contains("music.163.com") ? "https://music.163.com/" : getApiUrl();
            conn.setRequestProperty("Referer", referer);
            conn.setInstanceFollowRedirects(true);

            int code = conn.getResponseCode();
            if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP
                    || code == 307 || code == 308) {
                String redirectUrl = conn.getHeaderField("Location");
                if (redirectUrl != null && !redirectUrl.isBlank()) {
                    currentUrl = redirectUrl.startsWith("http") ? redirectUrl : getApiUrl() + redirectUrl;
                    continue;
                }
            }

            if (code >= 400) {
                InputStream err = conn.getErrorStream();
                String msg = err != null ? readStream(err) : "HTTP " + code;
                throw new Exception("Music API error (" + code + "): " + msg);
            }

            try (InputStream in = conn.getInputStream()) {
                return readStream(in);
            }
        }
        throw new Exception("Too many redirects for " + urlStr);
    }

    private String readStream(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private List<MusicSong> parseSearchResult(String json, String defaultSource) {
        List<MusicSong> list = new ArrayList<>();
        if (json == null || json.isBlank() || !json.startsWith("[")) {
            return list;
        }

        List<String> objects = splitJsonObjectsInArray(json);
        for (String objJson : objects) {
            try {
                String id = extractJsonField(objJson, "id");
                String name = extractJsonField(objJson, "name");
                String artist = extractArtistField(objJson);
                String album = extractJsonField(objJson, "album");
                String lyricId = extractJsonField(objJson, "lyric_id");
                if (lyricId.isBlank()) {
                    lyricId = id;
                }

                String url = extractJsonField(objJson, "url");
                String picUrl = extractJsonField(objJson, "pic");

                String songSource = extractJsonField(objJson, "source");
                if (songSource.isBlank()) {
                    songSource = defaultSource;
                }

                if (!id.isBlank() || !name.isBlank()) {
                    MusicSong song = new MusicSong(id, name, artist, album, songSource, url, picUrl, "");
                    list.add(song);
                }
            } catch (Exception ex) {
                LOGGER.log(Level.FINE, "Could not parse song item JSON", ex);
            }
        }
        return list;
    }

    private String parseSingleUrlField(String json, String fieldName) {
        if (json == null || json.isBlank()) {
            return "";
        }
        if (!json.startsWith("{")) {
            return json.trim();
        }
        return extractJsonField(json, fieldName);
    }

    String fetchLyricFromJsonForTest(String json) {
        return extractNestedLyricField(json);
    }

    private String extractNestedLyricField(String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        String trimmed = json.trim();
        if (trimmed.startsWith("[")) {
            return trimmed;
        }

        int lrcIdx = trimmed.indexOf("\"lrc\":");
        if (lrcIdx == -1) {
            lrcIdx = trimmed.indexOf("\"lrc\" :");
        }
        if (lrcIdx != -1) {
            String val = extractJsonField(trimmed.substring(lrcIdx), "lyric");
            if (!val.isBlank()) {
                return val;
            }
        }

        if (trimmed.contains("\"lyric\"")) {
            return extractJsonField(trimmed, "lyric");
        }

        return "";
    }

    private List<String> splitJsonObjectsInArray(String json) {
        List<String> list = new ArrayList<>();
        int depth = 0;
        int start = -1;
        boolean inString = false;
        boolean escape = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\' && inString) {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }

            if (c == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start != -1) {
                    list.add(json.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return list;
    }

    private String extractJsonField(String json, String fieldName) {
        String pattern = "\"" + fieldName + "\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) {
            return "";
        }
        int colonIdx = json.indexOf(':', idx + pattern.length());
        if (colonIdx == -1) {
            return "";
        }
        int startVal = colonIdx + 1;
        while (startVal < json.length() && Character.isWhitespace(json.charAt(startVal))) {
            startVal++;
        }
        if (startVal >= json.length()) {
            return "";
        }

        if (json.charAt(startVal) == '"') {
            StringBuilder sb = new StringBuilder();
            boolean escape = false;
            for (int i = startVal + 1; i < json.length(); i++) {
                char c = json.charAt(i);
                if (escape) {
                    if (c == 'n') sb.append('\n');
                    else if (c == 't') sb.append('\t');
                    else if (c == 'r') sb.append('\r');
                    else if (c == 'u' && i + 4 < json.length()) {
                        try {
                            int code = Integer.parseInt(json.substring(i + 1, i + 5), 16);
                            sb.append((char) code);
                            i += 4;
                        } catch (Exception ex) {
                            sb.append(c);
                        }
                    } else {
                        sb.append(c);
                    }
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    break;
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        } else {
            int endVal = startVal;
            while (endVal < json.length() && json.charAt(endVal) != ',' && json.charAt(endVal) != '}'
                    && json.charAt(endVal) != ']') {
                endVal++;
            }
            return json.substring(startVal, endVal).trim().replaceAll("^\"|\"$", "");
        }
    }

    private String extractArtistField(String json) {
        String pattern = "\"artist\"";
        int idx = json.indexOf(pattern);
        if (idx == -1) {
            return "";
        }
        int colonIdx = json.indexOf(':', idx + pattern.length());
        if (colonIdx == -1) {
            return "";
        }
        int startVal = colonIdx + 1;
        while (startVal < json.length() && Character.isWhitespace(json.charAt(startVal))) {
            startVal++;
        }
        if (startVal >= json.length()) {
            return "";
        }

        if (json.charAt(startVal) == '[') {
            int endBracket = json.indexOf(']', startVal);
            if (endBracket != -1) {
                String arrayStr = json.substring(startVal + 1, endBracket);
                List<String> artists = new ArrayList<>();
                for (String part : arrayStr.split(",")) {
                    String cleaned = part.trim().replaceAll("^\"|\"$", "");
                    if (!cleaned.isBlank()) {
                        artists.add(cleaned);
                    }
                }
                return String.join(", ", artists);
            }
        }
        return extractJsonField(json, "artist");
    }

    private String extractNetEaseArtists(String json) {
        int idx = json.indexOf("\"artists\"");
        if (idx == -1) {
            return "";
        }
        List<String> names = new ArrayList<>();
        int searchStart = idx;
        while (true) {
            int nameIdx = json.indexOf("\"name\"", searchStart);
            if (nameIdx == -1 || nameIdx > json.indexOf(']', idx) && json.indexOf(']', idx) != -1) {
                break;
            }
            String name = extractJsonField(json.substring(nameIdx), "name");
            if (!name.isBlank() && !names.contains(name)) {
                names.add(name);
            }
            searchStart = nameIdx + 6;
        }
        return String.join(", ", names);
    }

    private String extractNetEaseAlbum(String json) {
        int idx = json.indexOf("\"album\"");
        if (idx == -1) {
            return "";
        }
        return extractJsonField(json.substring(idx), "name");
    }

    private String extractTopLevelJsonField(String json, String fieldName) {
        String pattern = "\"" + fieldName + "\"";
        int depthObj = 0;
        int depthArr = 0;
        boolean inString = false;
        boolean escape = false;

        for (int i = 0; i < json.length() - pattern.length(); i++) {
            char c = json.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\' && inString) {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                if (inString && depthObj == 1 && depthArr == 0) {
                    if (json.startsWith(pattern, i)) {
                        return extractJsonField(json.substring(i), fieldName);
                    }
                }
                continue;
            }
            if (inString) {
                continue;
            }

            if (c == '{') depthObj++;
            else if (c == '}') depthObj--;
            else if (c == '[') depthArr++;
            else if (c == ']') depthArr--;
        }
        return "";
    }

    private boolean isNumeric(String str) {
        if (str == null || str.isBlank()) return false;
        for (char c : str.toCharArray()) {
            if (!Character.isDigit(c)) return false;
        }
        return true;
    }
}
