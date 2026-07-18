package com.cn.schrodinger.understatus.weather;

public enum ForecastRange {
    DAYS_3("3d", Type.DAILY),
    DAYS_7("7d", Type.DAILY),
    DAYS_10("10d", Type.DAILY),
    DAYS_15("15d", Type.DAILY),
    DAYS_30("30d", Type.DAILY),
    HOURS_24("24h", Type.HOURLY),
    HOURS_72("72h", Type.HOURLY),
    HOURS_168("168h", Type.HOURLY);

    public enum Type { DAILY, HOURLY }

    private final String path;
    private final Type type;

    ForecastRange(String path, Type type) {
        this.path = path;
        this.type = type;
    }

    public String path() { return path; }

    public Type type() { return type; }

    public static ForecastRange hourly(int hours) {
        return switch (hours) {
            case 24 -> HOURS_24;
            case 72 -> HOURS_72;
            case 168 -> HOURS_168;
            default -> throw new IllegalArgumentException("逐小时预报仅支持 24、72 或 168 小时");
        };
    }
}
