package com.cn.schrodinger.understatus.toolbox.core;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Formats code while preserving quoted content and comments verbatim. */
public final class SqlFormatter {
    private static final Set<String> BREAKS = Set.of("SELECT", "FROM", "WHERE", "JOIN", "AND", "OR",
            "GROUP", "ORDER", "HAVING", "LIMIT", "INSERT", "UPDATE", "SET", "VALUES", "DELETE", "UNION");
    private static final Pattern DOLLAR = Pattern.compile("\\$(?:[A-Za-z_][A-Za-z_0-9]*)?\\$");
    private SqlFormatter() {}

    public static String format(String sql) { return transform(sql, true); }
    public static String minify(String sql) { return transform(sql, false); }

    private static String transform(String sql, boolean pretty) {
        if (sql == null || sql.isBlank()) return "";
        StringBuilder out = new StringBuilder(sql.length());
        for (int i = 0; i < sql.length();) {
            char c = sql.charAt(i);
            if (Character.isWhitespace(c)) {
                if (!out.isEmpty() && !Character.isWhitespace(out.charAt(out.length() - 1))) out.append(' ');
                i++;
            } else if (sql.startsWith("--", i) || c == '#') {
                int end = sql.indexOf('\n', i);
                if (end < 0) end = sql.length();
                out.append(sql, i, end).append('\n');
                i = end;
            } else if (sql.startsWith("/*", i)) {
                int end = i + 2, depth = 1;
                while (end < sql.length() && depth > 0) {
                    if (sql.startsWith("/*", end)) { depth++; end += 2; }
                    else if (sql.startsWith("*/", end)) { depth--; end += 2; }
                    else end++;
                }
                if (depth != 0) throw new IllegalArgumentException("未闭合的 SQL 注释");
                out.append(sql, i, end); i = end;
            } else if (c == '\'' || c == '"' || c == 96 || c == '[') {
                char close = c == '[' ? ']' : c;
                int end = i + 1;
                boolean closed = false;
                while (end < sql.length()) {
                    char next = sql.charAt(end++);
                    if (next == '\\' && end < sql.length()) end++;
                    else if (next == close) {
                        if (end < sql.length() && sql.charAt(end) == close) end++;
                        else { closed = true; break; }
                    }
                }
                if (!closed) throw new IllegalArgumentException("未闭合的 SQL 引号");
                out.append(sql, i, end); i = end;
            } else if (c == '$' && dollarEnd(sql, i) > i) {
                int end = dollarEnd(sql, i);
                out.append(sql, i, end); i = end;
            } else if (Character.isLetter(c) || c == '_') {
                int end = i + 1;
                while (end < sql.length() && (Character.isLetterOrDigit(sql.charAt(end)) || "_$".indexOf(sql.charAt(end)) >= 0)) end++;
                String token = sql.substring(i, end), upper = token.toUpperCase(Locale.ROOT);
                if (pretty && BREAKS.contains(upper)) {
                    while (!out.isEmpty() && out.charAt(out.length() - 1) == ' ') out.setLength(out.length() - 1);
                    if (!out.isEmpty() && out.charAt(out.length() - 1) != '\n') out.append('\n');
                    if (upper.equals("AND") || upper.equals("OR")) out.append("  ");
                    out.append(upper);
                } else out.append(token);
                i = end;
            } else { out.append(c); i++; }
        }
        return out.toString().trim();
    }

    private static int dollarEnd(String sql, int start) {
        Matcher matcher = DOLLAR.matcher(sql).region(start, sql.length());
        if (!matcher.lookingAt()) return start;
        String delimiter = matcher.group();
        int end = sql.indexOf(delimiter, matcher.end());
        if (end < 0) throw new IllegalArgumentException("未闭合的 SQL dollar 引号");
        return end + delimiter.length();
    }
}
