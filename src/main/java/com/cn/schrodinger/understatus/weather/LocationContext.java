package com.cn.schrodinger.understatus.weather;

import java.time.ZoneId;
import java.util.Objects;

public record LocationContext(String locationId, String name, double longitude,
        double latitude, ZoneId zoneId) {

    public LocationContext {
        locationId = Objects.requireNonNull(locationId).trim();
        name = Objects.requireNonNull(name).trim();
        zoneId = Objects.requireNonNull(zoneId);
        if (locationId.isEmpty() || name.isEmpty()) {
            throw new IllegalArgumentException("地点 ID 和名称不能为空");
        }
    }

    public String coordinate() {
        return String.format(java.util.Locale.ROOT, "%.2f,%.2f", longitude, latitude);
    }
}
