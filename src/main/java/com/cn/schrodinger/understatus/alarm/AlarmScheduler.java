package com.cn.schrodinger.understatus.alarm;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AlarmScheduler {

    private static final Logger LOGGER = Logger.getLogger(AlarmScheduler.class.getName());
    private LocalDateTime previousCheck;

    public List<Alarm> dueAlarms(List<Alarm> alarms, LocalDateTime now) {
        LocalDateTime start = previousCheck == null || previousCheck.isAfter(now)
                ? now.withSecond(0).withNano(0).minusNanos(1) : previousCheck;
        if (start.isBefore(now.minusMinutes(5))) start = now.minusMinutes(5);
        previousCheck = now;
        List<Alarm> due = new ArrayList<>();
        for (Alarm alarm : alarms) {
            if (!alarm.enabled || alarm.lastTriggeredDate != null && alarm.lastTriggeredDate.equals(now.toLocalDate())) {
                continue;
            }
            try {
                LocalTime alarmTime = LocalTime.parse(alarm.time);
                LocalDateTime scheduled = now.toLocalDate().atTime(alarmTime);
                if (scheduled.isAfter(now)) scheduled = scheduled.minusDays(1);
                if (!scheduled.isAfter(start) || scheduled.toLocalDate().equals(alarm.lastTriggeredDate)) {
                    continue;
                }
                if (matchesRepeat(alarm, scheduled.getDayOfWeek())) {
                    alarm.lastTriggeredDate = scheduled.toLocalDate();
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
