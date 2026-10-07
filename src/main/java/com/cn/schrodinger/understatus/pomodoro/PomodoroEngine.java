package com.cn.schrodinger.understatus.pomodoro;

import java.time.Clock;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Countdown by elapsed time; a delayed callback starts one full next phase. */
public final class PomodoroEngine {
    private final LongSupplier elapsedMillis;
    private int workMinutes = 25;
    private int breakMinutes = 5;
    private String state = "WORK";
    private long remainingMillis = 25 * 60_000L;
    private long deadline;
    private long lastSample;
    private long elapsed;
    private boolean sampled;
    private boolean running;

    public PomodoroEngine() { this(() -> System.nanoTime() / 1_000_000L); }

    /** Compatibility seam for deterministic clocks; negative corrections are discarded. */
    public PomodoroEngine(Clock clock) { this(Objects.requireNonNull(clock)::millis); }

    /** Elapsed-time source in milliseconds, independent of the wall clock. */
    public PomodoroEngine(LongSupplier elapsedMillis) { this.elapsedMillis = Objects.requireNonNull(elapsedMillis); }

    public void init(int workMin, int breakMin) {
        int work = Math.max(1, Math.min(180, workMin));
        int rest = Math.max(1, Math.min(60, breakMin));
        boolean changed = "WORK".equals(state) ? work != workMinutes : rest != breakMinutes;
        workMinutes = work;
        breakMinutes = rest;
        if (!running && changed) remainingMillis = phaseMillis();
    }

    public boolean isRunning() { return running; }
    public void setRunning(boolean value) {
        if (value == running) return;
        long now = now();
        if (value) deadline = now + remainingMillis;
        else remainingMillis = Math.max(0, deadline - now);
        running = value;
    }
    public String getState() { return state; }
    public int getTimeLeft() {
        long millis = running ? Math.max(0, deadline - now()) : remainingMillis;
        return (int) ((millis + 999) / 1000);
    }
    public void setTimeLeft(int seconds) {
        remainingMillis = Math.max(0, seconds) * 1000L;
        if (running) deadline = now() + remainingMillis;
    }
    public int getWorkMinutes() { return workMinutes; }
    public int getBreakMinutes() { return breakMinutes; }

    public boolean tick() {
        if (!running || now() < deadline) return false;
        skip();
        return true;
    }

    /** Explicit user action; independent of timer ticks and pause state. */
    public void skip() {
        state = "WORK".equals(state) ? "BREAK" : "WORK";
        remainingMillis = phaseMillis();
        if (running) deadline = now() + remainingMillis;
    }

    public void reset() {
        running = false;
        state = "WORK";
        remainingMillis = phaseMillis();
    }

    private long phaseMillis() { return ("WORK".equals(state) ? workMinutes : breakMinutes) * 60_000L; }
    private long now() {
        long sample = elapsedMillis.getAsLong();
        if (sampled) elapsed += Math.max(0, sample - lastSample);
        else sampled = true;
        // Always rebase the sample after a backward jump. Clamping the absolute
        // value would freeze an injected wall clock until its old high-water mark.
        lastSample = sample;
        return elapsed;
    }
}
