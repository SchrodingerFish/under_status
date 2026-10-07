package com.cn.schrodinger.understatus.alarm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AlarmSchedulerTest {

    @Test
    void catchesUpWhenEventThreadMissesAlarmMinute() {
        Alarm alarm = Alarm.once("12:00", "break");
        AlarmScheduler scheduler = new AlarmScheduler();
        LocalDateTime noon = LocalDateTime.of(2026, 10, 7, 12, 0);
        assertTrue(scheduler.dueAlarms(List.of(alarm), noon.minusMinutes(1)).isEmpty());
        assertEquals(List.of(alarm), scheduler.dueAlarms(List.of(alarm), noon.plusMinutes(2)));
        assertTrue(scheduler.dueAlarms(List.of(alarm), noon.plusMinutes(3)).isEmpty());
    }

    @Test
    void onceAlarmTriggersOnlyOnceAndDisablesItself() {
        Alarm alarm = Alarm.once("12:00", "午休");
        AlarmScheduler scheduler = new AlarmScheduler();
        LocalDateTime noon = LocalDateTime.of(2026, 7, 16, 12, 0);

        assertEquals(List.of(alarm), scheduler.dueAlarms(List.of(alarm), noon));
        assertFalse(alarm.enabled);
        assertTrue(scheduler.dueAlarms(List.of(alarm), noon.plusSeconds(30)).isEmpty());
    }

    @Test void catchesUpRecentAlarmsAndPersistsDailyDeduplication() {
        Alarm alarm = Alarm.once("12:00", "reminder");
        alarm.repeatMode = "DAILY";
        AlarmScheduler scheduler = new AlarmScheduler();
        LocalDateTime noon = LocalDateTime.of(2026, 10, 7, 12, 0);
        assertTrue(scheduler.dueAlarms(List.of(alarm), noon.minusMinutes(1)).isEmpty());
        assertEquals(1, scheduler.dueAlarms(List.of(alarm), noon.plusMinutes(2)).size());
        Alarm reloaded = Alarm.deserialize(alarm.serialize());
        assertEquals(noon.toLocalDate(), reloaded.lastTriggeredDate);
        assertTrue(new AlarmScheduler().dueAlarms(List.of(reloaded), noon.plusSeconds(30)).isEmpty());
    }
}
