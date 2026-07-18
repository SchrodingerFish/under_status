package com.cn.schrodinger.understatus.toolbox.core;

/**
 * Character encoding converter supporting Unicode escape sequences (\\uXXXX)
 * and space-separated ASCII code points.
 *
 * @author peter/antigravity
 */
public class EncodingConverter {

    public static String stringToUnicode(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            sb.append(String.format("\\u%04x", (int) c));
        }
        return sb.toString();
    }

    public static String unicodeToString(String unicode) {
        if (unicode == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < unicode.length()) {
            if (i <= unicode.length() - 6 && unicode.charAt(i) == '\\' && unicode.charAt(i + 1) == 'u') {
                try {
                    int code = Integer.parseInt(unicode.substring(i + 2, i + 6), 16);
                    sb.append((char) code);
                    i += 6;
                } catch (Exception ex) {
                    sb.append(unicode.charAt(i));
                    i++;
                }
            } else {
                sb.append(unicode.charAt(i));
                i++;
            }
        }
        return sb.toString();
    }

    public static String stringToAscii(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            sb.append((int) c).append(" ");
        }
        return sb.toString().trim();
    }

    public static String asciiToString(String ascii) {
        if (ascii == null || ascii.trim().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        String[] tokens = ascii.trim().split("\\s+");
        for (String token : tokens) {
            try {
                int val = Integer.parseInt(token);
                sb.append((char) val);
            } catch (Exception ex) {
                // Ignore invalid numbers
            }
        }
        return sb.toString();
    }
}
