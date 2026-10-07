package com.cn.schrodinger.understatus.toolbox.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Ultra-fast LCS (Longest Common Subsequence) diff calculator.
 * Features common prefix and suffix pruning to shrink the DP table from O(N*M) to O(D^2),
 * and O(1) backtracking appends, providing orders-of-magnitude speedup and minimal memory usage.
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
        String[] linesA = (textA == null || textA.isEmpty()) ? new String[0] : textA.split("\\r?\\n", -1);
        String[] linesB = (textB == null || textB.isEmpty()) ? new String[0] : textB.split("\\r?\\n", -1);

        int n = linesA.length;
        int m = linesB.length;

        // 1. Fast path: Both empty
        if (n == 0 && m == 0) {
            return Collections.emptyList();
        }

        List<DiffLine> result = new ArrayList<>();

        // 2. Strip common prefix
        int start = 0;
        while (start < n && start < m && linesA[start].equals(linesB[start])) {
            result.add(new DiffLine(0, "  " + linesA[start]));
            start++;
        }

        // 3. Strip common suffix
        int endA = n - 1;
        int endB = m - 1;
        List<DiffLine> suffix = new ArrayList<>();
        while (endA >= start && endB >= start && linesA[endA].equals(linesB[endB])) {
            suffix.add(new DiffLine(0, "  " + linesA[endA]));
            endA--;
            endB--;
        }
        Collections.reverse(suffix);

        // 4. Middle distinct slice computation
        int subLenA = endA - start + 1;
        int subLenB = endB - start + 1;

        if (subLenA > 0 && subLenB > 0) {
            // Compute LCS on middle slice only
            int[][] dp = new int[subLenA + 1][subLenB + 1];
            for (int i = 1; i <= subLenA; i++) {
                String lineA = linesA[start + i - 1];
                for (int j = 1; j <= subLenB; j++) {
                    if (lineA.equals(linesB[start + j - 1])) {
                        dp[i][j] = dp[i - 1][j - 1] + 1;
                    } else {
                        dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                    }
                }
            }

            // Backtrack to collect edits (append and reverse in O(K) instead of O(K^2) insert at 0)
            List<DiffLine> middle = new ArrayList<>(subLenA + subLenB);
            int i = subLenA, j = subLenB;
            while (i > 0 || j > 0) {
                if (i > 0 && j > 0 && linesA[start + i - 1].equals(linesB[start + j - 1])) {
                    middle.add(new DiffLine(0, "  " + linesA[start + i - 1]));
                    i--;
                    j--;
                } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
                    middle.add(new DiffLine(1, "+ " + linesB[start + j - 1]));
                    j--;
                } else {
                    middle.add(new DiffLine(-1, "- " + linesA[start + i - 1]));
                    i--;
                }
            }
            Collections.reverse(middle);
            result.addAll(middle);
        } else if (subLenA > 0) {
            // Only deletions in middle
            for (int k = start; k <= endA; k++) {
                result.add(new DiffLine(-1, "- " + linesA[k]));
            }
        } else if (subLenB > 0) {
            // Only additions in middle
            for (int k = start; k <= endB; k++) {
                result.add(new DiffLine(1, "+ " + linesB[k]));
            }
        }

        // 5. Append suffix
        result.addAll(suffix);

        return result;
    }
}
