package com.cn.schrodinger.understatus.toolbox.core;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Locale;

/** Five fields use UNIX numbering; six/seven fields use Quartz numbering. */
public final class CronExplainer {
    private CronExplainer() {}

    public static String explainCron(String cron) {
        if (cron == null || cron.isBlank()) return "";
        try {
            List<LocalDateTime> runs = getNextExecutionTimes(cron, 5);
            String dialect = cron.trim().split("\\s+").length == 5 ? "UNIX（星期 0/7=日）" : "Quartz（星期 1=日）";
            StringBuilder text = new StringBuilder("Cron 方言: ").append(dialect)
                    .append("\n支持数字、名称、*、范围、列表、正整数步长；? 仅用于 Quartz 日期/星期。\n")
                    .append("未来执行时间（系统时区，最晚 2199 年）:\n");
            DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss (EEEE)", Locale.CHINESE);
            for (LocalDateTime run : runs) text.append(run.format(format)).append('\n');
            if (runs.isEmpty()) text.append("指定范围内没有执行时间");
            return text.toString();
        } catch (IllegalArgumentException ex) {
            return "Cron 解析失败: " + ex.getMessage();
        }
    }

    public static List<LocalDateTime> getNextExecutionTimes(String cron, int count) {
        return getNextExecutionTimes(cron, count, Clock.systemDefaultZone());
    }

    public static List<LocalDateTime> getNextExecutionTimes(String cron, int count, Clock clock) {
        if (cron == null || cron.isBlank()) return List.of();
        if (count < 0 || count > 1000) throw new IllegalArgumentException("次数必须为 0~1000");
        String[] f = cron.trim().toUpperCase(Locale.ROOT).split("\\s+");
        if (f.length < 5 || f.length > 7) throw new IllegalArgumentException("需要 5、6 或 7 个字段");
        boolean unix = f.length == 5;
        int offset = unix ? 0 : 1;
        String dom = f[2 + offset], dow = f[4 + offset];
        if (unix && cron.contains("?")) throw new IllegalArgumentException("UNIX 不支持 ?");
        if (!unix && !wild(dom) && !wild(dow)) throw new IllegalArgumentException("Quartz 日期与星期不能同时指定");
        BitSet seconds = field(unix ? "0" : f[0], 0, 59);
        BitSet minutes = field(f[offset], 0, 59);
        BitSet hours = field(f[1 + offset], 0, 23);
        BitSet days = field(dom.equals("?") ? "*" : dom, 1, 31);
        BitSet months = field(names(f[3 + offset], "JAN FEB MAR APR MAY JUN JUL AUG SEP OCT NOV DEC", 1), 1, 12);
        BitSet weekdays = field(names(dow.equals("?") ? "*" : dow, "SUN MON TUE WED THU FRI SAT", unix ? 0 : 1), unix ? 0 : 1, 7);
        BitSet years = field(f.length == 7 ? f[6] : "*", 1970, 2199);
        List<LocalDateTime> result = new ArrayList<>();
        LocalDateTime current = LocalDateTime.now(clock).withNano(0).plusSeconds(1);
        while (result.size() < count && current.getYear() <= 2199) {
            if (Thread.currentThread().isInterrupted()) throw new IllegalArgumentException("计算已取消");
            int year = years.nextSetBit(current.getYear());
            if (year < 0) break;
            if (year != current.getYear()) current = LocalDateTime.of(year, 1, 1, 0, 0);
            if (!months.get(current.getMonthValue())) {
                current = current.withDayOfMonth(1).plusMonths(1).toLocalDate().atStartOfDay();
                continue;
            }
            int weekday = current.getDayOfWeek().getValue();
            boolean dayMatch = days.get(current.getDayOfMonth());
            boolean weekMatch = unix ? weekdays.get(weekday % 7) || weekday == 7 && weekdays.get(7)
                    : weekdays.get(weekday % 7 + 1);
            boolean matches = unix && !wild(dom) && !wild(dow) ? dayMatch || weekMatch
                    : (wild(dom) || dayMatch) && (wild(dow) || weekMatch);
            if (!matches) { current = current.toLocalDate().plusDays(1).atStartOfDay(); continue; }
            int hour = hours.nextSetBit(current.getHour());
            if (hour < 0) { current = current.toLocalDate().plusDays(1).atStartOfDay(); continue; }
            if (hour != current.getHour()) current = current.withHour(hour).withMinute(0).withSecond(0);
            int minute = minutes.nextSetBit(current.getMinute());
            if (minute < 0) { current = current.plusHours(1).withMinute(0).withSecond(0); continue; }
            if (minute != current.getMinute()) current = current.withMinute(minute).withSecond(0);
            int second = seconds.nextSetBit(current.getSecond());
            if (second < 0) { current = current.plusMinutes(1).withSecond(0); continue; }
            current = current.withSecond(second);
            result.add(current);
            current = current.plusSeconds(1);
        }
        return List.copyOf(result);
    }

    private static boolean wild(String field) { return field.equals("*") || field.equals("?"); }

    private static String names(String value, String names, int start) {
        String[] list = names.split(" ");
        for (int i = 0; i < list.length; i++) value = value.replace(list[i], Integer.toString(i + start));
        return value;
    }

    private static BitSet field(String value, int min, int max) {
        BitSet bits = new BitSet(max + 1);
        for (String part : value.split(",", -1)) {
            String[] stepParts = part.split("/", -1);
            if (stepParts.length > 2) throw new IllegalArgumentException("无效步长: " + value);
            int step = stepParts.length == 2 ? number(stepParts[1]) : 1;
            if (step <= 0 || step > max + 1) throw new IllegalArgumentException("步长必须是有效正整数");
            String[] range = stepParts[0].split("-", -1);
            if (range.length > 2) throw new IllegalArgumentException("无效范围: " + value);
            int from = range[0].equals("*") ? min : number(range[0]);
            int to = range.length == 2 ? number(range[1])
                    : range[0].equals("*") || stepParts.length == 2 ? max : from;
            if (from < min || to > max || from > to) throw new IllegalArgumentException("字段超出范围 " + min + "~" + max);
            for (int n = from; n <= to; n += step) bits.set(n);
        }
        return bits;
    }

    private static int number(String text) {
        if (!text.matches("[0-9]{1,4}")) throw new IllegalArgumentException("不支持的字段: " + text);
        return Integer.parseInt(text);
    }
}
