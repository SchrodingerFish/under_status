package com.cn.schrodinger.understatus.alarm;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Model class representing a single configured alarm clock.
 * Handles serialization/deserialization to/from a flat string for persistence.
 *
 * @author peter/antigravity
 */
public class Alarm {
    public String id;
    public String time; // Format: "HH:mm"
    public String message;
    public boolean enabled;
    public String repeatMode; // "ONCE", "DAILY", "WEEKDAYS", "CUSTOM"
    public boolean[] repeatDays = new boolean[7]; // Index 0=Mon, 6=Sun

    // Runtime state (non-persistent)
    public LocalDate lastTriggeredDate = null;

    public Alarm() {
        this.id = String.valueOf(System.currentTimeMillis()) + "_" + (int)(Math.random() * 1000);
        this.time = "12:00";
        this.message = "提醒时间到了！";
        this.enabled = true;
        this.repeatMode = "ONCE";
    }

    public static Alarm once(String time, String message) {
        return new Alarm(java.util.UUID.randomUUID().toString(), time, message, true,
                "ONCE", new boolean[7]);
    }

    public Alarm(String id, String time, String message, boolean enabled, String repeatMode, boolean[] repeatDays) {
        this.id = id;
        this.time = time;
        this.message = message;
        this.enabled = enabled;
        this.repeatMode = repeatMode;
        if (repeatDays != null && repeatDays.length == 7) {
            System.arraycopy(repeatDays, 0, this.repeatDays, 0, 7);
        }
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append(id).append("||");
        sb.append(time).append("||");
        
        // Base64 encode message to safely store special characters and delimiters
        String encodedMsg = Base64.getEncoder().encodeToString(message.getBytes(StandardCharsets.UTF_8));
        sb.append(encodedMsg).append("||");
        
        sb.append(enabled).append("||");
        sb.append(repeatMode).append("||");
        
        for (boolean day : repeatDays) {
            sb.append(day ? "1" : "0");
        }
        return sb.toString();
    }

    public static Alarm deserialize(String line) {
        try {
            String[] parts = line.split("\\|\\|");
            if (parts.length < 6) {
                return null;
            }
            String id = parts[0];
            String time = parts[1];
            
            byte[] decodedBytes = Base64.getDecoder().decode(parts[2]);
            String message = new String(decodedBytes, StandardCharsets.UTF_8);
            
            boolean enabled = Boolean.parseBoolean(parts[3]);
            String repeatMode = parts[4];
            
            boolean[] repeatDays = new boolean[7];
            String daysStr = parts[5];
            for (int i = 0; i < 7 && i < daysStr.length(); i++) {
                repeatDays[i] = (daysStr.charAt(i) == '1');
            }
            return new Alarm(id, time, message, enabled, repeatMode, repeatDays);
        } catch (Exception ex) {
            java.util.logging.Logger.getLogger(Alarm.class.getName()).log(java.util.logging.Level.FINE, "Ignoring malformed persisted alarm", ex);
            return null;
        }
    }

    public static String serializeList(List<Alarm> alarms) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < alarms.size(); i++) {
            sb.append(alarms.get(i).serialize());
            if (i < alarms.size() - 1) {
                sb.append("\n"); // Newline separated
            }
        }
        return sb.toString();
    }

    public static List<Alarm> deserializeList(String data) {
        List<Alarm> list = new ArrayList<>();
        if (data == null || data.trim().isEmpty()) {
            return list;
        }
        String[] lines = data.split("\n");
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            Alarm alarm = deserialize(line);
            if (alarm != null) {
                list.add(alarm);
            }
        }
        return list;
    }

    public String getRepeatDescription() {
        if ("ONCE".equals(repeatMode)) {
            return "仅一次";
        } else if ("DAILY".equals(repeatMode)) {
            return "每天";
        } else if ("WEEKDAYS".equals(repeatMode)) {
            return "工作日 (周一至周五)";
        } else if ("CUSTOM".equals(repeatMode)) {
            StringBuilder sb = new StringBuilder("自定义 (");
            String[] weekNames = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
            List<String> activeDays = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                if (repeatDays[i]) {
                    activeDays.add(weekNames[i]);
                }
            }
            if (activeDays.isEmpty()) {
                return "未选择重复天数";
            }
            sb.append(String.join(",", activeDays)).append(")");
            return sb.toString();
        }
        return "未知";
    }
}
