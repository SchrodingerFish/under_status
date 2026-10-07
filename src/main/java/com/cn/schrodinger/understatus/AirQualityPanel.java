package com.cn.schrodinger.understatus;

import com.cn.schrodinger.understatus.weather.AirQualitySnapshot;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.Locale;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.UIManager;

/**
 * Modern visual card panel for Air Quality details (空气质量详情).
 * Features an AQI status summary card with color indicator,
 * and 6-grid pollutant metrics with units.
 */
public class AirQualityPanel extends JPanel {

    private final JPanel contentPanel = new JPanel();

    public AirQualityPanel() {
        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        contentPanel.setLayout(new BorderLayout(0, 12));
        contentPanel.setOpaque(false);

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        renderEmpty();
    }

    public void setAirQuality(AirQualitySnapshot snapshot) {
        if (snapshot == null) {
            renderEmpty();
            return;
        }

        contentPanel.removeAll();
        boolean isDark = isDarkTheme();

        // 1. Top AQI Summary Card
        JPanel summaryCard = createSummaryCard(snapshot, isDark);
        contentPanel.add(summaryCard, BorderLayout.NORTH);

        // 2. Pollutants 6-Grid Cards
        JPanel gridPanel = createPollutantsGrid(snapshot.pollutants(), isDark);
        contentPanel.add(gridPanel, BorderLayout.CENTER);

        contentPanel.revalidate();
        contentPanel.repaint();
    }

    private void renderEmpty() {
        contentPanel.removeAll();
        JLabel label = new JLabel("暂无空气质量数据");
        label.setHorizontalAlignment(JLabel.CENTER);
        label.setFont(new Font("SansSerif", Font.PLAIN, 13));
        label.setForeground(Color.GRAY);
        contentPanel.add(label, BorderLayout.CENTER);
        contentPanel.revalidate();
        contentPanel.repaint();
    }

    private JPanel createSummaryCard(AirQualitySnapshot a, boolean isDark) {
        JPanel card = new JPanel(new BorderLayout(16, 8));
        Color bg = isDark ? new Color(34, 40, 54) : new Color(255, 255, 255);
        Color border = isDark ? new Color(60, 72, 94) : new Color(225, 232, 242);
        card.setBackground(bg);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border, 1, true),
                BorderFactory.createEmptyBorder(14, 18, 14, 18)
        ));

        // Left: Big AQI score + Level badge
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        leftPanel.setOpaque(false);

        JLabel aqiNumber = new JLabel(String.valueOf(a.aqi()));
        aqiNumber.setFont(new Font("SansSerif", Font.BOLD, 36));
        Color aqiColor = getAqiColor(a.aqi());
        aqiNumber.setForeground(aqiColor);
        leftPanel.add(aqiNumber);

        JPanel infoStack = new JPanel(new GridLayout(2, 1, 2, 2));
        infoStack.setOpaque(false);

        JLabel catBadge = new JLabel(" " + (a.category().isEmpty() ? "空气指数" : a.category()) + " ");
        catBadge.setFont(new Font("SansSerif", Font.BOLD, 12));
        catBadge.setOpaque(true);
        catBadge.setBackground(aqiColor);
        catBadge.setForeground(Color.WHITE);
        catBadge.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        infoStack.add(catBadge);

        String primText = (a.primaryPollutant().isEmpty() || "none".equalsIgnoreCase(a.primaryPollutant()))
                ? "首要污染物: 无" : ("首要污染物: " + a.primaryPollutant());
        JLabel primLabel = new JLabel(primText);
        primLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        primLabel.setForeground(isDark ? new Color(175, 190, 210) : new Color(100, 115, 135));
        infoStack.add(primLabel);

        leftPanel.add(infoStack);
        card.add(leftPanel, BorderLayout.WEST);

        // Right: attribution tag or status
        if (!a.attributionTag().isEmpty()) {
            JLabel attrLabel = new JLabel("许可: " + a.attributionTag());
            attrLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
            attrLabel.setForeground(isDark ? new Color(140, 155, 175) : new Color(130, 145, 165));
            card.add(attrLabel, BorderLayout.EAST);
        }

        return card;
    }

    private JPanel createPollutantsGrid(Map<String, Double> pollutants, boolean isDark) {
        JPanel grid = new JPanel(new GridLayout(2, 3, 10, 10));
        grid.setOpaque(false);

        String[] keys = new String[]{"pm2p5", "pm10", "o3", "no2", "so2", "co"};
        String[] titles = new String[]{"PM2.5 细颗粒物", "PM10 可吸入颗粒物", "O3 臭氧", "NO2 二氧化氮", "SO2 二氧化硫", "CO 一氧化碳"};
        String[] units = new String[]{"μg/m³", "μg/m³", "μg/m³", "μg/m³", "μg/m³", "mg/m³"};

        for (int i = 0; i < keys.length; i++) {
            Double val = pollutants.get(keys[i]);
            grid.add(createPollutantTile(titles[i], val, units[i], isDark));
        }

        return grid;
    }

    private JPanel createPollutantTile(String title, Double val, String unit, boolean isDark) {
        JPanel tile = new JPanel(new BorderLayout(6, 4));
        Color bg = isDark ? new Color(34, 40, 54) : new Color(255, 255, 255);
        Color border = isDark ? new Color(60, 72, 94) : new Color(225, 232, 242);
        tile.setBackground(bg);
        tile.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border, 1, true),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        titleLabel.setForeground(isDark ? new Color(160, 175, 195) : new Color(95, 110, 130));
        tile.add(titleLabel, BorderLayout.NORTH);

        String valStr = (val != null) ? String.format(Locale.US, "%.1f %s", val, unit) : "-- " + unit;
        JLabel valLabel = new JLabel(valStr);
        valLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        valLabel.setForeground(isDark ? new Color(230, 238, 250) : new Color(30, 45, 65));
        tile.add(valLabel, BorderLayout.CENTER);

        return tile;
    }

    private static Color getAqiColor(int aqi) {
        if (aqi <= 50)  return new Color(46, 175, 90);   // 优 - 绿
        if (aqi <= 100) return new Color(245, 158, 11);  // 良 - 橙黄
        if (aqi <= 150) return new Color(249, 115, 22);  // 轻度 - 橙
        if (aqi <= 200) return new Color(239, 68, 68);   // 中度 - 红
        if (aqi <= 300) return new Color(168, 85, 247);  // 重度 - 紫
        return new Color(136, 19, 55);                   // 严重 - 褐红
    }

    private boolean isDarkTheme() {
        Color bg = getBackground();
        if (bg == null) bg = UIManager.getColor("Panel.background");
        if (bg == null) return false;
        double lum = 0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue();
        return lum < 128;
    }
}
