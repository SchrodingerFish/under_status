package com.cn.schrodinger.understatus.music;

/**
 * Playback modes for the music player.
 */
public enum PlaybackMode {

    /** Sequential playback through playlist. */
    SEQUENCE("➡️ 顺序播放"),

    /** Loop through entire playlist. */
    LIST_LOOP("🔁 列表循环"),

    /** Repeat single track continuously. */
    SINGLE_LOOP("🔂 单曲循环"),

    /** Random shuffle through playlist. */
    RANDOM("🔀 随机播放");

    private final String displayTitle;

    PlaybackMode(String displayTitle) {
        this.displayTitle = displayTitle;
    }

    public String getDisplayTitle() {
        return displayTitle;
    }

    public PlaybackMode next() {
        PlaybackMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
