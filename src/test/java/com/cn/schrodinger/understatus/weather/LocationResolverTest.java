package com.cn.schrodinger.understatus.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LocationResolverTest {

    @Test
    void resolvesManualCityToIdCoordinatesAndZone() throws Exception {
        ResolverTransport transport = new ResolverTransport();
        LocationResolver resolver = new LocationResolver(transport);

        LocationContext result = resolver.resolve(new QWeatherConfig(
                "abc.def.qweatherapi.com", "secret", "zh", "m"), "北京", false);

        assertEquals("101010100", result.locationId());
        assertEquals("北京", result.name());
        assertEquals(116.41, result.longitude());
        assertEquals("Asia/Shanghai", result.zoneId().getId());
        assertEquals("secret", transport.headers.get("X-QW-Api-Key"));
    }

    @Test
    void fallsBackToManualCityWhenIpLookupFails() throws Exception {
        ResolverTransport transport = new ResolverTransport();
        transport.failIp = true;

        new LocationResolver(transport).resolve(new QWeatherConfig(
                "abc.def.qweatherapi.com", "secret", "zh", "m"), "上海", true);

        assertEquals("上海", queryValue(transport.geoUri, "location"));
    }

    private static String queryValue(URI uri, String key) {
        for (String item : uri.getQuery().split("&")) {
            String[] parts = item.split("=", 2);
            if (key.equals(parts[0])) {
                return java.net.URLDecoder.decode(parts[1], java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    private static final class ResolverTransport implements HttpTransport {
        boolean failIp;
        URI geoUri;
        Map<String, String> headers = Map.of();

        @Override
        public String get(URI uri) throws Exception {
            if (failIp) {
                throw new WeatherException(WeatherException.Kind.NETWORK, "offline");
            }
            return "{\"success\":true,\"city\":\"北京\"}";
        }

        @Override
        public String get(URI uri, Map<String, String> headers) {
            this.geoUri = uri;
            this.headers = Map.copyOf(headers);
            return "{\"code\":\"200\",\"location\":[{\"id\":\"101010100\","
                    + "\"name\":\"北京\",\"lon\":\"116.41\",\"lat\":\"39.92\","
                    + "\"tz\":\"Asia/Shanghai\"}]}";
        }
    }
}
