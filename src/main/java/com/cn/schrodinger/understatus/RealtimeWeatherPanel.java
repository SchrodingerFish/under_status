package com.cn.schrodinger.understatus;

import com.cn.schrodinger.understatus.weather.GridWeatherNow;
import com.cn.schrodinger.understatus.weather.WeatherNow;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.UIManager;

/**
 * Modern, visually appealing Realtime Weather Dashboard (实时天气详情看板).
 * Used for both City Realtime Weather (城市天气·实时) and Grid Realtime Weather (格点天气·实时).
 * Features a dynamic Hero Weather Overview Card and an 8-tile metrics grid.
 */
public class RealtimeWeatherPanel extends JPanel {

    private final JPanel contentPanel = new JPanel();

    public RealtimeWeatherPanel() {
        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));

        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setOpaque(false);

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        renderEmpty(UiDefaults.text("Weather.now.loading"));
    }

    public void setCityWeather(WeatherNow w, String locationName, boolean isStale) {
        if (w == null) {
            renderEmpty(UiDefaults.text("Weather.now.empty"));
            return;
        }

        UiDefaults.textAlternative(this, UiDefaults.text("Weather.now.name"),
                UiDefaults.text("Weather.now.summary", locationName == null ? UiDefaults.text("Status.unavailable") : locationName,
                        w.updateTime() == null ? UiDefaults.text("Status.unavailable") : w.updateTime(),
                        UiDefaults.text(isStale ? "Status.stale" : "Status.current"), w.temperatureCelsius(),
                        w.condition(), w.windDirection(), w.windSpeedKph(), w.humidityPercent(),
                        w.precipitationMm(), w.pressureHpa(),
                        w.cloudPercent() == null ? UiDefaults.text("Status.unavailable") : w.cloudPercent() + "%",
                        w.dewPointCelsius() == null ? UiDefaults.text("Status.unavailable") : w.dewPointCelsius() + "°C")
                + UiDefaults.text("Weather.now.station", w.feelsLikeCelsius(), w.visibilityKm()));
        contentPanel.removeAll();
        boolean isDark = isDarkTheme();

        // 1. Hero Card
        JPanel heroCard = createHeroCard(
                w.temperatureCelsius(),
                w.feelsLikeCelsius(),
                w.condition(),
                locationName != null ? "📍 " + locationName : "📍 城市站点",
                w.updateTime() != null ? w.updateTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "--:--",
                w.windDirection() + " " + w.windScale() + "级 (" + w.windSpeedKph() + " km/h)",
                isStale,
                false,
                isDark
        );
        contentPanel.add(heroCard);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 12)));

        // 2. Metrics Grid (8 Tiles)
        JPanel grid = new JPanel(new GridLayout(2, 4, 10, 10));
        grid.setOpaque(false);

        // Tile 1: Wind
        grid.add(createMetricTile("💨 风向与风速",
                w.windDirection() + " " + w.windScale() + "级",
                w.windSpeedKph() + " km/h · 角度 " + w.windDegrees() + "°",
                new Color(14, 165, 233), isDark));

        // Tile 2: Humidity
        String humHint = w.humidityPercent() < 35 ? "干燥 · 建议补水"
                : (w.humidityPercent() > 75 ? "潮湿 · 体感略闷" : "湿度适宜 · 体感舒适");
        grid.add(createMetricTile("💧 相对湿度",
                w.humidityPercent() + "%",
                humHint,
                new Color(59, 130, 246), isDark));

        // Tile 3: Precip
        String precipHint = w.precipitationMm() <= 0.0 ? "近1小时无降水"
                : (w.precipitationMm() < 2.5 ? "有微量小雨" : "有降水过程");
        grid.add(createMetricTile("🌧️ 过去1小时降水",
                String.format(Locale.US, "%.1f mm", w.precipitationMm()),
                precipHint,
                new Color(16, 185, 129), isDark));

        // Tile 4: Visibility
        String visHint = w.visibilityKm() >= 10.0 ? "视野极佳 · 通透"
                : (w.visibilityKm() >= 5.0 ? "视野良好" : "轻度低能见度");
        grid.add(createMetricTile("👁️ 能见度",
                String.format(Locale.US, "%.1f km", w.visibilityKm()),
                visHint,
                new Color(139, 92, 246), isDark));

        // Tile 5: Pressure
        String pressHint = w.pressureHpa() < 1005 ? "偏低气压"
                : (w.pressureHpa() > 1025 ? "偏高气压" : "标准常压环境");
        grid.add(createMetricTile("🌡️ 大气压强",
                w.pressureHpa() + " hPa",
                pressHint,
                new Color(245, 158, 11), isDark));

        // Tile 6: Cloud Cover
        String cloudVal = w.cloudPercent() != null ? w.cloudPercent() + "%" : "--";
        String cloudHint = (w.cloudPercent() != null && w.cloudPercent() < 30) ? "晴空少云"
                : ((w.cloudPercent() != null && w.cloudPercent() > 70) ? "云层较密" : "部分云层");
        grid.add(createMetricTile("☁️ 云量覆盖",
                cloudVal,
                cloudHint,
                new Color(100, 116, 139), isDark));

        // Tile 7: Dew point
        String dewVal = w.dewPointCelsius() != null ? w.dewPointCelsius() + "°C" : "--";
        grid.add(createMetricTile("❄️ 露点温度",
                dewVal,
                "水分饱和凝结温度",
                new Color(6, 182, 212), isDark));

        // Tile 8: Station Info
        grid.add(createMetricTile("🧭 观测站点",
                "国家气象观测站",
                "官方地面基准观测",
                new Color(16, 185, 129), isDark));

        contentPanel.add(grid);
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    public void setGridWeather(GridWeatherNow w, String coordinate, boolean isStale) {
        if (w == null) {
            renderEmpty(UiDefaults.text("Weather.grid.empty"));
            return;
        }

        UiDefaults.textAlternative(this, UiDefaults.text("Weather.now.name"),
                UiDefaults.text("Weather.now.summary", coordinate == null ? UiDefaults.text("Status.unavailable") : coordinate,
                        w.updateTime() == null ? UiDefaults.text("Status.unavailable") : w.updateTime(),
                        UiDefaults.text(isStale ? "Status.stale" : "Status.current"), w.temperatureCelsius(),
                        w.condition(), w.windDirection(), w.windSpeedKph(), w.humidityPercent(),
                        w.precipitationMm(), w.pressureHpa(),
                        w.cloudPercent() == null ? UiDefaults.text("Status.unavailable") : w.cloudPercent() + "%",
                        w.dewPointCelsius() == null ? UiDefaults.text("Status.unavailable") : w.dewPointCelsius() + "°C"));
        contentPanel.removeAll();
        boolean isDark = isDarkTheme();

        // 1. Hero Card
        JPanel heroCard = createHeroCard(
                w.temperatureCelsius(),
                null,
                w.condition(),
                coordinate != null ? "📍 坐标网格: " + coordinate : "📍 1km气象网格",
                w.updateTime() != null ? w.updateTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "--:--",
                w.windDirection() + " " + w.windScale() + "级 (" + w.windSpeedKph() + " km/h)",
                isStale,
                true,
                isDark
        );
        contentPanel.add(heroCard);
        contentPanel.add(Box.createRigidArea(new Dimension(0, 12)));

        // 2. Metrics Grid (8 Tiles)
        JPanel grid = new JPanel(new GridLayout(2, 4, 10, 10));
        grid.setOpaque(false);

        // Tile 1: Wind
        grid.add(createMetricTile("💨 风向与风速",
                w.windDirection() + " " + w.windScale() + "级",
                w.windSpeedKph() + " km/h · 角度 " + w.windDegrees() + "°",
                new Color(14, 165, 233), isDark));

        // Tile 2: Humidity
        String humHint = w.humidityPercent() < 35 ? "干燥 · 建议补水"
                : (w.humidityPercent() > 75 ? "潮湿 · 体感略闷" : "湿度适宜 · 体感舒适");
        grid.add(createMetricTile("💧 相对湿度",
                w.humidityPercent() + "%",
                humHint,
                new Color(59, 130, 246), isDark));

        // Tile 3: Precip
        String precipHint = w.precipitationMm() <= 0.0 ? "近1小时无降水"
                : (w.precipitationMm() < 2.5 ? "有微量小雨" : "有降水过程");
        grid.add(createMetricTile("🌧️ 过去1小时降水",
                String.format(Locale.US, "%.1f mm", w.precipitationMm()),
                precipHint,
                new Color(16, 185, 129), isDark));

        // Tile 4: Grid Resolution
        grid.add(createMetricTile("🎯 网格空间分辨率",
                "1 km × 1 km",
                "超精细格点气象插值",
                new Color(139, 92, 246), isDark));

        // Tile 5: Pressure
        String pressHint = w.pressureHpa() < 1005 ? "偏低气压"
                : (w.pressureHpa() > 1025 ? "偏高气压" : "标准常压环境");
        grid.add(createMetricTile("🌡️ 大气压强",
                w.pressureHpa() + " hPa",
                pressHint,
                new Color(245, 158, 11), isDark));

        // Tile 6: Cloud Cover
        String cloudVal = w.cloudPercent() != null ? w.cloudPercent() + "%" : "--";
        String cloudHint = (w.cloudPercent() != null && w.cloudPercent() < 30) ? "晴空少云"
                : ((w.cloudPercent() != null && w.cloudPercent() > 70) ? "云层较密" : "部分云层");
        grid.add(createMetricTile("☁️ 云量覆盖",
                cloudVal,
                cloudHint,
                new Color(100, 116, 139), isDark));

        // Tile 7: Dew point
        String dewVal = w.dewPointCelsius() != null ? w.dewPointCelsius() + "°C" : "--";
        grid.add(createMetricTile("❄️ 露点温度",
                dewVal,
                "水分饱和凝结温度",
                new Color(6, 182, 212), isDark));

        // Tile 8: Technology
        grid.add(createMetricTile("🧭 模型类型",
                "智能网格融合",
                "雷达/卫星/地面多源插值",
                new Color(16, 185, 129), isDark));

        contentPanel.add(grid);
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    private JPanel createHeroCard(int temp, Integer feelsLike, String condition,
            String locationLabel, String updateTime, String windSummary,
            boolean isStale, boolean isGrid, boolean isDark) {

        JPanel card = new JPanel(new BorderLayout(16, 10)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bgTop = UiDefaults.background();
                Color bgBottom = UiDefaults.background();
                g2.setPaint(new java.awt.GradientPaint(0, 0, bgTop, 0, getHeight(), bgBottom));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(isDark ? new Color(64, 76, 102) : new Color(210, 224, 242));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        // Left Panel: Emoji, Temperature, and Weather Badges
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        left.setOpaque(false);

        String emoji = getWeatherEmoji(condition);
        JLabel emojiLabel = new JLabel(emoji);
        emojiLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 40));
        left.add(emojiLabel);

        JLabel tempLabel = new JLabel(temp + "°");
        tempLabel.setFont(UiDefaults.font(Font.BOLD, 46));
        tempLabel.setForeground(UiDefaults.foreground());
        left.add(tempLabel);

        JPanel badgeStack = new JPanel(new GridLayout(2, 1, 4, 4));
        badgeStack.setOpaque(false);

        JPanel badgesRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        badgesRow.setOpaque(false);

        JLabel condBadge = createPillBadge(condition, new Color(59, 130, 246), Color.WHITE);
        badgesRow.add(condBadge);

        if (feelsLike != null) {
            JLabel feelsBadge = createPillBadge("体感 " + feelsLike + "°C",
                    isDark ? new Color(50, 62, 85) : new Color(220, 230, 245),
                    isDark ? new Color(200, 215, 235) : new Color(40, 60, 90));
            badgesRow.add(feelsBadge);
        }
        badgeStack.add(badgesRow);

        JLabel windLabel = new JLabel(windSummary);
        windLabel.setFont(UiDefaults.font(Font.PLAIN, 12));
        windLabel.setForeground(UiDefaults.foreground());
        badgeStack.add(windLabel);

        left.add(badgeStack);
        card.add(left, BorderLayout.WEST);

        // Right Panel: Location, Update Time, and Status Badge
        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setOpaque(false);

        JLabel locLabel = new JLabel(locationLabel);
        locLabel.setFont(UiDefaults.font(Font.BOLD, 13));
        locLabel.setForeground(UiDefaults.foreground());
        locLabel.setAlignmentX(1.0f);
        right.add(locLabel);

        right.add(Box.createRigidArea(new Dimension(0, 4)));

        String timeStr = UiDefaults.text("Weather.updated", updateTime, UiDefaults.text(isStale ? "Status.stale" : "Status.current"));
        JLabel timeLabel = new JLabel(timeStr);
        timeLabel.setFont(UiDefaults.font(Font.PLAIN, 11));
        timeLabel.setForeground(UiDefaults.foreground());
        timeLabel.setAlignmentX(1.0f);
        right.add(timeLabel);

        right.add(Box.createRigidArea(new Dimension(0, 4)));

        String tagTitle = isGrid ? "1公里高精网格" : "城市地面观测";
        JLabel tagLabel = createPillBadge(tagTitle,
                isDark ? new Color(40, 52, 70) : new Color(225, 235, 248),
                isDark ? new Color(130, 180, 245) : new Color(30, 90, 180));
        tagLabel.setAlignmentX(1.0f);
        right.add(tagLabel);

        card.add(right, BorderLayout.EAST);
        return card;
    }

    private JPanel createMetricTile(String title, String value, String subtitle, Color accentColor, boolean isDark) {
        JPanel tile = new JPanel(new BorderLayout(6, 4));
        Color bg = UiDefaults.background();
        Color border = UiDefaults.border();
        tile.setBackground(bg);
        tile.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border, 1, true),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));

        // Title row with accent dot
        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        topRow.setOpaque(false);
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(UiDefaults.font(Font.PLAIN, 11));
        titleLabel.setForeground(UiDefaults.foreground());
        topRow.add(titleLabel);
        tile.add(topRow, BorderLayout.NORTH);

        // Big value
        JLabel valLabel = new JLabel(value);
        valLabel.setFont(UiDefaults.font(Font.BOLD, 15));
        valLabel.setForeground(UiDefaults.foreground());
        tile.add(valLabel, BorderLayout.CENTER);

        // Subtitle / qualitative hint
        JLabel subLabel = new JLabel(subtitle);
        subLabel.setFont(UiDefaults.font(Font.PLAIN, 10));
        subLabel.setForeground(UiDefaults.foreground());
        tile.add(subLabel, BorderLayout.SOUTH);

        return tile;
    }

    private JLabel createPillBadge(String text, Color bg, Color fg) {
        JLabel badge = new JLabel(" " + text + " ");
        badge.setFont(UiDefaults.font(Font.BOLD, 11));
        badge.setOpaque(true);
        badge.setBackground(bg);
        badge.setForeground(UiDefaults.onColor(bg));
        badge.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        return badge;
    }

    private static String getWeatherEmoji(String condition) {
        if (condition == null) return "🌤️";
        if (condition.contains("晴")) return "☀️";
        if (condition.contains("多云")) return "⛅";
        if (condition.contains("阴")) return "☁️";
        if (condition.contains("雷")) return "⛈️";
        if (condition.contains("暴雨") || condition.contains("大雨")) return "🌧️";
        if (condition.contains("雨")) return "🌦️";
        if (condition.contains("雪")) return "❄️";
        if (condition.contains("雾") || condition.contains("霾")) return "🌫️";
        if (condition.contains("风") || condition.contains("沙")) return "💨";
        return "🌤️";
    }

    private void renderEmpty(String msg) {
        UiDefaults.textAlternative(this, UiDefaults.text("Weather.now.name"), msg);
        contentPanel.removeAll();
        JLabel label = new JLabel(msg);
        label.setHorizontalAlignment(JLabel.CENTER);
        label.setFont(UiDefaults.font(Font.PLAIN, 13));
        label.setForeground(UiDefaults.foreground());
        label.setBorder(BorderFactory.createEmptyBorder(40, 0, 40, 0));
        contentPanel.add(label);
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    private boolean isDarkTheme() {
        Color bg = getBackground();
        if (bg == null) bg = UIManager.getColor("Panel.background");
        if (bg == null) return false;
        double lum = 0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue();
        return lum < 128;
    }
}
