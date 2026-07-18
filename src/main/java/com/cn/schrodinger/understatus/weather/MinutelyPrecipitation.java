package com.cn.schrodinger.understatus.weather;

import java.time.OffsetDateTime;
import java.util.List;

public record MinutelyPrecipitation(OffsetDateTime updateTime, String summary,
        List<Point> points, WeatherReference reference) {
    public MinutelyPrecipitation { points = List.copyOf(points); }

    public record Point(OffsetDateTime time, double precipitationMm, String type) {}
}
