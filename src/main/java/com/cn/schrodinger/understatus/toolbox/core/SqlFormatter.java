package com.cn.schrodinger.understatus.toolbox.core;

import java.util.Locale;
import java.util.Set;

/** Lexical SQL formatter; quoted content and comments are copied verbatim. */
public final class SqlFormatter {
    private static final Set<String> KEYWORDS = Set.of("SELECT", "FROM", "WHERE", "LEFT", "RIGHT", "INNER",
            "OUTER", "JOIN", "AND", "OR", "GROUP", "ORDER", "HAVING", "LIMIT", "INSERT", "UPDATE", "SET",
            "VALUES", "DELETE", "UNION", "BY", "INTO");
    private static final Set<String> BREAKS = Set.of("SELECT", "FROM", "WHERE", "LEFT", "RIGHT", "INNER",
            "JOIN", "AND", "OR", "GROUP", "ORDER", "HAVING", "LIMIT", "INSERT", "UPDATE", "SET",
            "VALUES", "DELETE", "UNION");

    private SqlFormatter() {}
    public static String format(String sql) { return transform(sql, true); }
    public static String minify(String sql) { return transform(sql, false); }

    private static String transform(String sql, boolean pretty) {
        if (sql == null || sql.isBlank()) return "";
        ToolLimits.input(sql);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < sql.length();) {
            ToolLimits.checkInterrupted();
            char ch = sql.charAt(i);
            if (Character.isWhitespace(ch)) {
                int start = i++;
                while (i < sql.length() && Character.isWhitespace(sql.charAt(i))) i++;
                String whitespace = sql.substring(start, i);
                // Newlines can separate PostgreSQL string constants, so preserve that distinction.
                if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') {
                    out.append(whitespace.indexOf('\n') >= 0 || whitespace.indexOf('\r') >= 0 ? '\n' : ' ');
                }
            } else if (sql.startsWith("--", i) || ch == '#') {
                int end = i;
                while (end < sql.length() && sql.charAt(end) != '\n' && sql.charAt(end) != '\r') end++;
                out.append(sql, i, end).append('\n');
                i = end;
            } else if (sql.startsWith("/*", i)) {
                int end = i + 2, depth = 1;
                while (end < sql.length() && depth > 0) {
                    if (sql.startsWith("/*", end)) { depth++; end += 2; }
                    else if (sql.startsWith("*/", end)) { depth--; end += 2; }
                    else end++;
                }
                if (depth != 0) throw new IllegalArgumentException("Unterminated SQL comment");
                out.append(sql, i, end);
                i = end;
            } else if (ch == '\'' || ch == '"' || ch == '`' || ch == '[') {
                char close = ch == '[' ? ']' : ch;
                int end = i + 1;
                boolean closed = false;
                while (end < sql.length()) {
                    char c = sql.charAt(end++);
                    if (c == '\\' && ch != '[' && end < sql.length()) end++;
                    else if (c == close) {
                        if (end < sql.length() && sql.charAt(end) == close) end++;
                        else { closed = true; break; }
                    }
                }
                if (!closed) throw new IllegalArgumentException("Unterminated SQL string or identifier");
                out.append(sql, i, end);
                i = end;
            } else if (ch == '$' && dollarDelimiter(sql, i) != null) {
                String delimiter = dollarDelimiter(sql, i);
                int end = sql.indexOf(delimiter, i + delimiter.length());
                if (end < 0) throw new IllegalArgumentException("Unterminated SQL dollar quote");
                end += delimiter.length();
                out.append(sql, i, end);
                i = end;
            } else if (ch == ':' || ch == '@' || ch == '$' || ch == '?' || ch == '%') {
                int end = parameterEnd(sql, i);
                out.append(sql, i, end);
                i = end;
            } else if (Character.isLetter(ch) || ch == '_') {
                int nameEnd = identifierEnd(sql, i);
                int end = qualifiedEnd(sql, nameEnd);
                String word = sql.substring(i, end);
                String upper = word.toUpperCase(Locale.ROOT);
                boolean keyword = pretty && end == nameEnd && !followsDot(sql, i);
                if (keyword && BREAKS.contains(upper)) {
                    while (out.length() > 0 && out.charAt(out.length() - 1) == ' ') out.setLength(out.length() - 1);
                    if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') out.append('\n');
                    if (upper.equals("AND") || upper.equals("OR")) out.append("  ");
                }
                out.append(keyword && KEYWORDS.contains(upper) ? upper : word);
                i = end;
            } else {
                out.append(ch);
                i++;
            }
        }
        return ToolLimits.output(out.toString().strip());
    }

    /** Prefixes are part of the token: parameter names can be case-sensitive keywords. */
    private static int parameterEnd(String sql, int start) {
        char prefix = sql.charAt(start);
        int end = start + 1;
        if (prefix == '%') {
            // Python DB-API named placeholders; bare % and %s stay unchanged as well.
            if (end < sql.length() && sql.charAt(end) == '(') {
                int close = sql.indexOf(")s", end + 1);
                if (close >= 0) return close + 2;
            }
            return end;
        }
        if (prefix == '$' && end < sql.length() && sql.charAt(end) == '{') {
            int close = sql.indexOf('}', end + 1);
            if (close < 0) throw new IllegalArgumentException("Unterminated SQL named placeholder");
            return close + 1;
        }
        if (prefix == '?') {
            while (end < sql.length() && Character.isDigit(sql.charAt(end))) end++;
            return end;
        }
        // SQL Server system variables and PostgreSQL casts are indivisible prefixes.
        if ((prefix == '@' || prefix == ':') && end < sql.length() && sql.charAt(end) == prefix) end++;
        end = identifierEnd(sql, end);
        if (prefix == '@') return qualifiedEnd(sql, end);
        if (prefix == '$') {
            // SQLite also accepts Tcl-style $scope::name(optional suffix) parameters.
            while (sql.startsWith("::", end)) {
                int next = identifierEnd(sql, end + 2);
                if (next == end + 2) break;
                end = next;
            }
            if (end > start + 1 && end < sql.length() && sql.charAt(end) == '(') {
                int close = sql.indexOf(')', end + 1);
                if (close < 0) throw new IllegalArgumentException("Unterminated SQL parameter suffix");
                end = close + 1;
            }
        }
        return end;
    }

    private static int identifierEnd(String sql, int start) {
        int end = start;
        while (end < sql.length() && (Character.isLetterOrDigit(sql.charAt(end))
                || sql.charAt(end) == '_' || sql.charAt(end) == '$')) end++;
        return end;
    }

    private static int qualifiedEnd(String sql, int start) {
        int end = start;
        while (end < sql.length() && sql.charAt(end) == '.') {
            int next = identifierEnd(sql, end + 1);
            if (next == end + 1) break;
            end = next;
        }
        return end;
    }

    private static boolean followsDot(String sql, int start) {
        int previous = start - 1;
        while (previous >= 0 && Character.isWhitespace(sql.charAt(previous))) previous--;
        return previous >= 0 && sql.charAt(previous) == '.';
    }

    private static String dollarDelimiter(String sql, int start) {
        int end = start + 1;
        while (end < sql.length() && (Character.isLetterOrDigit(sql.charAt(end)) || sql.charAt(end) == '_')) end++;
        if (end >= sql.length() || sql.charAt(end) != '$') return null;
        if (end > start + 1 && Character.isDigit(sql.charAt(start + 1))) return null;
        return sql.substring(start, end + 1);
    }
}
