package com.cn.schrodinger.understatus.toolbox.core;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lightweight Cron expression fields descriptor.
 * Translates Cron schedule layouts into Chinese human-readable text.
 *
 * @author peter/antigravity
 */
public class CronExplainer {

    public static String explainCron(String cron) {
        if (cron == null || cron.trim().isEmpty()) {
            return "";
        }
        cron = cron.trim();
        String[] parts = cron.split("\\s+");
        if (parts.length < 5 || parts.length > 7) {
            return "无效的 Cron 表达式格式 (Must have 5 to 7 fields separated by spaces)";
        }
        try {
            String sec = "0";
            String min, hour, dom, month, dow, year = "*";
            if (parts.length == 5) {
                min = parts[0];
                hour = parts[1];
                dom = parts[2];
                month = parts[3];
                dow = parts[4];
            } else {
                sec = parts[0];
                min = parts[1];
                hour = parts[2];
                dom = parts[3];
                month = parts[4];
                dow = parts[5];
                if (parts.length == 7) {
                    year = parts[6];
                }
            }

            StringBuilder desc = new StringBuilder("字段解释 (Fields):\n");
            desc.append("秒数 (Seconds): ").append(translateField(sec, "每秒")).append("\n");
            desc.append("分钟 (Minutes): ").append(translateField(min, "每分钟")).append("\n");
            desc.append("小时 (Hours):   ").append(translateField(hour, "每小时")).append("\n");
            desc.append("日期 (Days):    ").append(translateField(dom, "每天")).append("\n");
            desc.append("月份 (Months):  ").append(translateField(month, "每月")).append("\n");
            desc.append("星期 (Weekdays):").append(translateField(dow, "每周")).append("\n");
            if (!year.equals("*")) {
                desc.append("年份 (Years):   ").append(translateField(year, "每年")).append("\n");
            }
            
            desc.append("\n运行计划摘要 (Schedule Summary):\n");
            desc.append("在 ").append(explainFieldText(month, "月")).append(" 的 ")
                .append(explainFieldText(dom, "号 (或星期 " + explainFieldText(dow, "") + ")")).append("，")
                .append(explainFieldText(hour, "点")).append(" ")
                .append(explainFieldText(min, "分")).append(" ")
                .append(explainFieldText(sec, "秒")).append(" 执行。\n");

            List<java.time.LocalDateTime> nextRuns = getNextExecutionTimes(cron, 5);
            if (!nextRuns.isEmpty()) {
                desc.append("\n未来 5 次执行时间预估 (Next 5 Execution Runs):\n");
                java.time.format.DateTimeFormatter dtf = 
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss (EEEE)", java.util.Locale.CHINESE);
                for (int i = 0; i < nextRuns.size(); i++) {
                    desc.append(String.format("  [%d] %s\n", i + 1, nextRuns.get(i).format(dtf)));
                }
            }

            return desc.toString();
        } catch (Exception ex) {
            return "Cron 解析失败: " + ex.getMessage();
        }
    }

    public static java.util.List<java.time.LocalDateTime> getNextExecutionTimes(String cron, int count) {
        java.util.List<java.time.LocalDateTime> result = new java.util.ArrayList<>();
        if (cron == null || cron.trim().isEmpty()) {
            return result;
        }
        String[] parts = cron.trim().split("\\s+");
        if (parts.length < 5 || parts.length > 7) {
            return result;
        }
        String sec = "0", min, hour, dom, month, dow;
        if (parts.length == 5) {
            min = parts[0]; hour = parts[1]; dom = parts[2]; month = parts[3]; dow = parts[4];
        } else {
            sec = parts[0]; min = parts[1]; hour = parts[2]; dom = parts[3]; month = parts[4]; dow = parts[5];
        }

        // Pre-compile fields into fast bitmasks once (O(1) checks without string splits in loops)
        java.util.BitSet allowedSec = parseCronField(sec, 0, 59);
        java.util.BitSet allowedMin = parseCronField(min, 0, 59);
        java.util.BitSet allowedHour = parseCronField(hour, 0, 23);
        java.util.BitSet allowedDom = parseCronField(dom, 1, 31);
        java.util.BitSet allowedMonth = parseCronField(month, 1, 12);

        String p = dow.toUpperCase()
                .replace("SUN", "1").replace("MON", "2").replace("TUE", "3")
                .replace("WED", "4").replace("THU", "5").replace("FRI", "6").replace("SAT", "7");
        java.util.BitSet allowedDow = parseCronField(p, 1, 7);

        boolean domWild = dom.equals("*") || dom.equals("?");
        boolean dowWild = dow.equals("*") || dow.equals("?");

        java.time.LocalDateTime curr = java.time.LocalDateTime.now().withNano(0).plusSeconds(1);

        int maxIterations = 20000;
        while (result.size() < count && maxIterations-- > 0) {
            // Fast skip month
            if (!allowedMonth.get(curr.getMonthValue())) {
                curr = curr.plusMonths(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
                continue;
            }

            // Fast skip day of month
            if (!domWild && !allowedDom.get(curr.getDayOfMonth())) {
                curr = curr.plusDays(1).withHour(0).withMinute(0).withSecond(0);
                continue;
            }

            // Fast skip day of week
            if (!dowWild) {
                int javaDow = curr.getDayOfWeek().getValue(); // 1=Mon..7=Sun
                int quartzDow = (javaDow == 7) ? 1 : javaDow + 1; // 1=Sun..7=Sat
                int linuxDow = (javaDow == 7) ? 0 : javaDow; // 0=Sun..6=Sat
                if (!allowedDow.get(quartzDow) && !allowedDow.get(linuxDow) && !allowedDow.get(javaDow)) {
                    curr = curr.plusDays(1).withHour(0).withMinute(0).withSecond(0);
                    continue;
                }
            }

            // Fast skip hour
            if (!allowedHour.get(curr.getHour())) {
                curr = curr.plusHours(1).withMinute(0).withSecond(0);
                continue;
            }

            // Fast skip minute
            if (!allowedMin.get(curr.getMinute())) {
                curr = curr.plusMinutes(1).withSecond(0);
                continue;
            }

            // Check second
            int nextSec = allowedSec.nextSetBit(curr.getSecond());
            if (nextSec < 0 || nextSec > 59) {
                curr = curr.plusMinutes(1).withSecond(0);
                continue;
            }

            curr = curr.withSecond(nextSec);
            result.add(curr);
            curr = curr.plusSeconds(1);
        }
        return result;
    }

    private static java.util.BitSet parseCronField(String field, int min, int max) {
        java.util.BitSet bs = new java.util.BitSet(max + 1);
        if (field.equals("*") || field.equals("?")) {
            bs.set(min, max + 1);
            return bs;
        }
        for (String part : field.split(",")) {
            part = part.trim();
            if (part.equals("*") || part.equals("?")) {
                bs.set(min, max + 1);
            } else if (part.contains("/")) {
                String[] slash = part.split("/");
                int start = slash[0].equals("*") ? min : Integer.parseInt(slash[0]);
                int step = Integer.parseInt(slash[1]);
                for (int v = start; v <= max; v += step) {
                    bs.set(v);
                }
            } else if (part.contains("-")) {
                String[] dash = part.split("-");
                int start = Integer.parseInt(dash[0]);
                int end = Integer.parseInt(dash[1]);
                for (int v = start; v <= end; v++) {
                    bs.set(v);
                }
            } else {
                try {
                    bs.set(Integer.parseInt(part));
                } catch (NumberFormatException ignored) {}
            }
        }
        return bs;
    }

    private static String translateField(String val, String def) {
        if (val.equals("*")) return def;
        if (val.equals("?")) return "不指定";
        if (val.contains("/")) {
            String[] p = val.split("/");
            String start = p[0].equals("*") ? "0" : p[0];
            return "从第 " + start + " 开始，每隔 " + p[1] + " 执行一次";
        }
        if (val.contains("-")) {
            return "在区间 " + val + " 内执行";
        }
        return "在指定值 [" + val + "] 执行";
    }

    private static String explainFieldText(String val, String unit) {
        if (val.equals("*")) return "每" + unit;
        if (val.equals("?")) return "任意";
        if (val.contains("/")) {
            String[] p = val.split("/");
            String start = p[0].equals("*") ? "0" : p[0];
            return "从第 " + start + unit + "开始，每 " + p[1] + " " + unit;
        }
        return val + " " + unit;
    }
}
