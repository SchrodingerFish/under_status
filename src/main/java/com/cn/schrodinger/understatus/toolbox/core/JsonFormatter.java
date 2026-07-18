package com.cn.schrodinger.understatus.toolbox.core;

/**
 * Pure Java JSON formatting utilities (prettify, minify).
 * Lightweight, high-performance, and has zero external dependencies.
 *
 * @author peter/antigravity
 */
public class JsonFormatter {

    public static String format(String json) {
        StringBuilder pretty = new StringBuilder();
        int indentLevel = 0;
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') {
                if (i > 0 && json.charAt(i - 1) == '\\') {
                    // Escaped quote
                } else {
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
        for (int i = 0; i < count; i++) {
            sb.append("  ");
        }
    }

    public static String minify(String json) {
        StringBuilder min = new StringBuilder();
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') {
                if (i > 0 && json.charAt(i - 1) == '\\') {
                    // Escaped
                } else {
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
}
