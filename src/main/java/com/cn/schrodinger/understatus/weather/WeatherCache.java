package com.cn.schrodinger.understatus.weather;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class WeatherCache {

    private final Clock clock;
    private final Map<WeatherCacheKey, Entry> entries = new ConcurrentHashMap<>();

    public WeatherCache() { this(Clock.systemUTC()); }

    WeatherCache(Clock clock) { this.clock = clock; }

    public <T> void put(WeatherCacheKey key, T value, Duration ttl) {
        entries.put(key, new Entry(value, clock.instant().plus(ttl)));
    }

    public <T> Optional<CachedValue<T>> get(WeatherCacheKey key, Class<T> type) {
        Entry entry = entries.get(key);
        if (entry == null || !type.isInstance(entry.value())) {
            return Optional.empty();
        }
        return Optional.of(new CachedValue<>(type.cast(entry.value()),
                !clock.instant().isBefore(entry.expiresAt())));
    }

    public void remove(WeatherCacheKey key) { entries.remove(key); }

    public void clearLocation(String location) {
        entries.keySet().removeIf(key -> key.location().equals(location));
    }

    public void clear() { entries.clear(); }

    public record CachedValue<T>(T value, boolean stale) {}

    private record Entry(Object value, Instant expiresAt) {}
}
