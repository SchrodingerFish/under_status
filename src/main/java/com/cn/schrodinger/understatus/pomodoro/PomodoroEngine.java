package com.cn.schrodinger.understatus.pomodoro;

/**
 * Encapsulates the countdown states and ticks of the Pomodoro timer.
 * UI-agnostic; countdown follows elapsed clock time, including delayed UI ticks.
 *
 * @author peter/antigravity
 */
public class PomodoroEngine {

    private int workMinutes = 25;
    private int breakMinutes = 5;
    private String state = "WORK"; // "WORK", "BREAK"
    private int timeLeft = 25 * 60;
    private boolean running = false;
    private final java.time.Clock clock;
    private long lastTick;

    public PomodoroEngine() { this(java.time.Clock.systemUTC()); }
    public PomodoroEngine(java.time.Clock clock) { this.clock = java.util.Objects.requireNonNull(clock); }

    public void init(int workMin, int breakMin) {
        this.workMinutes = Math.max(1, Math.min(1440, workMin));
        this.breakMinutes = Math.max(1, Math.min(1440, breakMin));
        if (!running) {
            this.timeLeft = ("WORK".equals(state) ? workMinutes : breakMinutes) * 60;
        }
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        if (this.running == running) return;
        if (this.running) tick();
        lastTick = clock.millis();
        this.running = running;
    }

    public String getState() {
        return state;
    }

    public int getTimeLeft() {
        return timeLeft;
    }

    public void setTimeLeft(int timeLeft) {
        this.timeLeft = Math.max(1, timeLeft);
        lastTick = clock.millis();
    }

    public int getWorkMinutes() {
        return workMinutes;
    }

    public int getBreakMinutes() {
        return breakMinutes;
    }

    /**
     * Decrements the countdown timer.
     * Returns true if a state transition occurred (e.g. Work finished, Rest finished).
     */
    public boolean tick() {
        if (!running) {
            return false;
        }
        long now = clock.millis();
        if (now < lastTick) lastTick = now;
        long seconds = (now - lastTick) / 1000;
        lastTick += seconds * 1000;
        boolean transitioned = false;
        if (seconds >= timeLeft) {
            seconds -= timeLeft;
            if ("WORK".equals(state)) {
                state = "BREAK";
                timeLeft = breakMinutes * 60;
            } else {
                state = "WORK";
                timeLeft = workMinutes * 60;
            }
            transitioned = true;
            seconds %= (workMinutes + breakMinutes) * 60L;
            if (seconds >= timeLeft) {
                seconds -= timeLeft;
                state = "WORK".equals(state) ? "BREAK" : "WORK";
                timeLeft = ("WORK".equals(state) ? workMinutes : breakMinutes) * 60;
            }
        }
        timeLeft -= (int) seconds;
        return transitioned;
    }

    public void reset() {
        this.running = false;
        this.state = "WORK";
        this.timeLeft = workMinutes * 60;
    }
}
