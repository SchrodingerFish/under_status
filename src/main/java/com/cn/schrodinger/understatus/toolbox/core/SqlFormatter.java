package com.cn.schrodinger.understatus.toolbox.core;

/**
 * High performance, local SQL Prettifier and Minifier.
 *
 * @author peter/antigravity
 */
public class SqlFormatter {

    public static String format(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return "";
        }
        
        String cleaned = sql.replaceAll("\\s+", " ").trim();

        // SQL standard keywords
        String[] keywords = {
            "SELECT", "FROM", "WHERE", "LEFT JOIN", "RIGHT JOIN", 
            "INNER JOIN", "JOIN", "AND", "OR", "GROUP BY", "ORDER BY", 
            "HAVING", "LIMIT", "INSERT INTO", "UPDATE", "SET", "VALUES", 
            "DELETE FROM", "UNION"
        };

        String result = cleaned;
        for (String kw : keywords) {
            result = result.replaceAll("(?i)\\b" + kw + "\\b", "\n" + kw);
        }

        StringBuilder sb = new StringBuilder();
        String[] lines = result.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            // Indent sub-clauses
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
