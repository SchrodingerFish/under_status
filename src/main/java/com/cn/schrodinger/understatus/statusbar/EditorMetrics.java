package com.cn.schrodinger.understatus.statusbar;

/**
 * Snapshot of the active editor's text metrics.
 *
 * @param lines number of lines in the document
 * @param characters character count ({@link javax.swing.text.Document#getLength()})
 * @param encoding resolved file encoding name
 * @param available {@code false} when no editor document is currently tracked
 */
public record EditorMetrics(int lines, int characters, String encoding, boolean available) {

    public static EditorMetrics unavailable() {
        return new EditorMetrics(0, 0, "", false);
    }
}
