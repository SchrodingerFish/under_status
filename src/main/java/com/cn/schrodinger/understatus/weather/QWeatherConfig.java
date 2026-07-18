package com.cn.schrodinger.understatus.weather;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** Validated connection settings for the QWeather Web API. */
public record QWeatherConfig(String apiHost, String apiKey, String language, String unit) {

    private static final Pattern HOST = Pattern.compile(
            "(?i)^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+qweatherapi\\.com$");

    public QWeatherConfig {
        apiHost = normalize(apiHost);
        apiKey = normalize(apiKey);
        language = normalize(language);
        unit = normalize(unit);
        if (!HOST.matcher(apiHost).matches()) {
            throw new IllegalArgumentException("API Host 必须是和风天气控制台提供的专属主机名");
        }
        if (language.isEmpty()) {
            language = "zh";
        }
        if (unit.isEmpty()) {
            unit = "m";
        }
        if (!"m".equals(unit) && !"i".equals(unit)) {
            throw new IllegalArgumentException("天气单位必须是 m 或 i");
        }
    }

    public boolean isReady() {
        return !apiHost.isEmpty() && !apiKey.isEmpty();
    }

    public URI endpoint(String path, Map<String, String> query) {
        Objects.requireNonNull(path, "path");
        if (!path.startsWith("/") || path.contains("?") || path.contains("#")) {
            throw new IllegalArgumentException("API 路径必须是以 / 开头且不含查询参数的路径");
        }
        StringBuilder value = new StringBuilder("https://").append(apiHost).append(path);
        if (query != null && !query.isEmpty()) {
            boolean first = true;
            for (Map.Entry<String, String> entry : new TreeMap<>(query).entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    continue;
                }
                value.append(first ? '?' : '&');
                first = false;
                value.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
            }
        }
        return URI.create(value.toString());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
