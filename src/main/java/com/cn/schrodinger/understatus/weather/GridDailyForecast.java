package com.cn.schrodinger.understatus.weather;

import java.time.LocalDate;

public record GridDailyForecast(LocalDate date, int maximumTemperatureCelsius,
        int minimumTemperatureCelsius, String dayIconCode, String dayCondition,
        String nightIconCode, String nightCondition, String dayWindDirection,
        String dayWindScale, int humidityPercent, double precipitationMm,
        int pressureHpa, WeatherReference reference) {}
