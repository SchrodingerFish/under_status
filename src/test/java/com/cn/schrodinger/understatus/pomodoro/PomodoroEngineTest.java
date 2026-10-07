package com.cn.schrodinger.understatus.pomodoro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

class PomodoroEngineTest {

    @Test
    void backwardClockCorrectionDoesNotFreezeSubsequentElapsedTime() {
        MutableClock clock = new MutableClock();
        PomodoroEngine engine = new PomodoroEngine(clock);
        engine.init(1, 1);
        engine.setRunning(true);
        clock.millis += 20_000;
        assertEquals(40, engine.getTimeLeft());
        clock.millis -= 3_600_000;
        assertFalse(engine.tick());
        assertEquals(40, engine.getTimeLeft());
        clock.millis += 60_000;
        assertTrue(engine.tick(), "Countdown must resume immediately after the correction");
        assertEquals("BREAK", engine.getState());
        assertEquals(60, engine.getTimeLeft(), "A delayed callback starts one full next phase");
    }

    @Test
    void resetAfterClockCorrectionStartsAWorkingCountdown() {
        MutableClock clock = new MutableClock();
        PomodoroEngine engine = new PomodoroEngine(clock);
        engine.init(1, 1);
        engine.setRunning(true);
        clock.millis += 20_000;
        assertEquals(40, engine.getTimeLeft());
        clock.millis -= 3_600_000;
        engine.reset();
        engine.setRunning(true);
        clock.millis += 10_000;
        assertEquals(50, engine.getTimeLeft());
    }

    @Test
    void timerCallbacksDoNotConsumeTimeWithoutElapsedTime() {
        PomodoroEngine engine = new PomodoroEngine(new MutableClock());
        engine.init(1, 1);
        engine.setRunning(true);
        for (int i = 0; i < 10; i++) engine.tick();
        assertEquals(60, engine.getTimeLeft());
    }

    @Test
    void transitionsFromWorkToBreak() {
        MutableClock clock = new MutableClock();
        PomodoroEngine engine = new PomodoroEngine(clock);
        engine.init(1, 1);
        engine.setTimeLeft(1);
        engine.setRunning(true);
        clock.millis += 1_000;

        assertTrue(engine.tick());
        assertEquals("BREAK", engine.getState());
        assertEquals(60, engine.getTimeLeft());
    }

    @Test
    void delayedCallbackAndPauseUseElapsedTime() {
        MutableClock clock = new MutableClock();
        PomodoroEngine engine = new PomodoroEngine(clock);
        engine.init(1, 2);
        engine.setRunning(true);
        clock.millis += 20_000;
        engine.tick();
        assertEquals(40, engine.getTimeLeft());
        engine.setRunning(false);
        engine.init(1, 2);
        clock.millis += 3_600_000;
        assertEquals(40, engine.getTimeLeft());
        engine.setRunning(true);
        clock.millis += 120_000;
        assertTrue(engine.tick());
        assertEquals("BREAK", engine.getState());
        assertEquals(120, engine.getTimeLeft());
    }

    private static final class MutableClock extends Clock {
        private long millis;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
        @Override public long millis() { return millis; }
    }
}
