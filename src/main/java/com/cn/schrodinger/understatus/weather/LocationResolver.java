package com.cn.schrodinger.understatus.weather;

import java.net.URI;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

public final class LocationResolver {

    private static final URI IP_LOOKUP = URI.create("https://ipwho.is/?fields=success,city");
    private final HttpTransport transport;

    public LocationResolver(HttpTransport transport) {
        this.transport = transport;
    }

    public LocationContext resolve(QWeatherConfig config, String city, boolean autoIp)
            throws WeatherException {
        String fallback = city == null || city.isBlank() ? "北京" : city.trim();
        String target = autoIp ? resolveByIp(fallback) : fallback;
        URI uri = config.endpoint("/geo/v2/city/lookup",
                Map.of("location", target, "lang", config.language()));
        try {
            String body = transport.get(uri, auth(config));
            JsonValue.ObjectValue root = JsonParser.parse(body).asObject();
            requireSuccess(root);
            List<JsonValue> locations = root.required("location").asArray();
            if (locations.isEmpty()) {
                throw new WeatherException(WeatherException.Kind.UNSUPPORTED,
                        "未找到配置的城市");
            }
            JsonValue.ObjectValue item = locations.get(0).asObject();
            return new LocationContext(text(item, "id"), text(item, "name"),
                    Double.parseDouble(text(item, "lon")),
                    Double.parseDouble(text(item, "lat")),
                    zone(text(item, "tz")));
        } catch (WeatherException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new WeatherException(WeatherException.Kind.NETWORK,
                    "无法解析天气地点", ex);
        }
    }

    private String resolveByIp(String fallback) {
        try {
            JsonValue.ObjectValue root = JsonParser.parse(transport.get(IP_LOOKUP)).asObject();
            boolean success = root.optional("success").isEmpty()
                    || root.required("success").asBoolean();
            String city = success ? text(root, "city") : "";
            return city.isBlank() ? fallback : city;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static Map<String, String> auth(QWeatherConfig config) {
        return Map.of("X-QW-Api-Key", config.apiKey());
    }

    private static void requireSuccess(JsonValue.ObjectValue root) throws WeatherException {
        String code = text(root, "code");
        if (!"200".equals(code)) {
            throw QWeatherClient.errorForCode(code);
        }
    }

    private static String text(JsonValue.ObjectValue value, String key) throws WeatherException {
        return value.required(key).asString();
    }

    private static ZoneId zone(String value) {
        try {
            return ZoneId.of(value);
        } catch (RuntimeException ex) {
            return ZoneId.of("UTC");
        }
    }
}
