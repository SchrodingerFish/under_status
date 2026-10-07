package com.cn.schrodinger.understatus.pomodoro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PomodoroEngineTest {

    @Test
    void transitionsFromWorkToBreak() {
        MutableClock clock = new MutableClock();
        PomodoroEngine engine = new PomodoroEngine(clock);
        engine.init(1, 1);
        engine.setTimeLeft(1);
        engine.setRunning(true);
        clock.millis = 1000;

        assertTrue(engine.tick());
        assertEquals("BREAK", engine.getState());
        assertEquals(60, engine.getTimeLeft());
    }

    @Test void delayedTicksCatchUpAndPausePreservesRemainingTime() {
        MutableClock clock = new MutableClock();
        PomodoroEngine engine = new PomodoroEngine(clock);
        engine.init(1, 1);
        engine.setRunning(true);
        clock.millis = 90_000;
        assertTrue(engine.tick());
        assertEquals("BREAK", engine.getState());
        assertEquals(30, engine.getTimeLeft());
        engine.setRunning(false);
        clock.millis += 600_000;
        engine.setRunning(true);
        clock.millis += 2000;
        engine.tick();
        assertEquals(28, engine.getTimeLeft());
    }

    private static final class MutableClock extends java.time.Clock {
        long millis;
        @Override public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
        @Override public java.time.Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public java.time.Instant instant() { return java.time.Instant.ofEpochMilli(millis); }
    }
}
