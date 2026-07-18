package com.cn.schrodinger.understatus.weather;

import java.time.OffsetDateTime;

public record HourlyForecast(OffsetDateTime time, int temperatureCelsius,
        String condition, String iconCode, Integer precipitationProbability,
        double precipitationMm, int windDegrees, String windDirection,
        String windScale, int windSpeedKph, int humidityPercent, int pressureHpa,
        Integer cloudPercent, Integer dewPointCelsius, WeatherReference reference) {

    public HourlyForecast(OffsetDateTime time, int temperatureCelsius, String condition) {
        this(time, temperatureCelsius, condition, "", null, 0, 0, "", "", 0,
                0, 0, null, null, WeatherReference.EMPTY);
    }
}
