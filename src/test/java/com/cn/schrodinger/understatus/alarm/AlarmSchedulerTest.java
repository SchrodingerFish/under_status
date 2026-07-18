package com.cn.schrodinger.understatus.alarm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AlarmSchedulerTest {

    @Test
    void onceAlarmTriggersOnlyOnceAndDisablesItself() {
        Alarm alarm = Alarm.once("12:00", "午休");
        AlarmScheduler scheduler = new AlarmScheduler();
        LocalDateTime noon = LocalDateTime.of(2026, 7, 16, 12, 0);

        assertEquals(List.of(alarm), scheduler.dueAlarms(List.of(alarm), noon));
        assertFalse(alarm.enabled);
        assertTrue(scheduler.dueAlarms(List.of(alarm), noon.plusSeconds(30)).isEmpty());
    }
}
