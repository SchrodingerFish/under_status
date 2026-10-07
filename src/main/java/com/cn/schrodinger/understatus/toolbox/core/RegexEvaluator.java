package com.cn.schrodinger.understatus.toolbox.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded Java regex evaluation. All matcher input accesses share a work budget. */
public final class RegexEvaluator {
    public static final int MAX_TEXT = 200_000;
    private RegexEvaluator() {}
    public record Match(int start, int end) {}
    public record Result(List<Match> matches, String output, boolean truncated) {}

    public static Result evaluate(String regex, String text, int flags, String replacement) {
        if (regex.length() > 4096 || text.length() > MAX_TEXT || replacement != null && replacement.length() > 4096) {
            throw new IllegalArgumentException("正则最多 4096 字符，文本最多 20 万字符");
        }
        try {
            Budget budget = new Budget();
            Pattern pattern = Pattern.compile(regex, flags);
            Matcher matcher = pattern.matcher(new Input(text, 0, text.length(), budget));
            List<Match> matches = new ArrayList<>();
            StringBuilder output = new StringBuilder();
            boolean truncated = false;
            while (matcher.find()) {
                budget.check();
                if (matches.size() == 500) {
                    if (replacement != null) throw new IllegalArgumentException("替换超过 500 处，请缩小输入范围");
                    truncated = true;
                    break;
                }
                matches.add(new Match(matcher.start(), matcher.end()));
                if (replacement != null) {
                    int largestGroup = matcher.end() - matcher.start();
                    for (int group = 1; group <= matcher.groupCount(); group++) {
                        largestGroup = Math.max(largestGroup, matcher.end(group) - matcher.start(group));
                    }
                    if (replacement.contains("$") && (long) replacement.length() * (largestGroup + 1) > MAX_TEXT) {
                        throw new IllegalArgumentException("单次替换可能超过结果上限，请缩小输入范围");
                    }
                    matcher.appendReplacement(output, replacement);
                }
                else {
                    output.append("【匹配 #").append(matches.size()).append("】位置: ")
                            .append(matcher.start()).append('~').append(matcher.end()).append('\n');
                    for (int group = 0; group <= matcher.groupCount(); group++) {
                        String value = matcher.group(group);
                        output.append("Group ").append(group).append(": ")
                                .append(value == null ? "(未匹配)" : value).append('\n');
                        limit(output);
                    }
                }
                limit(output);
            }
            if (replacement != null) matcher.appendTail(output);
            limit(output);
            if (truncated) output.append("\n仅显示前 500 个匹配，后续匹配未计算。");
            return new Result(List.copyOf(matches), output.toString(), truncated);
        } catch (StackOverflowError error) {
            throw new IllegalArgumentException("正则嵌套过深，请简化表达式");
        }
    }

    private static void limit(StringBuilder text) {
        if (text.length() > MAX_TEXT) throw new IllegalArgumentException("结果超过 20 万字符，请缩小输入范围");
    }

    private static final class Budget {
        private final long deadline = System.nanoTime() + 500_000_000L;
        private int remaining = 2_000_000;
        void check() {
            if (--remaining < 0 || Thread.currentThread().isInterrupted() || System.nanoTime() > deadline) {
                throw new IllegalArgumentException("匹配已取消或超出计算预算，请简化正则/缩小文本");
            }
        }
    }

    private record Input(String text, int start, int end, Budget budget) implements CharSequence {
        @Override public int length() { budget.check(); return end - start; }
        @Override public char charAt(int index) {
            budget.check();
            if (index < 0 || index >= end - start) throw new IndexOutOfBoundsException(index);
            return text.charAt(start + index);
        }
        @Override public CharSequence subSequence(int from, int to) {
            budget.check();
            if (from < 0 || to < from || to > end - start) throw new IndexOutOfBoundsException();
            return new Input(text, start + from, start + to, budget);
        }
        @Override public String toString() { budget.check(); return text.substring(start, end); }
    }
}
