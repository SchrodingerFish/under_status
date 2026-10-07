package com.cn.schrodinger.understatus.toolbox.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High performance, single-pass SQL Prettifier and Minifier.
 *
 * @author peter/antigravity
 */
public class SqlFormatter {

    private static final Pattern KEYWORDS_PATTERN = Pattern.compile(
            "(?i)\\b(SELECT|FROM|WHERE|LEFT JOIN|RIGHT JOIN|INNER JOIN|JOIN|AND|OR|GROUP BY|ORDER BY|HAVING|LIMIT|INSERT INTO|UPDATE|SET|VALUES|DELETE FROM|UNION)\\b"
    );

    public static String format(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return "";
        }

        String cleaned = sql.replaceAll("\\s+", " ").trim();
        Matcher m = KEYWORDS_PATTERN.matcher(cleaned);
        StringBuilder withNewlines = new StringBuilder(cleaned.length() + 64);
        while (m.find()) {
            m.appendReplacement(withNewlines, "\n" + m.group().toUpperCase());
        }
        m.appendTail(withNewlines);

        StringBuilder sb = new StringBuilder(withNewlines.length() + 32);
        String[] lines = withNewlines.toString().split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            boolean isSub = trimmed.startsWith("AND") || trimmed.startsWith("OR");
            if (isSub) {
                sb.append("  ");
            }

            sb.append(trimmed).append("\n");
        }

        return sb.toString().trim();
    }

    public static String minify(String sql) {
        if (sql == null) {
            return "";
        }
        return sql.replaceAll("\\s+", " ").trim();
    }
}
