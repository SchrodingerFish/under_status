package com.cn.schrodinger.understatus.weather;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public final class WeatherDataService {

    private static final Duration CURRENT_TTL = Duration.ofMinutes(15);
    private static final Duration HOURLY_TTL = Duration.ofMinutes(30);
    private static final Duration DAILY_TTL = Duration.ofHours(1);
    private static final Duration AIR_TTL = Duration.ofMinutes(30);
    private static final Duration INDEX_TTL = Duration.ofHours(6);
    private static final Duration MINUTELY_TTL = Duration.ofMinutes(5);
    private static final Duration LOCATION_TTL = Duration.ofHours(24);
    private static final Duration IP_LOCATION_TTL = Duration.ofMinutes(30);
    private static final Duration HISTORY_TTL = Duration.ofDays(1);

    private final QWeatherClient client;
    private final LocationResolver resolver;
    private final WeatherCache cache;
    private final Object lock = new Object();
    private final Map<WeatherCacheKey, Flight> inFlight = new HashMap<>();
    private long generation;

    public WeatherDataService(QWeatherClient client, LocationResolver resolver,
            WeatherCache cache) {
        this.client = client;
        this.resolver = resolver;
        this.cache = cache;
    }

    public LocationContext resolve(QWeatherConfig config, String city, boolean autoIp,
            boolean force) throws WeatherException {
        return resolveResult(config, city, autoIp, force).value();
    }

    public Result<LocationContext> resolveResult(QWeatherConfig config, String city, boolean autoIp,
            boolean force) throws WeatherException {
        String identity = (autoIp ? "ip:" : "city:") + (city == null ? "" : city.trim());
        WeatherCacheKey key = key(identity, "location", "", config, null);
        return load(key, LocationContext.class, autoIp ? IP_LOCATION_TTL : LOCATION_TTL, force,
                () -> resolver.resolve(config, city, autoIp));
    }

    public Result<WeatherNow> now(QWeatherConfig c, LocationContext l, boolean force)
            throws WeatherException {
        return load(key(l.locationId(), "now", "", c, null), WeatherNow.class,
                CURRENT_TTL, force, () -> client.fetchNow(c, l));
    }

    public Result<List<DailyForecast>> daily(QWeatherConfig c, LocationContext l, ForecastRange range,
            boolean force) throws WeatherException {
        return loadList(key(l.locationId(), "daily", range.path(), c,
                        LocalDate.now(l.zoneId())),
                DAILY_TTL, force, () -> client.fetchDaily(c, l, range));
    }

    public Result<List<HourlyForecast>> hourly(QWeatherConfig c, LocationContext l, int hours,
            boolean force) throws WeatherException {
        return loadList(key(l.locationId(), "hourly", hours + "h", c, null),
                HOURLY_TTL, force, () -> client.fetchHourly(c, l, hours));
    }

    public Result<GridWeatherNow> gridNow(QWeatherConfig c, LocationContext l,
            boolean force) throws WeatherException {
        return load(key(l.coordinate(), "grid-now", "", c, null), GridWeatherNow.class,
                CURRENT_TTL, force, () -> client.fetchGridNow(c, l));
    }

    public Result<List<GridDailyForecast>> gridDaily(QWeatherConfig c, LocationContext l, ForecastRange range,
            boolean force) throws WeatherException {
        return loadList(key(l.coordinate(), "grid-daily", range.path(), c,
                        LocalDate.now(l.zoneId())),
                DAILY_TTL, force, () -> client.fetchGridDaily(c, l, range));
    }

    public Result<List<GridHourlyForecast>> gridHourly(QWeatherConfig c, LocationContext l, int hours,
            boolean force) throws WeatherException {
        return loadList(key(l.coordinate(), "grid-hourly", hours + "h", c, null),
                HOURLY_TTL, force, () -> client.fetchGridHourly(c, l, hours));
    }

    public Result<AirQualitySnapshot> air(QWeatherConfig c, LocationContext l,
            boolean force) throws WeatherException {
        return load(key(l.coordinate(), "air", "", c, null), AirQualitySnapshot.class,
                AIR_TTL, force, () -> client.fetchAirQuality(c, l));
    }

    public Result<HistoricalWeather> historical(QWeatherConfig c, LocationContext l,
            LocalDate date, boolean force) throws WeatherException {
        return load(key(l.locationId(), "historical", "", c, date), HistoricalWeather.class,
                HISTORY_TTL, force, () -> client.fetchHistorical(c, l, date));
    }

    public Result<List<WeatherIndex>> indices(QWeatherConfig c, LocationContext l,
            WeatherIndexRange range, boolean force) throws WeatherException {
        return loadList(key(l.locationId(), "indices", range.path(), c, null),
                INDEX_TTL, force, () -> client.fetchIndices(c, l, range));
    }

    public Result<MinutelyPrecipitation> minutely(QWeatherConfig c, LocationContext l,
            boolean force) throws WeatherException {
        return load(key(l.coordinate(), "minutely", "", c, null),
                MinutelyPrecipitation.class, MINUTELY_TTL, force,
                () -> client.fetchMinutely(c, l));
    }

    public void clearLocation(LocationContext location) {
        if (location == null) return;
        synchronized (lock) {
            invalidateInFlight();
            cache.clearLocation(location.locationId());
            cache.clearLocation(location.coordinate());
        }
    }

    public void clearAll() {
        synchronized (lock) {
            invalidateInFlight();
            cache.clear();
        }
    }

    private void invalidateInFlight() {
        generation++;
        inFlight.values().forEach(flight -> flight.result.completeExceptionally(cancelled()));
        inFlight.clear();
    }

    private <T> Result<T> load(WeatherCacheKey key, Class<T> type, Duration ttl,
            boolean force, Loader<T> loader) throws WeatherException {
        checkInterrupted();
        Flight flight;
        long requestGeneration;
        boolean owner;
        synchronized (lock) {
            WeatherCache.CachedValue<T> existing = cache.get(key, type).orElse(null);
            flight = inFlight.get(key);
            if (flight == null && !force && existing != null && !existing.stale()) {
                return new Result<>(existing.value(), false);
            }
            owner = flight == null;
            if (owner) {
                flight = new Flight();
                inFlight.put(key, flight);
            }
            requestGeneration = generation;
        }
        return finishLoad(key, type, ttl, loader, flight, requestGeneration, owner, 1);
    }

    private <T> Result<T> finishLoad(WeatherCacheKey key, Class<T> type, Duration ttl,
            Loader<T> loader, Flight flight, long requestGeneration, boolean owner,
            int takeoversRemaining) throws WeatherException {
        if (!owner) {
            try {
                return await(flight.result);
            } catch (WeatherException ex) {
                if (ex.kind() != WeatherException.Kind.CANCELLED || takeoversRemaining == 0
                        || Thread.currentThread().isInterrupted()) throw ex;
                Flight replacement;
                boolean replacementOwner = false;
                synchronized (lock) {
                    if (requestGeneration != generation) throw cancelled();
                    replacement = flight.replacement;
                    if (replacement == null) {
                        replacement = inFlight.get(key);
                        if (replacement == null || replacement == flight) {
                            replacement = new Flight();
                            inFlight.put(key, replacement);
                            replacementOwner = true;
                        }
                        // Late followers reuse this exact result, including a fast forced refresh
                        // that has already finished and been removed from the in-flight map.
                        flight.replacement = replacement;
                    }
                }
                return finishLoad(key, type, ttl, loader, replacement, requestGeneration,
                        replacementOwner, takeoversRemaining - 1);
            }
        }
        try {
            T value = executeWithRetry(loader);
            checkInterrupted();
            synchronized (lock) {
                if (requestGeneration != generation) throw cancelled();
                cache.put(key, value, ttl);
                Result<T> result = new Result<>(value, false);
                flight.result.complete(result);
                return result;
            }
        } catch (WeatherException ex) {
            synchronized (lock) {
                WeatherException failure = requestGeneration != generation ? cancelled() : ex;
                // Re-read to enforce the retention deadline even if a request was slow.
                WeatherCache.CachedValue<T> existing = cache.get(key, type).orElse(null);
                if (failure.kind() != WeatherException.Kind.CANCELLED
                        && !Thread.currentThread().isInterrupted() && existing != null) {
                    cache.markStale(key);
                    Result<T> result = new Result<>(existing.value(), true);
                    flight.result.complete(result);
                    return result;
                }
                flight.result.completeExceptionally(failure);
                throw failure;
            }
        } catch (RuntimeException | Error ex) {
            flight.result.completeExceptionally(ex);
            throw ex;
        } finally {
            synchronized (lock) { inFlight.remove(key, flight); }
        }
    }

    private static final class Flight {
        private final CompletableFuture<Result<?>> result = new CompletableFuture<>();
        // Accessed under lock; retained only by callers still consuming the abandoned flight.
        private Flight replacement;
    }

    @SuppressWarnings("unchecked")
    private static <T> Result<T> await(CompletableFuture<Result<?>> flight) throws WeatherException {
        try {
            return (Result<T>) flight.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw cancelled();
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof WeatherException weather) throw weather;
            if (ex.getCause() instanceof RuntimeException runtime) throw runtime;
            if (ex.getCause() instanceof Error error) throw error;
            throw new WeatherException(WeatherException.Kind.RESPONSE, "天气数据加载失败", ex.getCause());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> Result<List<T>> loadList(WeatherCacheKey key, Duration ttl,
            boolean force, Loader<List<T>> loader) throws WeatherException {
        return (Result) load(key, List.class, ttl, force, () -> List.copyOf(loader.load()));
    }

    private static <T> T executeWithRetry(Loader<T> loader) throws WeatherException {
        WeatherException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            checkInterrupted();
            try {
                return loader.load();
            } catch (WeatherException ex) {
                last = ex;
                checkInterrupted();
                if (!retryable(ex) || attempt == 2) throw ex;
                try {
                    Thread.sleep(150L << attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw cancelled();
                }
            }
        }
        throw last;
    }

    private static void checkInterrupted() throws WeatherException {
        if (Thread.currentThread().isInterrupted()) throw cancelled();
    }

    private static WeatherException cancelled() {
        return new WeatherException(WeatherException.Kind.CANCELLED, "天气请求已取消");
    }

    private static boolean retryable(WeatherException error) {
        return error.kind() == WeatherException.Kind.TIMEOUT
                || error.kind() == WeatherException.Kind.NETWORK
                || error.kind() == WeatherException.Kind.UNAVAILABLE;
    }

    private static WeatherCacheKey key(String location, String endpoint, String range,
            QWeatherConfig config, LocalDate date) {
        return new WeatherCacheKey(location, endpoint, range, config.language(),
                config.unit(), date, config.cacheIdentity());
    }

    @FunctionalInterface
    private interface Loader<T> { T load() throws WeatherException; }

    public record Result<T>(T value, boolean stale) {}
}
