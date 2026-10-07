package com.cn.schrodinger.understatus.toolbox.core;

/**
 * Character encoding converter supporting Unicode escape sequences (\\uXXXX)
 * and space-separated ASCII code points.
 *
 * @author peter/antigravity
 */
public class EncodingConverter {

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    public static String stringToUnicode(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length() * 6);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            sb.append('\\').append('u')
              .append(HEX_CHARS[(c >>> 12) & 0xF])
              .append(HEX_CHARS[(c >>> 8) & 0xF])
              .append(HEX_CHARS[(c >>> 4) & 0xF])
              .append(HEX_CHARS[c & 0xF]);
        }
        return sb.toString();
    }

    public static String unicodeToString(String unicode) {
        if (unicode == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(unicode.length());
        int i = 0;
        int len = unicode.length();
        while (i < len) {
            if (i <= len - 6 && unicode.charAt(i) == '\\' && unicode.charAt(i + 1) == 'u') {
                int h1 = hexValue(unicode.charAt(i + 2));
                int h2 = hexValue(unicode.charAt(i + 3));
                int h3 = hexValue(unicode.charAt(i + 4));
                int h4 = hexValue(unicode.charAt(i + 5));
                if (h1 >= 0 && h2 >= 0 && h3 >= 0 && h4 >= 0) {
                    sb.append((char) ((h1 << 12) | (h2 << 8) | (h3 << 4) | h4));
                    i += 6;
                    continue;
                }
            }
            sb.append(unicode.charAt(i));
            i++;
        }
        return sb.toString();
    }

    private static int hexValue(char ch) {
        if (ch >= '0' && ch <= '9') return ch - '0';
        if (ch >= 'a' && ch <= 'f') return ch - 'a' + 10;
        if (ch >= 'A' && ch <= 'F') return ch - 'A' + 10;
        return -1;
    }

    public static String stringToAscii(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length() * 4);
        for (int i = 0; i < text.length(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append((int) text.charAt(i));
        }
        return sb.toString();
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
