package com.cn.schrodinger.understatus.weather;

public enum WeatherIndexRange {
    ONE_DAY("1d"), THREE_DAYS("3d");

    private final String path;

    WeatherIndexRange(String path) { this.path = path; }

    public String path() { return path; }
}
