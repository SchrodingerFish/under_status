package com.cn.schrodinger.understatus.weather;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.LinkedHashMap;

public final class WeatherCache {

    private final Clock clock;
    private final Map<WeatherCacheKey, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private long generation;
    private static final Duration STALE_RETENTION = Duration.ofHours(6);

    public WeatherCache() { this(Clock.systemUTC()); }

    WeatherCache(Clock clock) { this.clock = clock; }

    public synchronized <T> void put(WeatherCacheKey key, T value, Duration ttl) {
        entries.entrySet().removeIf(entry -> expired(entry.getValue()));
        entries.put(key, new Entry(value, clock.instant().plus(ttl)));
        while (entries.size() > 128) entries.remove(entries.keySet().iterator().next());
    }

    public synchronized long generation() { return generation; }
    public synchronized <T> void putIfCurrent(long expected, WeatherCacheKey key, T value, Duration ttl) {
        if (expected == generation) put(key, value, ttl);
    }
    private boolean expired(Entry entry) { return !clock.instant().isBefore(entry.expiresAt().plus(STALE_RETENTION)); }

    public synchronized <T> Optional<CachedValue<T>> get(WeatherCacheKey key, Class<T> type) {
        Entry entry = entries.get(key);
        if (entry != null && expired(entry)) { entries.remove(key); return Optional.empty(); }
        if (entry == null || !type.isInstance(entry.value())) {
            return Optional.empty();
        }
        return Optional.of(new CachedValue<>(type.cast(entry.value()),
                !clock.instant().isBefore(entry.expiresAt())));
    }

    public synchronized void remove(WeatherCacheKey key) { generation++; entries.remove(key); }

    public synchronized void clearLocation(String location) {
        generation++;
        entries.keySet().removeIf(key -> key.location().equals(location));
    }

    public synchronized void clear() { generation++; entries.clear(); }

    public record CachedValue<T>(T value, boolean stale) {}

    private record Entry(Object value, Instant expiresAt) {}
}
