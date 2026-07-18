package com.cn.schrodinger.understatus.weather;

import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class QWeatherClient {

    private final HttpTransport transport;

    public QWeatherClient(HttpTransport transport) {
        this.transport = transport;
    }

    public WeatherNow fetchNow(QWeatherConfig config, LocationContext location)
            throws WeatherException {
        JsonValue.ObjectValue root = request(config, "/v7/weather/now",
                Map.of("location", location.locationId(), "lang", config.language(),
                        "unit", config.unit()));
        JsonValue.ObjectValue now = requiredObject(root, "now");
        try {
            return new WeatherNow(offset(root, "updateTime"), integer(now, "temp"),
                    integer(now, "feelsLike"), text(now, "icon"), text(now, "text"),
                    integer(now, "wind360"), text(now, "windDir"),
                    text(now, "windScale"), integer(now, "windSpeed"),
                    integer(now, "humidity"), decimal(now, "precip"),
                    integer(now, "pressure"), decimal(now, "vis"),
                    optionalInteger(now, "cloud"), optionalInteger(now, "dew"),
                    reference(root));
        } catch (NumberFormatException ex) {
            throw responseError(ex);
        }
    }

    public List<DailyForecast> fetchDaily(QWeatherConfig config, LocationContext location,
            ForecastRange range) throws WeatherException {
        if (range == null || range.type() != ForecastRange.Type.DAILY) {
            throw new IllegalArgumentException("每日天气预报需要天数范围");
        }
        JsonValue.ObjectValue root = request(config, "/v7/weather/" + range.path(),
                Map.of("location", location.locationId(), "lang", config.language(),
                        "unit", config.unit()));
        List<DailyForecast> result = new ArrayList<>();
        try {
            for (JsonValue value : root.required("daily").asArray()) {
                JsonValue.ObjectValue day = value.asObject();
                result.add(new DailyForecast(LocalDate.parse(text(day, "fxDate")),
                        optionalText(day, "sunrise"), optionalText(day, "sunset"),
                        optionalText(day, "moonrise"), optionalText(day, "moonset"),
                        optionalText(day, "moonPhase"), integer(day, "tempMax"),
                        integer(day, "tempMin"), text(day, "iconDay"),
                        text(day, "textDay"), text(day, "iconNight"),
                        text(day, "textNight"), optionalText(day, "windDirDay"),
                        optionalText(day, "windScaleDay"), integer(day, "humidity"),
                        decimal(day, "precip"), integer(day, "pressure"),
                        decimal(day, "vis"), optionalInteger(day, "cloud"),
                        optionalInteger(day, "uvIndex"), reference(root)));
            }
            return List.copyOf(result);
        } catch (NumberFormatException | java.time.DateTimeException ex) {
            throw responseError(ex);
        }
    }

    public List<HourlyForecast> fetchHourly(QWeatherConfig config,
            LocationContext location, int hours) throws WeatherException {
        ForecastRange range = ForecastRange.hourly(hours);
        JsonValue.ObjectValue root = request(config, "/v7/weather/" + range.path(),
                Map.of("location", location.locationId(), "lang", config.language(),
                        "unit", config.unit()));
        List<HourlyForecast> result = new ArrayList<>();
        try {
            for (JsonValue value : root.required("hourly").asArray()) {
                JsonValue.ObjectValue hour = value.asObject();
                result.add(new HourlyForecast(OffsetDateTime.parse(text(hour, "fxTime")),
                        integer(hour, "temp"), text(hour, "text"), text(hour, "icon"),
                        optionalInteger(hour, "pop"), decimal(hour, "precip"),
                        integer(hour, "wind360"), text(hour, "windDir"),
                        text(hour, "windScale"), integer(hour, "windSpeed"),
                        integer(hour, "humidity"), integer(hour, "pressure"),
                        optionalInteger(hour, "cloud"), optionalInteger(hour, "dew"),
                        reference(root)));
            }
            return List.copyOf(result);
        } catch (NumberFormatException | java.time.DateTimeException ex) {
            throw responseError(ex);
        }
    }

    public GridWeatherNow fetchGridNow(QWeatherConfig config, LocationContext location)
            throws WeatherException {
        JsonValue.ObjectValue root = request(config, "/v7/grid-weather/now",
                query(config, location.coordinate()));
        JsonValue.ObjectValue now = requiredObject(root, "now");
        try {
            return new GridWeatherNow(offset(root, "updateTime"), integer(now, "temp"),
                    text(now, "icon"), text(now, "text"), integer(now, "wind360"),
                    text(now, "windDir"), text(now, "windScale"),
                    integer(now, "windSpeed"), integer(now, "humidity"),
                    decimal(now, "precip"), integer(now, "pressure"),
                    optionalInteger(now, "cloud"), optionalInteger(now, "dew"),
                    reference(root));
        } catch (NumberFormatException ex) {
            throw responseError(ex);
        }
    }

    public List<GridDailyForecast> fetchGridDaily(QWeatherConfig config,
            LocationContext location, ForecastRange range) throws WeatherException {
        if (range != ForecastRange.DAYS_3 && range != ForecastRange.DAYS_7) {
            throw new IllegalArgumentException("格点每日预报仅支持 3 天或 7 天");
        }
        JsonValue.ObjectValue root = request(config, "/v7/grid-weather/" + range.path(),
                query(config, location.coordinate()));
        List<GridDailyForecast> result = new ArrayList<>();
        try {
            for (JsonValue value : root.required("daily").asArray()) {
                JsonValue.ObjectValue day = value.asObject();
                result.add(new GridDailyForecast(LocalDate.parse(text(day, "fxDate")),
                        integer(day, "tempMax"), integer(day, "tempMin"),
                        text(day, "iconDay"), text(day, "textDay"),
                        text(day, "iconNight"), text(day, "textNight"),
                        optionalText(day, "windDirDay"), optionalText(day, "windScaleDay"),
                        integer(day, "humidity"), decimal(day, "precip"),
                        integer(day, "pressure"), reference(root)));
            }
            return List.copyOf(result);
        } catch (NumberFormatException | java.time.DateTimeException ex) {
            throw responseError(ex);
        }
    }

    public List<GridHourlyForecast> fetchGridHourly(QWeatherConfig config,
            LocationContext location, int hours) throws WeatherException {
        if (hours != 24 && hours != 72) {
            throw new IllegalArgumentException("格点逐小时预报仅支持 24 或 72 小时");
        }
        JsonValue.ObjectValue root = request(config, "/v7/grid-weather/" + hours + "h",
                query(config, location.coordinate()));
        List<GridHourlyForecast> result = new ArrayList<>();
        try {
            for (JsonValue value : root.required("hourly").asArray()) {
                JsonValue.ObjectValue hour = value.asObject();
                result.add(new GridHourlyForecast(OffsetDateTime.parse(text(hour, "fxTime")),
                        integer(hour, "temp"), text(hour, "icon"), text(hour, "text"),
                        integer(hour, "wind360"), text(hour, "windDir"),
                        text(hour, "windScale"), integer(hour, "windSpeed"),
                        integer(hour, "humidity"), decimal(hour, "precip"),
                        integer(hour, "pressure"), optionalInteger(hour, "cloud"),
                        optionalInteger(hour, "dew"), reference(root)));
            }
            return List.copyOf(result);
        } catch (NumberFormatException | java.time.DateTimeException ex) {
            throw responseError(ex);
        }
    }

    public AirQualitySnapshot fetchAirQuality(QWeatherConfig config,
            LocationContext location) throws WeatherException {
        String path = String.format(java.util.Locale.ROOT,
                "/airquality/v1/current/%.2f/%.2f", location.latitude(), location.longitude());
        JsonValue.ObjectValue root = request(config, path,
                Map.of("lang", config.language()));
        try {
            JsonValue.ObjectValue index = root.required("indexes").asArray().get(0).asObject();
            Map<String, Double> pollutants = new LinkedHashMap<>();
            JsonValue pollutantValue = root.optional("pollutants").orElse(null);
            if (pollutantValue != null) {
                for (JsonValue value : pollutantValue.asArray()) {
                    JsonValue.ObjectValue pollutant = value.asObject();
                    JsonValue.ObjectValue concentration = requiredObject(pollutant, "concentration");
                    pollutants.put(text(pollutant, "code"),
                            concentration.required("value").asDouble());
                }
            }
            return new AirQualitySnapshot(offset(root, "updateTime"),
                    flexibleInt(index.required("aqi")), optionalText(index, "category"),
                    optionalText(index, "primaryPollutant"), pollutants,
                    airAttribution(root), reference(root));
        } catch (IndexOutOfBoundsException | NumberFormatException ex) {
            throw responseError(ex);
        }
    }

    public HistoricalWeather fetchHistorical(QWeatherConfig config,
            LocationContext location, LocalDate date) throws WeatherException {
        LocalDate yesterday = LocalDate.now(location.zoneId()).minusDays(1);
        long age = ChronoUnit.DAYS.between(date, yesterday);
        if (age < 0 || age > 9) {
            throw new IllegalArgumentException("天气时光机日期必须是最近 10 天且不包含今天");
        }
        JsonValue.ObjectValue root = request(config, "/v7/historical/weather",
                Map.of("location", location.locationId(),
                        "date", date.format(DateTimeFormatter.BASIC_ISO_DATE),
                        "lang", config.language(), "unit", config.unit()));
        try {
            JsonValue.ObjectValue day = requiredObject(root, "weatherDaily");
            HistoricalWeather.Daily daily = new HistoricalWeather.Daily(
                    LocalDate.parse(text(day, "date")), optionalText(day, "sunrise"),
                    optionalText(day, "sunset"), optionalText(day, "moonrise"),
                    optionalText(day, "moonset"), optionalText(day, "moonPhase"),
                    integer(day, "tempMax"), integer(day, "tempMin"),
                    integer(day, "humidity"), decimal(day, "precip"),
                    integer(day, "pressure"));
            List<HistoricalWeather.Hourly> hourly = new ArrayList<>();
            for (JsonValue value : root.required("weatherHourly").asArray()) {
                JsonValue.ObjectValue hour = value.asObject();
                hourly.add(new HistoricalWeather.Hourly(
                        OffsetDateTime.parse(text(hour, "time")).toLocalDateTime(),
                        integer(hour, "temp"), text(hour, "icon"), text(hour, "text"),
                        decimal(hour, "precip"), integer(hour, "wind360"),
                        text(hour, "windDir"), text(hour, "windScale"),
                        integer(hour, "windSpeed"), integer(hour, "humidity"),
                        integer(hour, "pressure")));
            }
            return new HistoricalWeather(daily, hourly, reference(root));
        } catch (NumberFormatException | java.time.DateTimeException ex) {
            throw responseError(ex);
        }
    }

    public List<WeatherIndex> fetchIndices(QWeatherConfig config,
            LocationContext location, WeatherIndexRange range) throws WeatherException {
        if (range == null) {
            throw new IllegalArgumentException("天气指数范围不能为空");
        }
        JsonValue.ObjectValue root = request(config, "/v7/indices/" + range.path(),
                Map.of("location", location.locationId(), "type", "0",
                        "lang", config.language()));
        List<WeatherIndex> result = new ArrayList<>();
        try {
            for (JsonValue value : root.required("daily").asArray()) {
                JsonValue.ObjectValue item = value.asObject();
                result.add(new WeatherIndex(LocalDate.parse(text(item, "date")),
                        integer(item, "type"), text(item, "name"),
                        optionalText(item, "level"), optionalText(item, "category"),
                        optionalText(item, "text"), reference(root)));
            }
            return List.copyOf(result);
        } catch (NumberFormatException | java.time.DateTimeException ex) {
            throw responseError(ex);
        }
    }

    public MinutelyPrecipitation fetchMinutely(QWeatherConfig config,
            LocationContext location) throws WeatherException {
        JsonValue.ObjectValue root = request(config, "/v7/minutely/5m",
                Map.of("location", location.coordinate(), "lang", config.language()));
        List<MinutelyPrecipitation.Point> points = new ArrayList<>();
        try {
            for (JsonValue value : root.required("minutely").asArray()) {
                JsonValue.ObjectValue item = value.asObject();
                points.add(new MinutelyPrecipitation.Point(
                        OffsetDateTime.parse(text(item, "fxTime")),
                        decimal(item, "precip"), text(item, "type")));
            }
            return new MinutelyPrecipitation(offset(root, "updateTime"),
                    optionalText(root, "summary"), points, reference(root));
        } catch (NumberFormatException | java.time.DateTimeException ex) {
            throw responseError(ex);
        }
    }

    private static Map<String, String> query(QWeatherConfig config, String location) {
        return Map.of("location", location, "lang", config.language(), "unit", config.unit());
    }

    private static int flexibleInt(JsonValue value) throws WeatherException {
        try {
            return value.asInt();
        } catch (WeatherException ex) {
            return Integer.parseInt(value.asString());
        }
    }

    private static String airAttribution(JsonValue.ObjectValue root) throws WeatherException {
        JsonValue metadata = root.optional("metadata").orElse(null);
        return metadata == null || metadata == JsonValue.NullValue.INSTANCE
                ? "" : optionalText(metadata.asObject(), "tag");
    }

    private JsonValue.ObjectValue request(QWeatherConfig config, String path,
            Map<String, String> query) throws WeatherException {
        try {
            String body = transport.get(config.endpoint(path, query),
                    Map.of("X-QW-Api-Key", config.apiKey()));
            JsonValue.ObjectValue root = JsonParser.parse(body).asObject();
            String code = optionalText(root, "code");
            if (!code.isEmpty() && !"200".equals(code)) {
                throw errorForCode(code);
            }
            return root;
        } catch (WeatherException ex) {
            throw ex;
        } catch (java.net.http.HttpTimeoutException ex) {
            throw new WeatherException(WeatherException.Kind.TIMEOUT, "天气请求超时", ex);
        } catch (Exception ex) {
            throw new WeatherException(WeatherException.Kind.NETWORK, "天气服务暂时不可用", ex);
        }
    }

    static WeatherException errorForCode(String code) {
        WeatherException.Kind kind = switch (code == null ? "" : code) {
            case "204" -> WeatherException.Kind.UNSUPPORTED;
            case "400" -> WeatherException.Kind.INVALID_REQUEST;
            case "401" -> WeatherException.Kind.AUTHENTICATION;
            case "402", "403" -> WeatherException.Kind.FORBIDDEN;
            case "404" -> WeatherException.Kind.NOT_FOUND;
            case "429" -> WeatherException.Kind.RATE_LIMIT;
            case "500" -> WeatherException.Kind.UNAVAILABLE;
            default -> WeatherException.Kind.RESPONSE;
        };
        return new WeatherException(kind, switch (kind) {
            case AUTHENTICATION -> "天气服务认证失败，请检查 API Key";
            case FORBIDDEN -> "天气服务拒绝请求，请检查 API Host、权限或额度";
            case RATE_LIMIT -> "天气请求过于频繁，请稍后再试";
            case UNSUPPORTED -> "当前地区暂无该天气数据";
            default -> "天气服务返回错误代码: " + code;
        });
    }

    private static JsonValue.ObjectValue requiredObject(JsonValue.ObjectValue root,
            String key) throws WeatherException {
        return root.required(key).asObject();
    }

    private static String text(JsonValue.ObjectValue object, String key)
            throws WeatherException {
        return object.required(key).asString();
    }

    private static String optionalText(JsonValue.ObjectValue object, String key)
            throws WeatherException {
        JsonValue value = object.optional(key).orElse(null);
        return value == null || value == JsonValue.NullValue.INSTANCE ? "" : value.asString();
    }

    private static int integer(JsonValue.ObjectValue object, String key)
            throws WeatherException {
        return Integer.parseInt(text(object, key));
    }

    private static double decimal(JsonValue.ObjectValue object, String key)
            throws WeatherException {
        return Double.parseDouble(text(object, key));
    }

    private static Integer optionalInteger(JsonValue.ObjectValue object, String key)
            throws WeatherException {
        String value = optionalText(object, key);
        return value.isEmpty() ? null : Integer.valueOf(value);
    }

    private static OffsetDateTime offset(JsonValue.ObjectValue object, String key)
            throws WeatherException {
        String value = optionalText(object, key);
        return value.isEmpty() ? null : OffsetDateTime.parse(value);
    }

    private static WeatherReference reference(JsonValue.ObjectValue root)
            throws WeatherException {
        JsonValue value = root.optional("refer").orElse(null);
        if (value == null || value == JsonValue.NullValue.INSTANCE) {
            return new WeatherReference(optionalText(root, "fxLink"), List.of(), List.of());
        }
        JsonValue.ObjectValue refer = value.asObject();
        return new WeatherReference(optionalText(root, "fxLink"),
                strings(refer, "sources"), strings(refer, "license"));
    }

    private static List<String> strings(JsonValue.ObjectValue object, String key)
            throws WeatherException {
        JsonValue value = object.optional(key).orElse(null);
        if (value == null || value == JsonValue.NullValue.INSTANCE) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonValue item : value.asArray()) {
            result.add(item.asString());
        }
        return List.copyOf(result);
    }

    private static WeatherException responseError(Exception cause) {
        return new WeatherException(WeatherException.Kind.RESPONSE,
                "天气响应包含无效字段", cause);
    }

}
