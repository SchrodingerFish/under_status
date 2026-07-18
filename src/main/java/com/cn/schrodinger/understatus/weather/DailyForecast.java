package com.cn.schrodinger.understatus.weather;

import java.time.LocalDate;

public record DailyForecast(LocalDate date, String sunrise, String sunset,
        String moonrise, String moonset, String moonPhase,
        int maximumTemperatureCelsius, int minimumTemperatureCelsius,
        String dayIconCode, String dayCondition, String nightIconCode,
        String nightCondition, String dayWindDirection, String dayWindScale,
        int humidityPercent, double precipitationMm, int pressureHpa,
        double visibilityKm, Integer cloudPercent, Integer uvIndex,
        WeatherReference reference) {}
