package com.cn.schrodinger.understatus.music;

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
import com.cn.schrodinger.understatus.settings.SettingsRepository;

/**
 * Client for fetching music search results, audio stream URLs, album covers, and lyrics
 * from GDStudio Music API or compatible Meting endpoints.
 */
public class MusicApiClient {

    private static final Logger LOGGER = Logger.getLogger(MusicApiClient.class.getName());

    public static final String DEFAULT_API_URL = "https://music-api.gdstudio.xyz/api.php";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";
    private static final int TIMEOUT_MS = 10000;

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
        String encodedKw = URLEncoder.encode(keyword.trim(), StandardCharsets.UTF_8);

        String targetUrl = getApiUrl() + "?types=search&name=" + encodedKw + "&source=" + sourceParam
                + "&count=" + Math.max(1, Math.min(50, count));

        String json = executeGet(targetUrl);
        return parseSearchResult(json, sourceParam);
    }

    /**
     * Resolve direct audio playback URL for a song.
     */
    public String fetchSongUrl(String songId, String source) throws Exception {
        if (songId == null || songId.isBlank()) {
            return "";
        }
        String sourceParam = (source == null || source.isBlank()) ? "netease" : source.toLowerCase();
        String targetUrl = getApiUrl() + "?types=url&id=" + URLEncoder.encode(songId, StandardCharsets.UTF_8)
                + "&source=" + sourceParam + "&br=320";

        String json = executeGet(targetUrl);
        return parseSingleUrlField(json, "url");
    }

    /**
     * Resolve album picture cover URL for a song.
     */
    public String fetchPicUrl(String picIdOrSongId, String source) throws Exception {
        if (picIdOrSongId == null || picIdOrSongId.isBlank()) {
            return "";
        }
        String sourceParam = (source == null || source.isBlank()) ? "netease" : source.toLowerCase();
        String targetUrl = getApiUrl() + "?types=pic&id=" + URLEncoder.encode(picIdOrSongId, StandardCharsets.UTF_8)
                + "&source=" + sourceParam;

        String json = executeGet(targetUrl);
        return parseSingleUrlField(json, "url");
    }

    /**
     * Resolve LRC lyrics content for a song.
     */
    public String fetchLyric(String lyricIdOrSongId, String source) throws Exception {
        if (lyricIdOrSongId == null || lyricIdOrSongId.isBlank()) {
            return "";
        }
        String sourceParam = (source == null || source.isBlank()) ? "netease" : source.toLowerCase();
        String targetUrl = getApiUrl() + "?types=lrc&id=" + URLEncoder.encode(lyricIdOrSongId, StandardCharsets.UTF_8)
                + "&source=" + sourceParam;

        String response = executeGet(targetUrl);
        if (response.startsWith("{") && response.contains("\"lyric\"")) {
            return parseSingleUrlField(response, "lyric");
        }
        return response;
    }

    private String executeGet(String urlStr) throws Exception {
        URL url = URI.create(urlStr).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept", "application/json, text/plain, */*");
        conn.setRequestProperty("Referer", getApiUrl());
        conn.setInstanceFollowRedirects(true);

        int code = conn.getResponseCode();
        if (code >= 400) {
            InputStream err = conn.getErrorStream();
            String msg = err != null ? readStream(err) : "HTTP " + code;
            throw new Exception("Music API error (" + code + "): " + msg);
        }

        try (InputStream in = conn.getInputStream()) {
            return readStream(in);
        }
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

        // Lightweight custom JSON array parser for search result objects
        List<String> objects = splitJsonObjectsInArray(json);
        for (String objJson : objects) {
            try {
                String id = extractJsonField(objJson, "id");
                String name = extractJsonField(objJson, "name");
                String artist = extractArtistField(objJson);
                String album = extractJsonField(objJson, "album");
                String picId = extractJsonField(objJson, "pic_id");
                if (picId.isBlank()) {
                    picId = extractJsonField(objJson, "pic");
                }
                if (picId.isBlank()) {
                    picId = id;
                }

                String lyricId = extractJsonField(objJson, "lyric_id");
                if (lyricId.isBlank()) {
                    lyricId = id;
                }

                String url = extractJsonField(objJson, "url");
                String picUrl = extractJsonField(objJson, "pic");
                if (!picUrl.startsWith("http")) {
                    picUrl = "";
                }

                String songSource = extractJsonField(objJson, "source");
                if (songSource.isBlank()) {
                    songSource = defaultSource;
                }

                if (!id.isBlank() || !name.isBlank()) {
                    MusicSong song = new MusicSong(id, name, artist, album, songSource, url, picUrl, lyricId);
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
}
