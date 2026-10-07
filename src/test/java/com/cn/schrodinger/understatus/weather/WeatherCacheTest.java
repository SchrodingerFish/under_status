package com.cn.schrodinger.understatus.weather;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class WeatherCacheTest {

    @Test
    void evictsLeastRecentlyUsedEntryAndExpiresStaleRetention() {
        MutableClock clock = new MutableClock();
        WeatherCache cache = new WeatherCache(clock, 2, Duration.ofHours(1));
        WeatherCacheKey first = key("first");
        WeatherCacheKey second = key("second");
        WeatherCacheKey third = key("third");
        cache.put(first, "one", Duration.ofMinutes(5));
        cache.put(second, "two", Duration.ofMinutes(5));
        cache.get(first, String.class);
        cache.put(third, "three", Duration.ofMinutes(5));
        assertTrue(cache.get(second, String.class).isEmpty());
        assertTrue(cache.get(first, String.class).isPresent());
        clock.instant = clock.instant.plus(Duration.ofMinutes(6));
        assertTrue(cache.get(first, String.class).orElseThrow().stale());
        clock.instant = clock.instant.plus(Duration.ofHours(1));
        assertTrue(cache.get(first, String.class).isEmpty());
        assertTrue(cache.get(third, String.class).isEmpty());
    }

    private static WeatherCacheKey key(String location) {
        return new WeatherCacheKey(location, "now", "", "zh", "m", null);
    }

    @Test
    void marksExpiredValuesStaleAndClearsByLocation() {
        MutableClock clock = new MutableClock();
        WeatherCache cache = new WeatherCache(clock);
        WeatherCacheKey key = new WeatherCacheKey(
                "101010100", "minutely", "", "zh", "m", null);
        cache.put(key, "rain", Duration.ofMinutes(5));

        assertFalse(cache.get(key, String.class).orElseThrow().stale());
        clock.instant = clock.instant.plus(Duration.ofMinutes(6));
        assertTrue(cache.get(key, String.class).orElseThrow().stale());
        cache.clearLocation("101010100");
        assertTrue(cache.get(key, String.class).isEmpty());
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-07-17T00:00:00Z");
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
