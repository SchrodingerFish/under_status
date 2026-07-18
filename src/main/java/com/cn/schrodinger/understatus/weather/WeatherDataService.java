package com.cn.schrodinger.understatus.weather;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

public final class WeatherDataService {

    private static final Duration CURRENT_TTL = Duration.ofMinutes(15);
    private static final Duration HOURLY_TTL = Duration.ofMinutes(30);
    private static final Duration DAILY_TTL = Duration.ofHours(1);
    private static final Duration AIR_TTL = Duration.ofMinutes(30);
    private static final Duration INDEX_TTL = Duration.ofHours(6);
    private static final Duration MINUTELY_TTL = Duration.ofMinutes(5);
    private static final Duration SESSION_TTL = Duration.ofDays(3650);

    private final QWeatherClient client;
    private final LocationResolver resolver;
    private final WeatherCache cache;

    public WeatherDataService(QWeatherClient client, LocationResolver resolver,
            WeatherCache cache) {
        this.client = client;
        this.resolver = resolver;
        this.cache = cache;
    }

    public LocationContext resolve(QWeatherConfig config, String city, boolean autoIp,
            boolean force) throws WeatherException {
        String identity = (autoIp ? "ip:" : "city:") + (city == null ? "" : city.trim());
        WeatherCacheKey key = key(identity, "location", "", config, null);
        return load(key, LocationContext.class, SESSION_TTL, force,
                () -> resolver.resolve(config, city, autoIp)).value();
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
                SESSION_TTL, force, () -> client.fetchHistorical(c, l, date));
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
        cache.clearLocation(location.locationId());
        cache.clearLocation(location.coordinate());
    }

    public void clearAll() { cache.clear(); }

    private <T> Result<T> load(WeatherCacheKey key, Class<T> type, Duration ttl,
            boolean force, Loader<T> loader) throws WeatherException {
        WeatherCache.CachedValue<T> existing = cache.get(key, type).orElse(null);
        if (!force && existing != null && !existing.stale()) {
            return new Result<>(existing.value(), false);
        }
        try {
            T value = executeWithRetry(loader);
            cache.put(key, value, ttl);
            return new Result<>(value, false);
        } catch (WeatherException ex) {
            if (existing != null) {
                return new Result<>(existing.value(), true);
            }
            throw ex;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> Result<List<T>> loadList(WeatherCacheKey key, Duration ttl,
            boolean force, Loader<List<T>> loader) throws WeatherException {
        WeatherCache.CachedValue<List> existing = cache.get(key, List.class).orElse(null);
        if (!force && existing != null && !existing.stale()) {
            return new Result<>((List<T>) existing.value(), false);
        }
        try {
            List<T> value = List.copyOf(executeWithRetry(loader));
            cache.put(key, value, ttl);
            return new Result<>(value, false);
        } catch (WeatherException ex) {
            if (existing != null) {
                return new Result<>((List<T>) existing.value(), true);
            }
            throw ex;
        }
    }

    private static <T> T executeWithRetry(Loader<T> loader) throws WeatherException {
        WeatherException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return loader.load();
            } catch (WeatherException ex) {
                last = ex;
                if (!retryable(ex) || attempt == 2) throw ex;
                try {
                    Thread.sleep(150L << attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new WeatherException(WeatherException.Kind.NETWORK,
                            "天气请求重试已中断", interrupted);
                }
            }
        }
        throw last;
    }

    private static boolean retryable(WeatherException error) {
        return error.kind() == WeatherException.Kind.TIMEOUT
                || error.kind() == WeatherException.Kind.NETWORK
                || error.kind() == WeatherException.Kind.UNAVAILABLE;
    }

    private static WeatherCacheKey key(String location, String endpoint, String range,
            QWeatherConfig config, LocalDate date) {
        return new WeatherCacheKey(location, endpoint, range, config.language(),
                config.unit(), date);
    }

    @FunctionalInterface
    private interface Loader<T> { T load() throws WeatherException; }

    public record Result<T>(T value, boolean stale) {}
}
