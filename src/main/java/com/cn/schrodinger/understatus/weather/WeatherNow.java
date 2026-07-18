package com.cn.schrodinger.understatus.weather;

import java.time.OffsetDateTime;

public record WeatherNow(OffsetDateTime updateTime, int temperatureCelsius,
        int feelsLikeCelsius, String iconCode, String condition, int windDegrees,
        String windDirection, String windScale, int windSpeedKph, int humidityPercent,
        double precipitationMm, int pressureHpa, double visibilityKm,
        Integer cloudPercent, Integer dewPointCelsius, WeatherReference reference) {}
