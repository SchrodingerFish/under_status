package com.cn.schrodinger.understatus.alarm;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AlarmScheduler {

    private static final Logger LOGGER = Logger.getLogger(AlarmScheduler.class.getName());
    private LocalDateTime lastCheck;

    public List<Alarm> dueAlarms(List<Alarm> alarms, LocalDateTime now) {
        List<Alarm> due = new ArrayList<>();
        LocalDateTime since = lastCheck == null || now.isBefore(lastCheck)
                ? now.withSecond(0).withNano(0).minusNanos(1) : lastCheck;
        if (since.isBefore(now.minusDays(1))) since = now.minusDays(1);
        lastCheck = now;
        for (Alarm alarm : alarms) {
            if (!alarm.enabled) {
                continue;
            }
            try {
                LocalTime alarmTime = LocalTime.parse(alarm.time);
                LocalDate date = now.toLocalDate();
                LocalDateTime occurrence = date.atTime(alarmTime);
                if (occurrence.isAfter(now)) {
                    date = date.minusDays(1);
                    occurrence = date.atTime(alarmTime);
                }
                if (occurrence.isAfter(since) && !date.equals(alarm.lastTriggeredDate)
                        && matchesRepeat(alarm, date.getDayOfWeek())) {
                    alarm.lastTriggeredDate = date;
                    if ("ONCE".equals(alarm.repeatMode)) {
                        alarm.enabled = false;
                    }
                    due.add(alarm);
                }
            } catch (RuntimeException ex) {
                LOGGER.log(Level.FINE, "Ignoring invalid alarm time");
            }
        }
        return due;
    }

    private boolean matchesRepeat(Alarm alarm, DayOfWeek day) {
        return switch (alarm.repeatMode) {
            case "ONCE", "DAILY" -> true;
            case "WEEKDAYS" -> day.getValue() <= 5;
            case "CUSTOM" -> alarm.repeatDays != null && alarm.repeatDays[day.getValue() - 1];
            default -> false;
        };
    }
}
