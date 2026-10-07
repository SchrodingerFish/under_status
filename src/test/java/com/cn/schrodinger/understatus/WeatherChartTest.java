package com.cn.schrodinger.understatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cn.schrodinger.understatus.weather.MinutelyPrecipitation;
import com.cn.schrodinger.understatus.weather.AirQualitySnapshot;
import com.cn.schrodinger.understatus.weather.WeatherNow;
import com.cn.schrodinger.understatus.weather.WeatherIndex;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class WeatherChartTest {

    @Test
    void testWeatherChartPanelMetricsAndPreferredSize() {
        List<QWeatherService.HourlyForecast> forecasts = new ArrayList<>();
        for (int i = 0; i < 72; i++) {
            forecasts.add(new QWeatherService.HourlyForecast(
                    String.format("%02d:00", i % 24),
                    String.format("10-%02d %02d:00", (i / 24) + 1, i % 24),
                    18 + (i % 8),
                    "多云",
                    50 + (i % 30),
                    (i % 5 == 0) ? 1.5 : 0.0,
                    12 + (i % 6),
                    "东北风",
                    "2级",
                    1012,
                    10,
                    String.format("10-%02d", (i / 24) + 1)
            ));
        }

        WeatherChartPanel panel = new WeatherChartPanel(forecasts);
        Dimension dim72 = panel.getPreferredSize();
        // Width should expand for 72 points so points do not squish
        assertTrue(dim72.width >= 72 * 50);

        // Test metric switching
        panel.setMetric(WeatherChartPanel.Metric.HUMIDITY);
        assertTrue(panel.getAccessibleContext().getAccessibleName().contains("湿度"));
        assertTrue(panel.getAccessibleContext().getAccessibleDescription().contains("10-01 00:00"));
        assertTrue(panel.getAccessibleContext().getAccessibleDescription().contains("1.5"));
        assertEquals(WeatherChartPanel.Metric.HUMIDITY, panel.getMetric());
        assertEquals("65%", WeatherChartPanel.Metric.HUMIDITY.formatValue(65.0));
        assertEquals("2.5mm", WeatherChartPanel.Metric.PRECIPITATION.formatValue(2.5));

        // Render to image buffer to verify paintComponent runs without exception
        panel.setSize(dim72);
        BufferedImage image = new BufferedImage(dim72.width, dim72.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        panel.paint(g2);
        g2.dispose();
        assertNotNull(image);
    }

    @Test
    void testDailyWeatherChartPanelRendering() {
        List<DailyWeatherChartPanel.DailyItem> items = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            items.add(new DailyWeatherChartPanel.DailyItem(
                    String.format("10-%02d", i + 1),
                    i == 0 ? "今天" : "周" + (i % 7 + 1),
                    25 + (i % 5),
                    15 + (i % 4),
                    "晴",
                    "多云",
                    (i % 3 == 0) ? 2.0 : 0.0,
                    60,
                    "南风 2级",
                    6,
                    "06:10",
                    "17:50"
            ));
        }

        DailyWeatherChartPanel dailyPanel = new DailyWeatherChartPanel();
        dailyPanel.setDailyForecasts(items);
        assertTrue(dailyPanel.getAccessibleContext().getAccessibleDescription().contains("10-15"));
        assertTrue(dailyPanel.getAccessibleContext().getAccessibleDescription().contains("17:50"));

        Dimension dim = dailyPanel.getPreferredSize();
        assertTrue(dim.width >= 15 * 70);

        dailyPanel.setSize(dim);
        BufferedImage image = new BufferedImage(dim.width, dim.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        dailyPanel.paint(g2);
        g2.dispose();
        assertNotNull(image);
    }

    @Test
    void testPrecipitationChartPanelRendering() {
        List<MinutelyPrecipitation.Point> points = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now();
        for (int i = 0; i < 24; i++) {
            points.add(new MinutelyPrecipitation.Point(
                    now.plusMinutes(i * 5L),
                    (i % 4 == 0) ? 1.2 : 0.1,
                    "rain"
            ));
        }

        PrecipitationChartPanel precipPanel = new PrecipitationChartPanel();
        precipPanel.setPoints(points);
        assertTrue(precipPanel.getAccessibleContext().getAccessibleDescription().contains("1.2"));
        assertTrue(precipPanel.getAccessibleContext().getAccessibleDescription().contains("rain"));

        precipPanel.setSize(760, 240);
        BufferedImage image = new BufferedImage(760, 240, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        precipPanel.paint(g2);
        g2.dispose();
        assertNotNull(image);
    }

    @Test void replacingOrClearingChartDataReplacesAccessibleDescription() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PrecipitationChartPanel panel = new PrecipitationChartPanel();
            panel.setPoints(List.of(new MinutelyPrecipitation.Point(OffsetDateTime.parse("2026-10-07T12:00:00+08:00"), 3.2, "rain")));
            assertTrue(panel.getAccessibleContext().getAccessibleDescription().contains("3.2"));
            panel.setPoints(List.of());
            assertEquals(UiDefaults.text("Weather.precipitation.empty"), panel.getAccessibleContext().getAccessibleDescription());
            WeatherChartPanel hourly = new WeatherChartPanel(null);
            assertEquals(UiDefaults.text("Weather.empty"), hourly.getAccessibleContext().getAccessibleDescription());
            assertTrue(hourly.isFocusable());
        });
    }

    @Test void weatherCardsExposeUnitsCacheStatusAndClearStaleDescriptions() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AirQualityPanel air = new AirQualityPanel();
            air.setAirQuality(new AirQualitySnapshot(OffsetDateTime.parse("2026-10-07T12:00:00+08:00"),
                    55, "良", "pm2p5", Map.of("pm2p5", 30.0, "co", 0.4), "", null));
            String airText = air.getAccessibleContext().getAccessibleDescription();
            assertTrue(airText.contains("AQI 55"));
            assertTrue(airText.contains("0.4 mg/m³"));
            air.setAirQuality(null);
            assertEquals(UiDefaults.text("Weather.air.empty"), air.getAccessibleContext().getAccessibleDescription());

            RealtimeWeatherPanel now = new RealtimeWeatherPanel();
            now.setCityWeather(new WeatherNow(OffsetDateTime.parse("2026-10-07T12:00:00+08:00"),
                    20, 19, "100", "晴", 90, "东风", "2", 10, 60, 0, 1010, 12, 10, 5, null), "测试城市", true);
            assertTrue(now.getAccessibleContext().getAccessibleDescription().contains(UiDefaults.text("Status.stale")));
            assertTrue(now.getAccessibleContext().getAccessibleDescription().contains("测试城市"));
            now.setCityWeather(null, "", false);
            assertEquals(UiDefaults.text("Weather.now.empty"), now.getAccessibleContext().getAccessibleDescription());

            WeatherIndicesPanel indices = new WeatherIndicesPanel();
            indices.setIndices(List.of(new WeatherIndex(LocalDate.of(2026, 10, 7), 1, "运动", "1", "适宜", "适合户外运动", null)));
            assertTrue(indices.getAccessibleContext().getAccessibleDescription().contains("适合户外运动"));
            indices.setIndices(List.of());
            assertEquals(UiDefaults.text("Weather.indices.empty"), indices.getAccessibleContext().getAccessibleDescription());
        });
    }
}
