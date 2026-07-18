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
        WeatherNow weather = DATA.now(config, location, false).value();
        return location.name() + " " + emoji(weather.condition()) + " " + weather.condition()
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
            result.add(new HourlyForecast(forecast.time().format(TIME),
                    forecast.temperatureCelsius(), forecast.condition()));
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
        public final String time;
        public final int temp;
        public final String text;

        public HourlyForecast(String time, int temp, String text) {
            this.time = time;
            this.temp = temp;
            this.text = text;
        }
    }
}
