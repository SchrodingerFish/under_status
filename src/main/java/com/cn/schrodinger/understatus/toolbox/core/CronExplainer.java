package com.cn.schrodinger.understatus.toolbox.core;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;

/** Bounded Unix (five fields) and Quartz (six/seven fields) schedule preview. */
public final class CronExplainer {
    private static final int FIRST_YEAR = 1970;
    private static final int LAST_YEAR = 2199;
    private static final String[] MONTHS = {"JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"};
    private static final String[] WEEKDAYS = {"SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"};

    private CronExplainer() {}

    public static String explainCron(String cron) { return explainCron(cron, Clock.systemDefaultZone()); }

    public static String explainCron(String cron, Clock clock) {
        if (cron == null || cron.isBlank()) return "";
        try {
            Schedule schedule = parse(cron);
            StringBuilder text = new StringBuilder(schedule.quartz
                    ? "Quartz (6/7 字段): 星期 1=SUN … 7=SAT；日期/星期必须有一个 ?。\n"
                    : "Unix (5 字段): 星期 0/7=SUN … 6=SAT；日期和星期均受限时取并集。\n");
            text.append("表达式: ").append(cron.trim()).append("\n时区: ").append(clock.getZone())
                    .append("\n支持 *, 列表, 范围, 正整数步长；年份范围 1970–2199。\n")
                    .append("预览使用本地日历时间，夏令时切换处不代表调度器实际触发次数。\n");
            String[] raw = cron.trim().split("\\s+");
            String[] labels = schedule.quartz
                    ? new String[]{"秒", "分钟", "小时", "日期", "月份", "星期", "年份"}
                    : new String[]{"分钟", "小时", "日期", "月份", "星期"};
            for (int i = 0; i < raw.length; i++) {
                text.append(labels[i]).append(": ").append(describe(raw[i])).append('\n');
            }
            List<LocalDateTime> runs = getNextExecutionTimes(cron, 5, clock);
            text.append("\n未来执行时间 (Next runs):\n");
            for (LocalDateTime run : runs) text.append(run.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss EEEE", Locale.CHINESE))).append('\n');
            if (runs.isEmpty()) text.append("截至 2199 年没有匹配时间。\n");
            return text.toString();
        } catch (IllegalArgumentException ex) {
            return "Cron 解析失败: " + ex.getMessage();
        }
    }

    private static String describe(String value) {
        if (value.equals("*")) return "任意值";
        if (value.equals("?")) return "不指定（由另一日期字段决定）";
        if (value.contains(",")) return "指定列表 " + value;
        if (value.contains("/")) {
            String[] step = value.split("/");
            return "在 " + (step[0].equals("*") ? "完整范围" : step[0]) + " 内，每隔 " + step[1] + " 个单位";
        }
        if (value.contains("-")) return "指定范围 " + value;
        return "指定值 " + value;
    }

    public static List<LocalDateTime> getNextExecutionTimes(String cron, int count) {
        return getNextExecutionTimes(cron, count, Clock.systemDefaultZone());
    }

    public static List<LocalDateTime> getNextExecutionTimes(String cron, int count, Clock clock) {
        if (count < 0 || count > 1000) throw new IllegalArgumentException("Count must be between 0 and 1000");
        Schedule s = parse(cron);
        List<LocalDateTime> result = new ArrayList<>();
        LocalDateTime current = LocalDateTime.now(clock).withNano(0).plusSeconds(1);
        if (current.getYear() < FIRST_YEAR) current = LocalDateTime.of(FIRST_YEAR, 1, 1, 0, 0);
        while (current.getYear() <= LAST_YEAR && result.size() < count) {
            ToolLimits.checkInterrupted();
            int year = s.years.nextSetBit(current.getYear());
            if (year < 0) break;
            if (year != current.getYear()) { current = LocalDateTime.of(year, 1, 1, 0, 0); continue; }
            if (!s.months.get(current.getMonthValue())) {
                current = current.withDayOfMonth(1).plusMonths(1).withHour(0).withMinute(0).withSecond(0);
                continue;
            }
            int weekday = s.quartz ? current.getDayOfWeek().getValue() % 7 + 1 : current.getDayOfWeek().getValue() % 7;
            boolean day = s.days.get(current.getDayOfMonth());
            boolean week = s.weekdays.get(weekday);
            boolean matchesDay = s.quartz ? (s.dayAny ? week : day)
                    : (s.dayAny || s.weekAny ? day && week : day || week);
            if (!matchesDay) { current = current.plusDays(1).withHour(0).withMinute(0).withSecond(0); continue; }
            int hour = s.hours.nextSetBit(current.getHour());
            if (hour < 0) { current = current.plusDays(1).withHour(0).withMinute(0).withSecond(0); continue; }
            if (hour != current.getHour()) { current = current.withHour(hour).withMinute(0).withSecond(0); }
            int minute = s.minutes.nextSetBit(current.getMinute());
            if (minute < 0) { current = current.plusHours(1).withMinute(0).withSecond(0); continue; }
            if (minute != current.getMinute()) current = current.withMinute(minute).withSecond(0);
            int second = s.seconds.nextSetBit(current.getSecond());
            if (second < 0) { current = current.plusMinutes(1).withSecond(0); continue; }
            current = current.withSecond(second);
            result.add(current);
            current = current.plusSeconds(1);
        }
        return result;
    }

    private record Schedule(boolean quartz, BitSet seconds, BitSet minutes, BitSet hours, BitSet days,
            BitSet months, BitSet weekdays, BitSet years, boolean dayAny, boolean weekAny) {}

    private static Schedule parse(String cron) {
        if (cron == null || cron.isBlank() || cron.length() > 512) throw new IllegalArgumentException("Expected a Cron expression (maximum 512 characters)");
        String[] parts = cron.trim().toUpperCase(Locale.ROOT).split("\\s+");
        if (parts.length < 5 || parts.length > 7) throw new IllegalArgumentException("Expected five Unix or six/seven Quartz fields");
        boolean quartz = parts.length != 5;
        int offset = quartz ? 1 : 0;
        String dom = parts[offset + 2], dow = parts[offset + 4];
        if (quartz && dom.equals("?") == dow.equals("?")) throw new IllegalArgumentException("Quartz requires exactly one ? in day-of-month/day-of-week");
        String month = names(parts[offset + 3], MONTHS, 1);
        dow = names(dow, WEEKDAYS, quartz ? 1 : 0);
        BitSet weekdays = field(dow, quartz ? 1 : 0, 7, quartz);
        if (!quartz && weekdays.get(7)) { weekdays.set(0); weekdays.clear(7); }
        return new Schedule(quartz, field(quartz ? parts[0] : "0", 0, 59, false),
                field(parts[offset], 0, 59, false), field(parts[offset + 1], 0, 23, false),
                field(dom, 1, 31, quartz), field(month, 1, 12, false), weekdays,
                field(parts.length == 7 ? parts[6] : "*", FIRST_YEAR, LAST_YEAR, false),
                quartz ? dom.equals("?") : dom.startsWith("*"),
                quartz ? dow.equals("?") : dow.startsWith("*"));
    }

    private static String names(String text, String[] names, int first) {
        for (int i = 0; i < names.length; i++) text = text.replaceAll("\\b" + names[i] + "\\b", Integer.toString(i + first));
        return text;
    }

    private static BitSet field(String text, int minimum, int maximum, boolean allowQuestion) {
        BitSet values = new BitSet(maximum + 1);
        if (text.equals("?") && allowQuestion) { values.set(minimum, maximum + 1); return values; }
        for (String item : text.split(",", -1)) {
            String[] stepParts = item.split("/", -1);
            if (stepParts.length > 2) throw new IllegalArgumentException("Malformed step: " + text);
            int step = stepParts.length == 2 ? number(stepParts[1], 1, maximum - minimum + 1) : 1;
            String[] range = stepParts[0].split("-", -1);
            int start, end;
            if (stepParts[0].equals("*")) { start = minimum; end = maximum; }
            else if (range.length == 2) {
                start = number(range[0], minimum, maximum);
                end = number(range[1], minimum, maximum);
                if (start > end) throw new IllegalArgumentException("Descending ranges are unsupported: " + text);
            } else if (range.length == 1) {
                start = number(range[0], minimum, maximum);
                end = stepParts.length == 2 ? maximum : start;
            } else throw new IllegalArgumentException("Malformed range: " + text);
            for (int value = start; value <= end; value += step) values.set(value);
        }
        return values;
    }

    private static int number(String text, int minimum, int maximum) {
        if (!text.matches("[0-9]{1,4}")) throw new IllegalArgumentException("Unsupported or malformed field: " + text);
        int value = Integer.parseInt(text);
        if (value < minimum || value > maximum) throw new IllegalArgumentException("Value out of range " + minimum + "–" + maximum + ": " + text);
        return value;
    }
}
