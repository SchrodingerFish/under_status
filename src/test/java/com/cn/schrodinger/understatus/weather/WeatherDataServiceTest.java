package com.cn.schrodinger.understatus.weather;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class WeatherDataServiceTest {

    @Test
    void cacheIsScopedToHostAndCredential() throws Exception {
        FailingTransport transport = new FailingTransport();
        WeatherDataService service = service(transport);
        service.now(config(), location(), false);
        service.now(new QWeatherConfig("other.qweatherapi.com", "secret", "zh", "m"), location(), false);
        service.now(new QWeatherConfig("abc.def.qweatherapi.com", "new-secret", "zh", "m"), location(), false);
        assertEquals(3, transport.calls);
    }

    @Test
    void failedForcedRefreshRetainsPreviousValue() throws Exception {
        FailingTransport transport = new FailingTransport();
        WeatherDataService service = service(transport);
        WeatherNow original = service.now(config(), location(), false).value();
        transport.fail = true;
        transport.failureKind = WeatherException.Kind.AUTHENTICATION;
        WeatherDataService.Result<WeatherNow> refreshed = service.now(config(), location(), true);
        assertTrue(refreshed.stale());
        assertEquals(original, refreshed.value());
        assertTrue(service.now(config(), location(), false).stale());
    }

    @Test
    void automaticIpLocationRefreshesAfterThirtyMinutes() throws Exception {
        java.util.concurrent.atomic.AtomicInteger ipCalls = new java.util.concurrent.atomic.AtomicInteger();
        HttpTransport transport = uri -> {
            if (uri.getHost().equals("ipwho.is")) {
                ipCalls.incrementAndGet();
                return "{\"success\":true,\"city\":\"北京\"}";
            }
            return "{\"code\":\"200\",\"location\":[{\"id\":\"101010100\",\"name\":\"北京\","
                    + "\"lon\":\"116.41\",\"lat\":\"39.92\",\"tz\":\"Asia/Shanghai\"}]}";
        };
        MutableClock clock = new MutableClock();
        WeatherDataService service = new WeatherDataService(new QWeatherClient(transport),
                new LocationResolver(transport), new WeatherCache(clock));
        service.resolve(config(), "北京", true, false);
        service.resolve(config(), "北京", true, false);
        assertEquals(1, ipCalls.get());
        clock.instant = clock.instant.plus(Duration.ofMinutes(31));
        service.resolve(config(), "北京", true, false);
        assertEquals(2, ipCalls.get());
    }

    @Test
    void preInterruptedRequestDoesNotReturnCacheOrRetry() throws Exception {
        FailingTransport transport = new FailingTransport();
        WeatherDataService service = service(transport);
        service.now(config(), location(), false);
        Thread.currentThread().interrupt();
        try {
            assertThrows(WeatherException.class, () -> service.now(config(), location(), false));
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(1, transport.calls);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void cachesCurrentWeatherAndFallsBackToStaleValue() throws Exception {
        FailingTransport transport = new FailingTransport();
        MutableClock clock = new MutableClock();
        WeatherDataService service = new WeatherDataService(new QWeatherClient(transport),
                new LocationResolver(transport), new WeatherCache(clock));
        QWeatherConfig config = new QWeatherConfig(
                "abc.def.qweatherapi.com", "secret", "zh", "m");
        LocationContext location = new LocationContext("101010100", "北京", 116.41,
                39.92, java.time.ZoneId.of("Asia/Shanghai"));

        assertFalse(service.now(config, location, false).stale());
        int calls = transport.calls;
        assertFalse(service.now(config, location, false).stale());
        assertTrue(transport.calls == calls);

        clock.instant = clock.instant.plus(Duration.ofMinutes(16));
        transport.fail = true;
        assertTrue(service.now(config, location, false).stale());
    }

    @Test
    void retriesTemporaryFailuresButNotAuthentication() throws Exception {
        FailingTransport transport = new FailingTransport();
        transport.failuresRemaining = 2;
        WeatherDataService service = service(transport);
        service.now(config(), location(), false);
        assertTrue(transport.calls == 3);

        FailingTransport authentication = new FailingTransport();
        authentication.failuresRemaining = 3;
        authentication.failureKind = WeatherException.Kind.AUTHENTICATION;
        org.junit.jupiter.api.Assertions.assertThrows(WeatherException.class,
                () -> service(authentication).now(config(), location(), false));
        assertTrue(authentication.calls == 1);
    }

    private static WeatherDataService service(FailingTransport transport) {
        return new WeatherDataService(new QWeatherClient(transport),
                new LocationResolver(transport), new WeatherCache());
    }

    @Test void changedCredentialsDoNotReuseCachedSuccessAndAuthenticationDoesNotUseFallback() throws Exception {
        FailingTransport transport = new FailingTransport();
        WeatherDataService service = service(transport);
        service.now(config(), location(), false);
        transport.fail = true;
        transport.failureKind = WeatherException.Kind.AUTHENTICATION;
        QWeatherConfig changed = new QWeatherConfig("abc.def.qweatherapi.com", "new-secret", "zh", "m");
        org.junit.jupiter.api.Assertions.assertThrows(WeatherException.class, () -> service.now(changed, location(), false));
        org.junit.jupiter.api.Assertions.assertThrows(WeatherException.class, () -> service.now(config(), location(), true));
        assertFalse(changed.toString().contains("new-secret"));
        service.clearLocation(null);
    }

    private static QWeatherConfig config() {
        return new QWeatherConfig("abc.def.qweatherapi.com", "secret", "zh", "m");
    }

    private static LocationContext location() {
        return new LocationContext("101010100", "北京", 116.41, 39.92,
                java.time.ZoneId.of("Asia/Shanghai"));
    }

    private static final class FailingTransport implements HttpTransport {
        int calls;
        boolean fail;
        int failuresRemaining;
        WeatherException.Kind failureKind = WeatherException.Kind.NETWORK;
        @Override public String get(java.net.URI uri) { return "{}"; }
        @Override public String get(java.net.URI uri, java.util.Map<String, String> headers)
                throws WeatherException {
            calls++;
            if (fail || failuresRemaining-- > 0) {
                throw new WeatherException(failureKind, "offline");
            }
            return "{\"code\":\"200\",\"now\":{\"temp\":\"31\","
                    + "\"feelsLike\":\"32\",\"icon\":\"100\",\"text\":\"晴\","
                    + "\"wind360\":\"180\",\"windDir\":\"南风\",\"windScale\":\"2\","
                    + "\"windSpeed\":\"8\",\"humidity\":\"48\",\"precip\":\"0.0\","
                    + "\"pressure\":\"1001\",\"vis\":\"18\"}}";
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-07-17T00:00:00Z");
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
