package com.cn.schrodinger.understatus.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

class JdkHttpTransportTest {

    @Test
    void decodesGzipAndPlainUtf8Bodies() throws Exception {
        byte[] plain = "{\"text\":\"晴\"}".getBytes(StandardCharsets.UTF_8);

        assertEquals("{\"text\":\"晴\"}", JdkHttpTransport.decodeBody(plain, ""));
        assertEquals("{\"text\":\"晴\"}",
                JdkHttpTransport.decodeBody(gzip(plain), "gzip"));
    }

    @Test
    void buildsHeaderAuthenticatedRequestWithoutPuttingKeyInUri() throws Exception {
        URI uri = URI.create("https://abc.def.qweatherapi.com/v7/weather/now?location=101010100");

        HttpRequest request = JdkHttpTransport.buildRequest(uri,
                Map.of("X-QW-Api-Key", "top-secret"));

        assertEquals("top-secret", request.headers().firstValue("X-QW-Api-Key").orElseThrow());
        assertEquals("gzip", request.headers().firstValue("Accept-Encoding").orElseThrow());
        assertFalse(request.uri().toString().contains("top-secret"));
    }

    @Test
    void rejectsNonHttpsUri() {
        WeatherException error = assertThrows(WeatherException.class,
                () -> JdkHttpTransport.buildRequest(URI.create("http://example.com"), Map.of()));
        assertEquals(WeatherException.Kind.CONFIGURATION, error.kind());
    }

    @Test
    void mapsHttpStatusesToStableKinds() {
        assertStatus(400, WeatherException.Kind.INVALID_REQUEST);
        assertStatus(401, WeatherException.Kind.AUTHENTICATION);
        assertStatus(403, WeatherException.Kind.FORBIDDEN);
        assertStatus(404, WeatherException.Kind.NOT_FOUND);
        assertStatus(429, WeatherException.Kind.RATE_LIMIT);
        assertStatus(500, WeatherException.Kind.UNAVAILABLE);
        assertStatus(204, WeatherException.Kind.UNSUPPORTED);
        WeatherException unavailable = assertThrows(WeatherException.class,
                () -> JdkHttpTransport.requireSuccess(400,
                        "{\"error\":{\"type\":\"#data-not-available\"}}"));
        assertEquals(WeatherException.Kind.UNSUPPORTED, unavailable.kind());
    }

    private static void assertStatus(int status, WeatherException.Kind expected) {
        WeatherException error = assertThrows(WeatherException.class,
                () -> JdkHttpTransport.requireSuccess(status));
        assertEquals(expected, error.kind());
        assertEquals(status, error.httpStatus());
    }

    private static byte[] gzip(byte[] value) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(value);
        }
        byte[] compressed = output.toByteArray();
        assertFalse(compressed.length == 0);
        return compressed;
    }
}
