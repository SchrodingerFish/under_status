package com.cn.schrodinger.understatus.toolbox.core;

/** Shared bounds for interactive text tools. Lengths count UTF-16 characters. */
public final class ToolLimits {
    public static final int MAX_INPUT = 250_000;
    public static final int MAX_OUTPUT = 500_000;

    private ToolLimits() {}

    public static void input(String text) {
        if (text != null && text.length() > MAX_INPUT) {
            throw new IllegalArgumentException("输入超过 250,000 字符限制");
        }
        checkInterrupted();
    }

    public static String output(String text) {
        if (text.length() > MAX_OUTPUT) throw new IllegalArgumentException("结果超过 500,000 字符限制");
        checkInterrupted();
        return text;
    }

    public static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
    }
}
