package com.cn.schrodinger.understatus;

import com.cn.schrodinger.understatus.weather.JdkHttpTransport;
import com.cn.schrodinger.understatus.weather.LocationContext;
import com.cn.schrodinger.understatus.weather.LocationResolver;
import com.cn.schrodinger.understatus.weather.QWeatherClient;
import com.cn.schrodinger.understatus.weather.QWeatherConfig;
import com.cn.schrodinger.understatus.weather.WeatherCache;
import com.cn.schrodinger.understatus.weather.WeatherDataService;
import com.cn.schrodinger.understatus.weather.WeatherException;
import com.cn.schrodinger.understatus.weather.WeatherNow;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Shared QWeather facade for status-bar and detail UI actions. */
public final class QWeatherService {

    private static final JdkHttpTransport TRANSPORT = new JdkHttpTransport();
    private static final QWeatherClient CLIENT = new QWeatherClient(TRANSPORT);
    private static final WeatherDataService DATA = new WeatherDataService(CLIENT,
            new LocationResolver(TRANSPORT), new WeatherCache());
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private QWeatherService() {}

    public static WeatherDataService dataService() { return DATA; }

    public static QWeatherConfig config(String apiHost, String apiKey) {
        return new QWeatherConfig(apiHost, apiKey, "zh", "m");
    }

    public static String fetchWeather(String apiHost, String apiKey, String cityName,
            boolean autoIp) throws WeatherException {
        QWeatherConfig config = config(apiHost, apiKey);
        LocationContext location = DATA.resolve(config, cityName, autoIp, false);
        WeatherDataService.Result<WeatherNow> result = DATA.now(config, location, false);
        WeatherNow weather = result.value();
        return (result.stale() ? "⚠ 缓存已过期 · " : "") + location.name() + " " + emoji(weather.condition()) + " " + weather.condition()
                + " " + weather.temperatureCelsius() + "°C";
    }

    public static List<HourlyForecast> fetchHourlyForecast(String apiHost,
            String apiKey, String cityName, boolean autoIp) throws WeatherException {
        QWeatherConfig config = config(apiHost, apiKey);
        LocationContext location = DATA.resolve(config, cityName, autoIp, false);
        List<HourlyForecast> result = new ArrayList<>();
        List<com.cn.schrodinger.understatus.weather.HourlyForecast> forecasts =
                DATA.hourly(config, location, 24, false).value();
        for (com.cn.schrodinger.understatus.weather.HourlyForecast forecast : forecasts) {
            result.add(new HourlyForecast(
                    forecast.time().format(TIME),
                    forecast.time().format(DateTimeFormatter.ofPattern("MM-dd HH:mm")),
                    forecast.temperatureCelsius(),
                    forecast.condition(),
                    forecast.humidityPercent(),
                    forecast.precipitationMm(),
                    forecast.windSpeedKph(),
                    forecast.windDirection(),
                    forecast.windScale(),
                    forecast.pressureHpa(),
                    forecast.precipitationProbability(),
                    forecast.time().format(DateTimeFormatter.ofPattern("MM-dd"))));
        }
        return List.copyOf(result);
    }

    public static void clearCache() { DATA.clearAll(); }

    private static String emoji(String text) {
        if (text == null) return "🌡️";
        if (text.contains("晴")) return "☀️";
        if (text.contains("云")) return "⛅";
        if (text.contains("雨")) return "🌧️";
        if (text.contains("雪")) return "❄️";
        if (text.contains("雷")) return "⛈️";
        if (text.contains("雾")) return "🌫️";
        return "☁️";
    }

    public static final class HourlyForecast {
        public final String time;             // "HH:mm"
        public final String fullTime;         // "MM-dd HH:mm"
        public final int temp;                // °C
        public final String text;             // condition
        public final int humidity;            // %
        public final double precipitation;    // mm
        public final int windSpeed;           // km/h
        public final String windDirection;    // "东北风"
        public final String windScale;        // "2级"
        public final int pressure;            // hPa
        public final Integer pop;             // 降水概率 %
        public final String date;             // "MM-dd"

        public HourlyForecast(String time, int temp, String text) {
            this(time, time, temp, text, 0, 0.0, 0, "", "", 0, null, "");
        }

        public HourlyForecast(String time, String fullTime, int temp, String text,
                int humidity, double precipitation, int windSpeed,
                String windDirection, String windScale, int pressure,
                Integer pop, String date) {
            this.time = time;
            this.fullTime = fullTime != null ? fullTime : time;
            this.temp = temp;
            this.text = text;
            this.humidity = humidity;
            this.precipitation = precipitation;
            this.windSpeed = windSpeed;
            this.windDirection = windDirection != null ? windDirection : "";
            this.windScale = windScale != null ? windScale : "";
            this.pressure = pressure;
            this.pop = pop;
            this.date = date != null ? date : "";
        }
    }
}
