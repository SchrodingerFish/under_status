package com.cn.schrodinger.understatus.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.time.ZoneId;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QWeatherClientTest {

    private static final QWeatherConfig CONFIG = new QWeatherConfig(
            "abc.def.qweatherapi.com", "top-secret", "zh", "m");
    private static final LocationContext BEIJING = new LocationContext(
            "101010100", "北京", 116.41, 39.92, ZoneId.of("Asia/Shanghai"));

    @Test
    void mapsCurrentWeatherAndUsesHeaderAuthentication() throws Exception {
        RecordingTransport transport = new RecordingTransport();
        transport.response = "{\"code\":\"200\",\"updateTime\":\"2026-07-17T10:00+08:00\","
                + "\"fxLink\":\"https://www.qweather.com/weather/beijing-101010100.html\","
                + "\"now\":{\"temp\":\"31\",\"feelsLike\":\"33\",\"icon\":\"100\","
                + "\"text\":\"晴\",\"wind360\":\"180\",\"windDir\":\"南风\","
                + "\"windScale\":\"2\",\"windSpeed\":\"8\",\"humidity\":\"48\","
                + "\"precip\":\"0.0\",\"pressure\":\"1001\",\"vis\":\"18\","
                + "\"cloud\":\"10\",\"dew\":\"20\"},"
                + "\"refer\":{\"sources\":[\"QWeather\"],\"license\":[\"QWeather License\"]}}";

        WeatherNow result = new QWeatherClient(transport).fetchNow(CONFIG, BEIJING);

        assertEquals(31, result.temperatureCelsius());
        assertEquals(33, result.feelsLikeCelsius());
        assertEquals("晴", result.condition());
        assertEquals("top-secret", transport.headers.get("X-QW-Api-Key"));
        assertFalse(transport.uri.toString().contains("top-secret"));
        assertEquals("/v7/weather/now", transport.uri.getPath());
    }

    @Test
    void mapsDailyAndHourlyForecastsWithApprovedRanges() throws Exception {
        RecordingTransport transport = new RecordingTransport();
        QWeatherClient client = new QWeatherClient(transport);
        transport.response = "{\"code\":\"200\",\"daily\":[{\"fxDate\":\"2026-07-17\","
                + "\"sunrise\":\"05:00\",\"sunset\":\"19:40\",\"moonrise\":\"08:00\","
                + "\"moonset\":\"22:00\",\"moonPhase\":\"蛾眉月\",\"tempMax\":\"34\","
                + "\"tempMin\":\"23\",\"iconDay\":\"100\",\"textDay\":\"晴\","
                + "\"iconNight\":\"150\",\"textNight\":\"晴\",\"windDirDay\":\"南风\","
                + "\"windScaleDay\":\"2\",\"humidity\":\"50\",\"precip\":\"0.0\","
                + "\"pressure\":\"1000\",\"vis\":\"20\",\"cloud\":\"10\",\"uvIndex\":\"8\"}]}";
        List<DailyForecast> daily = client.fetchDaily(CONFIG, BEIJING, ForecastRange.DAYS_7);
        assertEquals(34, daily.get(0).maximumTemperatureCelsius());
        assertEquals("7d", lastPathPart(transport.uri));

        transport.response = "{\"code\":\"200\",\"hourly\":[{\"fxTime\":\"2026-07-17T11:00+08:00\","
                + "\"temp\":\"32\",\"icon\":\"100\",\"text\":\"晴\",\"pop\":\"10\","
                + "\"precip\":\"0.0\",\"wind360\":\"180\",\"windDir\":\"南风\","
                + "\"windScale\":\"2\",\"windSpeed\":\"8\",\"humidity\":\"45\","
                + "\"pressure\":\"1000\",\"cloud\":\"10\",\"dew\":\"20\"}]}";
        List<HourlyForecast> hourly = client.fetchHourly(CONFIG, BEIJING, 24);
        assertEquals(32, hourly.get(0).temperatureCelsius());
        assertEquals("24h", lastPathPart(transport.uri));
    }

    @Test
    void rejectsWrongRangeBeforeNetworkCall() {
        RecordingTransport transport = new RecordingTransport();
        QWeatherClient client = new QWeatherClient(transport);

        assertThrows(IllegalArgumentException.class,
                () -> client.fetchDaily(CONFIG, BEIJING, ForecastRange.HOURS_24));
        assertThrows(IllegalArgumentException.class,
                () -> client.fetchHourly(CONFIG, BEIJING, 48));
        assertEquals(0, transport.calls.size());
    }

    @Test
    void reportsAuthenticationFailureWithoutLeakingKey() {
        RecordingTransport transport = new RecordingTransport();
        transport.response = "{\"code\":\"401\"}";

        WeatherException error = assertThrows(WeatherException.class,
                () -> new QWeatherClient(transport).fetchNow(CONFIG, BEIJING));

        assertEquals(WeatherException.Kind.AUTHENTICATION, error.kind());
        assertFalse(error.getMessage().contains("top-secret"));
    }

    @Test
    void mapsGridWeatherAndUsesCoordinates() throws Exception {
        RecordingTransport transport = new RecordingTransport();
        transport.response = "{\"code\":\"200\",\"now\":{\"temp\":\"30\","
                + "\"icon\":\"100\",\"text\":\"晴\",\"wind360\":\"180\","
                + "\"windDir\":\"南风\",\"windScale\":\"2\",\"windSpeed\":\"8\","
                + "\"humidity\":\"48\",\"precip\":\"0.0\",\"pressure\":\"1001\"}}";

        GridWeatherNow result = new QWeatherClient(transport).fetchGridNow(CONFIG, BEIJING);

        assertEquals(30, result.temperatureCelsius());
        assertEquals("116.41,39.92", queryValue(transport.uri, "location"));
        assertEquals("/v7/grid-weather/now", transport.uri.getPath());
    }

    @Test
    void mapsAirQualityV1CoordinatePath() throws Exception {
        RecordingTransport transport = new RecordingTransport();
        transport.response = "{\"metadata\":{\"tag\":\"QWeather Attribution\"},"
                + "\"indexes\":[{\"aqi\":\"42\",\"category\":\"优\","
                + "\"primaryPollutant\":\"none\"}],\"pollutants\":[{\"code\":\"pm2p5\","
                + "\"concentration\":{\"value\":12.5,\"unit\":\"μg/m3\"}}]}";

        AirQualitySnapshot result = new QWeatherClient(transport)
                .fetchAirQuality(CONFIG, BEIJING);

        assertEquals(42, result.aqi());
        assertEquals(12.5, result.pollutants().get("pm2p5"));
        assertEquals("QWeather Attribution", result.attributionTag());
        assertEquals("/airquality/v1/current/39.92/116.41", transport.uri.getPath());
    }

    @Test
    void mapsHistoricalWeatherAndRejectsToday() throws Exception {
        RecordingTransport transport = new RecordingTransport();
        LocalDate yesterday = LocalDate.now(BEIJING.zoneId()).minusDays(1);
        transport.response = "{\"code\":\"200\",\"weatherDaily\":{\"date\":\""
                + yesterday + "\",\"sunrise\":\"05:00\",\"sunset\":\"19:40\","
                + "\"moonrise\":\"08:00\",\"moonset\":\"22:00\",\"moonPhase\":\"蛾眉月\","
                + "\"tempMax\":\"33\",\"tempMin\":\"23\",\"humidity\":\"52\","
                + "\"precip\":\"0.0\",\"pressure\":\"1000\"},\"weatherHourly\":[{"
                + "\"time\":\"" + yesterday + "T00:00+08:00\",\"temp\":\"28\","
                + "\"icon\":\"100\",\"text\":\"晴\",\"precip\":\"0.0\","
                + "\"wind360\":\"180\",\"windDir\":\"南风\",\"windScale\":\"2\","
                + "\"windSpeed\":\"8\",\"humidity\":\"49\",\"pressure\":\"1001\"}]}";

        HistoricalWeather result = new QWeatherClient(transport)
                .fetchHistorical(CONFIG, BEIJING, yesterday);

        assertEquals(33, result.daily().maximumTemperatureCelsius());
        assertEquals(1, result.hourly().size());
        assertEquals(yesterday.atStartOfDay(), result.hourly().get(0).time());
        assertEquals("/v7/historical/weather", transport.uri.getPath());
        assertEquals("101010100", queryValue(transport.uri, "location"));
        assertEquals(yesterday.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE),
                queryValue(transport.uri, "date"));
        assertThrows(IllegalArgumentException.class, () -> new QWeatherClient(transport)
                .fetchHistorical(CONFIG, BEIJING, LocalDate.now(BEIJING.zoneId())));
    }

    @Test
    void mapsAllIndicesAndMinutelyPrecipitation() throws Exception {
        RecordingTransport transport = new RecordingTransport();
        QWeatherClient client = new QWeatherClient(transport);
        transport.response = "{\"code\":\"200\",\"daily\":[{\"date\":\"2026-07-17\","
                + "\"type\":\"1\",\"name\":\"运动指数\",\"level\":\"1\","
                + "\"category\":\"适宜\",\"text\":\"适宜运动\"}]}";

        List<WeatherIndex> indices = client.fetchIndices(
                CONFIG, BEIJING, WeatherIndexRange.ONE_DAY);
        assertEquals("运动指数", indices.get(0).name());
        assertEquals("0", queryValue(transport.uri, "type"));

        transport.response = "{\"code\":\"200\",\"updateTime\":\"2026-07-17T10:00+08:00\","
                + "\"summary\":\"未来两小时无降水\",\"minutely\":[{"
                + "\"fxTime\":\"2026-07-17T10:05+08:00\",\"precip\":\"0.0\","
                + "\"type\":\"rain\"}]}";
        MinutelyPrecipitation minutely = client.fetchMinutely(CONFIG, BEIJING);
        assertEquals("未来两小时无降水", minutely.summary());
        assertEquals("rain", minutely.points().get(0).type());
        assertEquals("/v7/minutely/5m", transport.uri.getPath());
    }

    private static String lastPathPart(URI uri) {
        String path = uri.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static String queryValue(URI uri, String key) {
        for (String item : uri.getRawQuery().split("&")) {
            String[] parts = item.split("=", 2);
            if (key.equals(parts[0])) {
                return java.net.URLDecoder.decode(parts[1], java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    static final class RecordingTransport implements HttpTransport {
        String response = "{}";
        URI uri;
        Map<String, String> headers = Map.of();
        final List<URI> calls = new ArrayList<>();

        @Override
        public String get(URI uri) {
            return response;
        }

        @Override
        public String get(URI uri, Map<String, String> headers) {
            this.uri = uri;
            this.headers = Map.copyOf(headers);
            calls.add(uri);
            return response;
        }
    }
}
