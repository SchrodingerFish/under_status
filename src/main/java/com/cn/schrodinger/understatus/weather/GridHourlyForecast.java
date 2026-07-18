package com.cn.schrodinger.understatus.weather;

import java.time.OffsetDateTime;

public record GridHourlyForecast(OffsetDateTime time, int temperatureCelsius,
        String iconCode, String condition, int windDegrees, String windDirection,
        String windScale, int windSpeedKph, int humidityPercent,
        double precipitationMm, int pressureHpa, Integer cloudPercent,
        Integer dewPointCelsius, WeatherReference reference) {}
