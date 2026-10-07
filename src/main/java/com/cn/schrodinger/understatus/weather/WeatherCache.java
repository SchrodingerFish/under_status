package com.cn.schrodinger.understatus.weather;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.LinkedHashMap;

public final class WeatherCache {

    private final Clock clock;
    private final int capacity;
    private final Duration staleRetention;
    private final Map<WeatherCacheKey, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);

    public WeatherCache() { this(Clock.systemUTC()); }

    WeatherCache(Clock clock) { this(clock, 256, Duration.ofHours(24)); }

    WeatherCache(Clock clock, int capacity, Duration staleRetention) {
        if (capacity < 1 || staleRetention.isNegative()) throw new IllegalArgumentException();
        this.clock = clock;
        this.capacity = capacity;
        this.staleRetention = staleRetention;
    }

    public synchronized <T> void put(WeatherCacheKey key, T value, Duration ttl) {
        Instant now = clock.instant();
        entries.values().removeIf(entry -> !now.isBefore(entry.retainUntil()));
        Instant expires = now.plus(ttl);
        entries.put(key, new Entry(value, expires, expires.plus(staleRetention)));
        while (entries.size() > capacity) entries.remove(entries.keySet().iterator().next());
    }

    public synchronized <T> Optional<CachedValue<T>> get(WeatherCacheKey key, Class<T> type) {
        Entry entry = entries.get(key);
        if (entry != null && !clock.instant().isBefore(entry.retainUntil())) {
            entries.remove(key);
            return Optional.empty();
        }
        if (entry == null || !type.isInstance(entry.value())) {
            return Optional.empty();
        }
        return Optional.of(new CachedValue<>(type.cast(entry.value()),
                !clock.instant().isBefore(entry.expiresAt())));
    }

    public synchronized void remove(WeatherCacheKey key) { entries.remove(key); }

    public synchronized void markStale(WeatherCacheKey key) {
        Entry entry = entries.get(key);
        if (entry != null) {
            Instant now = clock.instant();
            entries.put(key, new Entry(entry.value(),
                    entry.expiresAt().isBefore(now) ? entry.expiresAt() : now, entry.retainUntil()));
        }
    }

    public synchronized void clearLocation(String location) {
        entries.keySet().removeIf(key -> key.location().equals(location));
    }

    public synchronized void clear() { entries.clear(); }

    public record CachedValue<T>(T value, boolean stale) {}

    private record Entry(Object value, Instant expiresAt, Instant retainUntil) {}
}
