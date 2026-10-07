package com.cn.schrodinger.understatus.toolbox.core;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Isolated Java regex evaluator, with cooperative checks as an additional fast cancellation path. */
public final class RegexEvaluator {
    public static final int MAX_INPUT = 100_000;
    public static final int MAX_MATCHES = 500;
    public static final int MAX_OUTPUT = 200_000;
    private RegexEvaluator() {}

    public record Span(int start, int end) {}
    public record Result(String output, List<Span> spans, boolean truncated) {}

    public static Result evaluate(String regex, String text, int flags, String replacement) {
        return evaluate(regex, text, flags, replacement, Duration.ofMillis(300));
    }

    public static Result evaluate(String regex, String text, int flags, String replacement, Duration budget) {
        validate(regex, text, replacement, budget);
        return RegexWorkerProcess.evaluate(regex, text, flags, replacement, budget);
    }

    static void validate(String regex, String text, String replacement, Duration budget) {
        if (regex.length() > 2048 || text.length() > MAX_INPUT || (replacement != null && replacement.length() > 2048)) {
            throw new IllegalArgumentException("正则/替换上限 2,048 字符，测试文本上限 100,000 字符");
        }
        if (budget.isNegative() || budget.isZero() || budget.compareTo(Duration.ofSeconds(2)) > 0) throw new IllegalArgumentException("Invalid regex time budget");
    }

    static Result evaluateInProcess(String regex, String text, int flags, String replacement, Duration budget) {
        validate(regex, text, replacement, budget);
        long deadline = System.nanoTime() + budget.toNanos();
        try {
            Matcher matcher = Pattern.compile(regex, flags).matcher(new CheckedText(text, 0, text.length(), deadline));
            List<Span> spans = new ArrayList<>();
            StringBuilder out = new StringBuilder();
            int lastEnd = 0;
            while (matcher.find()) {
                check(deadline);
                if (spans.size() == MAX_MATCHES) {
                    if (replacement != null) throw new IllegalArgumentException("替换匹配超过 500 个；请缩小输入范围");
                    append(out, "\n仅展示前 500 个匹配结果。\n");
                    return new Result(out.toString(), List.copyOf(spans), true);
                }
                spans.add(new Span(matcher.start(), matcher.end()));
                if (replacement != null) {
                    append(out, text.substring(lastEnd, matcher.start()));
                    expandReplacement(out, replacement, matcher);
                    lastEnd = matcher.end();
                } else {
                    append(out, "【匹配 #" + spans.size() + "】 (" + matcher.start() + "~" + matcher.end() + "): ");
                    append(out, matcher.group());
                    append(out, "\n");
                    for (int group = 1; group <= matcher.groupCount(); group++) {
                        append(out, "  Group " + group + ": ");
                        append(out, String.valueOf(matcher.group(group)));
                        append(out, "\n");
                    }
                }
            }
            if (replacement != null) append(out, text.substring(lastEnd));
            check(deadline);
            return new Result(out.toString(), List.copyOf(spans), false);
        } catch (StackOverflowError ex) {
            throw new IllegalArgumentException("正则表达式递归过深，请简化表达式", ex);
        }
    }

    private static void expandReplacement(StringBuilder out, String replacement, Matcher matcher) {
        for (int i = 0; i < replacement.length(); i++) {
            char ch = replacement.charAt(i);
            if (ch == '\\') {
                if (++i == replacement.length()) throw new IllegalArgumentException("Trailing replacement escape");
                append(out, String.valueOf(replacement.charAt(i)));
            } else if (ch == '$') {
                if (++i == replacement.length()) throw new IllegalArgumentException("Missing replacement group");
                String value;
                if (replacement.charAt(i) == '{') {
                    int end = replacement.indexOf('}', i + 1);
                    if (end < 0) throw new IllegalArgumentException("Unclosed named replacement group");
                    value = matcher.group(replacement.substring(i + 1, end));
                    i = end;
                } else {
                    char digit = replacement.charAt(i);
                    if (digit < '0' || digit > '9') throw new IllegalArgumentException("Invalid replacement group");
                    int group = digit - '0';
                    if (group > matcher.groupCount()) throw new IllegalArgumentException("Unknown replacement group");
                    while (i + 1 < replacement.length()) {
                        char next = replacement.charAt(i + 1);
                        if (next < '0' || next > '9' || group * 10 + next - '0' > matcher.groupCount()) break;
                        group = group * 10 + next - '0';
                        i++;
                    }
                    value = matcher.group(group);
                }
                if (value != null) append(out, value);
            } else append(out, String.valueOf(ch));
        }
    }

    private static void append(StringBuilder out, String value) {
        if (value.length() > MAX_OUTPUT - out.length()) throw new IllegalArgumentException("正则输出超过 200,000 字符限制");
        out.append(value);
    }

    private static void check(long deadline) {
        ToolLimits.checkInterrupted();
        if (System.nanoTime() - deadline >= 0) throw new IllegalArgumentException("正则计算超时，请简化表达式或缩短文本");
    }

    private record CheckedText(String text, int start, int end, long deadline) implements CharSequence {
        @Override public int length() { check(deadline); return end - start; }
        @Override public char charAt(int index) {
            check(deadline);
            if (index < 0 || index >= end - start) throw new IndexOutOfBoundsException(index);
            return text.charAt(start + index);
        }
        @Override public CharSequence subSequence(int from, int to) {
            check(deadline);
            if (from < 0 || to < from || to > end - start) throw new IndexOutOfBoundsException();
            return new CheckedText(text, start + from, start + to, deadline);
        }
        @Override public String toString() { check(deadline); return text.substring(start, end); }
    }
}
