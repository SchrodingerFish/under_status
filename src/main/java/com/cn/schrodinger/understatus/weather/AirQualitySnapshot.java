package com.cn.schrodinger.understatus.weather;

import java.time.OffsetDateTime;
import java.util.Map;

public record AirQualitySnapshot(OffsetDateTime updateTime, int aqi, String category,
        String primaryPollutant, Map<String, Double> pollutants,
        String attributionTag, WeatherReference reference) {
    public AirQualitySnapshot {
        pollutants = pollutants == null ? Map.of() : Map.copyOf(pollutants);
        attributionTag = attributionTag == null ? "" : attributionTag;
    }
}
