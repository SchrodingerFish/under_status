package com.cn.schrodinger.understatus.music;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Model class representing a song item for music search, playlist queue, and favorites.
 */
public class MusicSong {

    private static final Logger LOGGER = Logger.getLogger(MusicSong.class.getName());

    private String id;
    private String name;
    private String artist;
    private String album;
    private String source;
    private String url;
    private String picUrl;
    private String lyric;

    public MusicSong() {
        this("", "", "", "", "netease", "", "", "");
    }

    public MusicSong(String id, String name, String artist, String album, String source) {
        this(id, name, artist, album, source, "", "", "");
    }

    public MusicSong(String id, String name, String artist, String album, String source,
                     String url, String picUrl, String lyric) {
        this.id = id == null ? "" : id;
        this.name = name == null ? "" : name;
        this.artist = artist == null ? "" : artist;
        this.album = album == null ? "" : album;
        this.source = source == null ? "netease" : source;
        this.url = url == null ? "" : url;
        this.picUrl = picUrl == null ? "" : picUrl;
        this.lyric = lyric == null ? "" : lyric;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getPicUrl() {
        return picUrl;
    }

    public void setPicUrl(String picUrl) {
        this.picUrl = picUrl;
    }

    public String getLyric() {
        return lyric;
    }

    public void setLyric(String lyric) {
        this.lyric = lyric;
    }

    public String getSourceDisplayName() {
        switch (source.toLowerCase()) {
            case "netease":
                return "网易云";
            case "tencent":
                return "QQ音乐";
            case "kugou":
                return "酷狗";
            case "kuwo":
                return "酷我";
            case "migu":
                return "咪咕";
            case "bilibili":
                return "B站";
            default:
                return source;
        }
    }

    /**
     * Serializes song to a flat string for preferences storage.
     */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append(encode(id)).append("||");
        sb.append(encode(name)).append("||");
        sb.append(encode(artist)).append("||");
        sb.append(encode(album)).append("||");
        sb.append(encode(source)).append("||");
        sb.append(encode(url)).append("||");
        sb.append(encode(picUrl)).append("||");
        sb.append(encode(lyric));
        return sb.toString();
    }

    public static MusicSong deserialize(String data) {
        if (data == null || data.isBlank()) {
            return null;
        }
        try {
            String[] parts = data.split("\\|\\|", -1);
            if (parts.length < 5) {
                return null;
            }
            String id = decode(parts[0]);
            String name = decode(parts[1]);
            String artist = decode(parts[2]);
            String album = decode(parts[3]);
            String source = decode(parts[4]);
            String url = parts.length > 5 ? decode(parts[5]) : "";
            String picUrl = parts.length > 6 ? decode(parts[6]) : "";
            String lyric = parts.length > 7 ? decode(parts[7]) : "";
            return new MusicSong(id, name, artist, album, source, url, picUrl, lyric);
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "Failed to deserialize song", ex);
            return null;
        }
    }

    public static String serializeList(List<MusicSong> list) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            sb.append(list.get(i).serialize());
            if (i < list.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    public static List<MusicSong> deserializeList(String data) {
        List<MusicSong> result = new ArrayList<>();
        if (data == null || data.isBlank()) {
            return result;
        }
        String[] lines = data.split("\n");
        for (String line : lines) {
            MusicSong song = deserialize(line.trim());
            if (song != null) {
                result.add(song);
            }
        }
        return result;
    }

    private static String encode(String val) {
        if (val == null) {
            val = "";
        }
        return Base64.getEncoder().encodeToString(val.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String val) {
        if (val == null || val.isBlank()) {
            return "";
        }
        return new String(Base64.getDecoder().decode(val), StandardCharsets.UTF_8);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MusicSong musicSong = (MusicSong) o;
        return Objects.equals(id, musicSong.id) && Objects.equals(source, musicSong.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, source);
    }

    @Override
    public String toString() {
        return name + " - " + artist + " [" + getSourceDisplayName() + "]";
    }
}
