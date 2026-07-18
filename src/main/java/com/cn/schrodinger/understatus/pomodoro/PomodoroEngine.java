package com.cn.schrodinger.understatus.pomodoro;

/**
 * Encapsulates the countdown states and ticks of the Pomodoro timer.
 * Thread-safe and UI-agnostic.
 *
 * @author peter/antigravity
 */
public class PomodoroEngine {

    private int workMinutes = 25;
    private int breakMinutes = 5;
    private String state = "WORK"; // "WORK", "BREAK"
    private int timeLeft = 25 * 60;
    private boolean running = false;

    public void init(int workMin, int breakMin) {
        this.workMinutes = workMin;
        this.breakMinutes = breakMin;
        if (!running) {
            this.timeLeft = workMinutes * 60;
        }
    }

    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public String getState() {
        return state;
    }

    public int getTimeLeft() {
        return timeLeft;
    }

    public void setTimeLeft(int timeLeft) {
        this.timeLeft = timeLeft;
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
        timeLeft--;
        if (timeLeft <= 0) {
            if ("WORK".equals(state)) {
                state = "BREAK";
                timeLeft = breakMinutes * 60;
            } else {
                state = "WORK";
                timeLeft = workMinutes * 60;
            }
            return true;
        }
        return false;
    }

    public void reset() {
        this.running = false;
        this.state = "WORK";
        this.timeLeft = workMinutes * 60;
    }
}
