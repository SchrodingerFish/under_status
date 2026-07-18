package com.cn.schrodinger.understatus.toolbox.core;

import java.util.ArrayList;
import java.util.List;

/**
 * High performance LCS (Longest Common Subsequence) diff calculator.
 * Compares two files/strings line-by-line and generates unified Git-style diff outputs.
 *
 * @author peter/antigravity
 */
public class DiffCalculator {

    public static class DiffLine {
        public final int type; // 0 = unchanged, 1 = added, -1 = deleted
        public final String text;

        public DiffLine(int type, String text) {
            this.type = type;
            this.text = text;
        }
    }

    public static List<DiffLine> calculateDiff(String textA, String textB) {
        String[] linesA = (textA == null) ? new String[0] : textA.split("\\r?\\n", -1);
        String[] linesB = (textB == null) ? new String[0] : textB.split("\\r?\\n", -1);

        int n = linesA.length;
        int m = linesB.length;
        
        // DP Table
        int[][] dp = new int[n + 1][m + 1];

        // LCS computation
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                if (linesA[i - 1].equals(linesB[j - 1])) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }

        List<DiffLine> result = new ArrayList<>();
        int i = n, j = m;
        
        // Backtrack to find edits
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && linesA[i - 1].equals(linesB[j - 1])) {
                result.add(0, new DiffLine(0, "  " + linesA[i - 1]));
                i--;
                j--;
            } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
                result.add(0, new DiffLine(1, "+ " + linesB[j - 1]));
                j--;
            } else {
                result.add(0, new DiffLine(-1, "- " + linesA[i - 1]));
                i--;
            }
        }
        return result;
    }
}
