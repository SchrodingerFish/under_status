package com.cn.schrodinger.understatus.pomodoro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PomodoroEngineTest {

    @Test
    void transitionsFromWorkToBreak() {
        PomodoroEngine engine = new PomodoroEngine();
        engine.init(1, 1);
        engine.setTimeLeft(1);
        engine.setRunning(true);

        assertTrue(engine.tick());
        assertEquals("BREAK", engine.getState());
        assertEquals(60, engine.getTimeLeft());
    }
}
