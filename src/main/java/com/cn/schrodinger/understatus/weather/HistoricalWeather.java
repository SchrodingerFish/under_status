package com.cn.schrodinger.understatus.weather;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record HistoricalWeather(Daily daily, List<Hourly> hourly,
        WeatherReference reference) {
    public HistoricalWeather {
        hourly = List.copyOf(hourly);
    }

    public record Daily(LocalDate date, String sunrise, String sunset,
            String moonrise, String moonset, String moonPhase,
            int maximumTemperatureCelsius, int minimumTemperatureCelsius,
            int humidityPercent, double precipitationMm, int pressureHpa) {}

    public record Hourly(LocalDateTime time, int temperatureCelsius,
            String iconCode, String condition, double precipitationMm,
            int windDegrees, String windDirection, String windScale,
            int windSpeedKph, int humidityPercent, int pressureHpa) {}
}
