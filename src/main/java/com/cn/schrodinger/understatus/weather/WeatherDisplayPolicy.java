package com.cn.schrodinger.understatus.weather;

import java.util.Objects;

public final class WeatherDisplayPolicy {

    private WeatherDisplayPolicy() {}

    public static WeatherDisplayState evaluate(
            boolean previousVisible,
            String previousApiHost,
            String previousApiKey,
            String previousCity,
            boolean previousAutoIp,
            boolean visible,
            String apiHost,
            String apiKey,
            String city,
            boolean autoIp) {
        if (!visible) {
            return new WeatherDisplayState(false, "", false);
        }
        if (apiHost == null || apiHost.isBlank() || apiKey == null || apiKey.isBlank()) {
            return new WeatherDisplayState(true, "🌤 天气：待配置", false);
        }
        boolean changed = previousVisible != visible
                || !Objects.equals(previousApiHost, apiHost)
                || !Objects.equals(previousApiKey, apiKey)
                || !Objects.equals(previousCity, city)
                || previousAutoIp != autoIp;
        return new WeatherDisplayState(true, changed ? "🌤 正在加载天气…" : "", changed);
    }
}
