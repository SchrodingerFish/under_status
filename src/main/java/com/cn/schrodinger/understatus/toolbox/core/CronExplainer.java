package com.cn.schrodinger.understatus.toolbox.core;

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
                .append(explainFieldText(sec, "秒")).append(" 执行。");

            return desc.toString();
        } catch (Exception ex) {
            return "Cron 解析失败: " + ex.getMessage();
        }
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
