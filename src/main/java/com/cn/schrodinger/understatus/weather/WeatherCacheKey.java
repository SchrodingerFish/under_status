package com.cn.schrodinger.understatus.weather;

import java.time.LocalDate;

public record WeatherCacheKey(String location, String endpoint, String range,
        String language, String unit, LocalDate date) {
    public WeatherCacheKey {
        location = value(location);
        endpoint = value(endpoint);
        range = value(range);
        language = value(language);
        unit = value(unit);
    }

    private static String value(String value) { return value == null ? "" : value; }
}
