package com.cn.schrodinger.understatus.toolbox.core;

/**
 * Pure Java JSON formatting utilities (prettify, minify).
 * Lightweight, high-performance, and has zero external dependencies.
 *
 * @author peter/antigravity
 */
public class JsonFormatter {

    private static final String[] INDENT_CACHE = new String[32];
    static {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < INDENT_CACHE.length; i++) {
            INDENT_CACHE[i] = sb.toString();
            sb.append("  ");
        }
    }

    public static String format(String json) {
        if (json == null) {
            return "";
        }
        StringBuilder pretty = new StringBuilder(json.length() * 3 / 2);
        int indentLevel = 0;
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') {
                int backslashes = 0;
                for (int j = i - 1; j >= 0 && json.charAt(j) == '\\'; j--) {
                    backslashes++;
                }
                if (backslashes % 2 == 0) {
                    inString = !inString;
                }
            }
            if (inString) {
                pretty.append(c);
                continue;
            }
            switch (c) {
                case '{':
                case '[':
                    pretty.append(c).append("\n");
                    indentLevel++;
                    appendIndent(pretty, indentLevel);
                    break;
                case '}':
                case ']':
                    pretty.append("\n");
                    indentLevel--;
                    appendIndent(pretty, indentLevel);
                    pretty.append(c);
                    break;
                case ',':
                    pretty.append(c).append("\n");
                    appendIndent(pretty, indentLevel);
                    break;
                case ':':
                    pretty.append(c).append(" ");
                    break;
                case ' ':
                case '\n':
                case '\r':
                case '\t':
                    break;
                default:
                    pretty.append(c);
            }
        }
        return pretty.toString();
    }

    private static void appendIndent(StringBuilder sb, int count) {
        if (count <= 0) return;
        if (count < INDENT_CACHE.length) {
            sb.append(INDENT_CACHE[count]);
        } else {
            for (int i = 0; i < count; i++) {
                sb.append("  ");
            }
        }
    }

    public static String minify(String json) {
        if (json == null) {
            return "";
        }
        StringBuilder min = new StringBuilder(json.length());
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') {
                int backslashes = 0;
                for (int j = i - 1; j >= 0 && json.charAt(j) == '\\'; j--) {
                    backslashes++;
                }
                if (backslashes % 2 == 0) {
                    inString = !inString;
                }
            }
            if (inString) {
                min.append(c);
                continue;
            }
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                continue;
            }
            min.append(c);
        }
        return min.toString();
    }

    public static String escape(String json) {
        if (json == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String unescape(String escaped) {
        if (escaped == null) return "";
        String s = escaped.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
        }
        StringBuilder sb = new StringBuilder();
        boolean inEscape = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inEscape) {
                switch (c) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    default -> {
                        sb.append('\\');
                        sb.append(c);
                    }
                }
                inEscape = false;
            } else if (c == '\\') {
                inEscape = true;
            } else {
                sb.append(c);
            }
        }
        if (inEscape) {
            sb.append('\\');
        }
        return sb.toString();
    }
}
