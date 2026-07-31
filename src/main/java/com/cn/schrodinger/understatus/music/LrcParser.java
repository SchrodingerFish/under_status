package com.cn.schrodinger.understatus.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for LRC lyric format with timestamp synchronization support.
 */
public final class LrcParser {

    private static final Pattern TIME_TAG_PATTERN = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[\\.:](\\d{1,3}))?\\]");

    private LrcParser() {
    }

    public static class LrcLine {
        private final long timeMs;
        private final String text;

        public LrcLine(long timeMs, String text) {
            this.timeMs = timeMs;
            this.text = text == null ? "" : text.trim();
        }

        public long getTimeMs() {
            return timeMs;
        }

        public String getText() {
            return text;
        }

        @Override
        public String toString() {
            return text.isBlank() ? "♪ ♪ ♪" : text;
        }
    }

    public static List<LrcLine> parse(String lrcContent) {
        if (lrcContent == null || lrcContent.isBlank()) {
            return Collections.emptyList();
        }

        List<LrcLine> list = new ArrayList<>();
        String cleaned = lrcContent.replace("\\n", "\n").replace("\\r", "");
        String[] lines = cleaned.split("\n");

        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }

            Matcher matcher = TIME_TAG_PATTERN.matcher(line);
            List<Long> timestamps = new ArrayList<>();
            int lastIndex = 0;

            while (matcher.find()) {
                long min = Long.parseLong(matcher.group(1));
                long sec = Long.parseLong(matcher.group(2));
                String fracStr = matcher.group(3);
                long millis = 0;
                if (fracStr != null) {
                    if (fracStr.length() == 1) {
                        millis = Long.parseLong(fracStr) * 100;
                    } else if (fracStr.length() == 2) {
                        millis = Long.parseLong(fracStr) * 10;
                    } else {
                        millis = Long.parseLong(fracStr.substring(0, 3));
                    }
                }
                long timeMs = (min * 60 + sec) * 1000 + millis;
                timestamps.add(timeMs);
                lastIndex = matcher.end();
            }

            if (!timestamps.isEmpty()) {
                String lyricText = line.substring(lastIndex).trim();
                for (Long ts : timestamps) {
                    list.add(new LrcLine(ts, lyricText));
                }
            }
        }

        // Fallback for plain text lyrics without time tags
        if (list.isEmpty()) {
            long tsCounter = 0;
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isBlank() && !trimmed.startsWith("{") && !trimmed.endsWith("}")) {
                    list.add(new LrcLine(tsCounter, trimmed));
                    tsCounter += 3000;
                }
            }
        }

        list.sort(Comparator.comparingLong(LrcLine::getTimeMs));
        return list;
    }

    public static int findCurrentLineIndex(List<LrcLine> lines, long currentMs) {
        if (lines == null || lines.isEmpty()) {
            return -1;
        }
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (currentMs >= lines.get(i).getTimeMs()) {
                return i;
            }
        }
        return 0;
    }
}
