package com.cn.schrodinger.understatus.toolbox.notes;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class NoteCodec {

    private static final Logger LOGGER = Logger.getLogger(NoteCodec.class.getName());
    private static final String VERSION = "v1";

    public String encode(List<Note> notes) {
        StringBuilder result = new StringBuilder();
        for (Note note : notes) {
            result.append(VERSION).append('\n')
                    .append(encodeValue(note.title())).append(':')
                    .append(encodeValue(note.content())).append('\n');
        }
        return result.toString();
    }

    public List<Note> decode(String data) {
        List<Note> result = new ArrayList<>();
        if (data == null || data.isBlank()) return result;
        String[] lines = data.split("\\R");
        for (int i = 0; i + 1 < lines.length; i += 2) {
            if (!VERSION.equals(lines[i])) {
                LOGGER.log(Level.FINE, "Ignoring unknown note format at line {0}", i + 1);
                continue;
            }
            String[] values = lines[i + 1].split(":", 2);
            if (values.length != 2) {
                LOGGER.log(Level.FINE, "Ignoring malformed note at line {0}", i + 2);
                continue;
            }
            try {
                result.add(new Note(decodeValue(values[0]), decodeValue(values[1])));
            } catch (IllegalArgumentException ex) {
                LOGGER.log(Level.FINE, "Ignoring malformed note at line {0}", i + 2);
            }
        }
        return result;
    }

    private static String encodeValue(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeValue(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
