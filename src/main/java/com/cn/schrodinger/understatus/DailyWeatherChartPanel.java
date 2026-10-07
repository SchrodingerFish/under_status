package com.cn.schrodinger.understatus;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.GeneralPath;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.Locale;
import javax.swing.JPanel;

/**
 * Modern high-low temperature dual trend chart panel for daily weather forecasts.
 * Features dual bezier smoothed curves, temperature span gradient fill,
 * daytime/nighttime weather emoji rows, wind & rain badges,
 * horizontal auto-scrolling, and interactive hover tooltips.
 */
public class DailyWeatherChartPanel extends JPanel {

    public static final class DailyItem {
        public final String date;          // e.g. "10-08"
        public final String week;          // e.g. "今天", "周三"
        public final int maxTemp;
        public final int minTemp;
        public final String dayCondition;
        public final String nightCondition;
        public final double precipitationMm;
        public final int humidityPercent;
        public final String windInfo;
        public final int uvIndex;          // -1 if none
        public final String sunrise;
        public final String sunset;

        public DailyItem(String date, String week, int maxTemp, int minTemp,
                String dayCondition, String nightCondition, double precipitationMm,
                int humidityPercent, String windInfo, int uvIndex,
                String sunrise, String sunset) {
            this.date = date;
            this.week = week;
            this.maxTemp = maxTemp;
            this.minTemp = minTemp;
            this.dayCondition = dayCondition != null ? dayCondition : "晴";
            this.nightCondition = nightCondition != null ? nightCondition : "晴";
            this.precipitationMm = precipitationMm;
            this.humidityPercent = humidityPercent;
            this.windInfo = windInfo != null ? windInfo : "";
            this.uvIndex = uvIndex;
            this.sunrise = sunrise != null ? sunrise : "";
            this.sunset = sunset != null ? sunset : "";
        }
    }

    private static final int COL_WIDTH = 75;
    private static final int DEFAULT_HEIGHT = 290;

    private static final Color HIGH_COLOR = new Color(255, 122, 69); // Warm Coral
    private static final Color LOW_COLOR  = new Color(24, 144, 255); // Cool Azure

    private List<DailyItem> items = List.of();
    private int hoverIndex = -1;
    private float[] cachedXs;
    private float[] cachedHighYs;
    private float[] cachedLowYs;

    public DailyWeatherChartPanel() {
        setOpaque(false);
        setupMouseListeners();
        updateAccessibleData();
    }

    public void setDailyForecasts(List<DailyItem> items) {
        this.items = (items != null) ? List.copyOf(items) : List.of();
        this.hoverIndex = -1;
        cachedXs = null;
        updateAccessibleData();
        revalidate();
        repaint();
    }

    private void updateAccessibleData() {
        StringBuilder description = new StringBuilder();
        for (DailyItem item : items) {
            description.append(UiDefaults.text("Weather.daily.row", item.date, item.week,
                    item.maxTemp, item.minTemp, item.dayCondition, item.nightCondition,
                    item.precipitationMm, item.humidityPercent, item.windInfo,
                    item.uvIndex < 0 ? UiDefaults.text("Status.unavailable") : item.uvIndex,
                    item.sunrise, item.sunset)).append("\n");
        }
        UiDefaults.textAlternative(this, UiDefaults.text("Weather.daily.name"),
                description.isEmpty() ? UiDefaults.text("Weather.daily.empty") : description.toString());
    }

    @Override
    public Dimension getPreferredSize() {
        int n = items.size();
        int minW = 700;
        int padL = 35, padR = 35;
        int w = Math.max(minW, padL + padR + n * COL_WIDTH);
        return new Dimension(w, DEFAULT_HEIGHT);
    }

    private void setupMouseListeners() {
        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                updateHover(e.getX());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (hoverIndex != -1) {
                    hoverIndex = -1;
                    repaint();
                }
            }
        };
        addMouseListener(adapter);
        addMouseMotionListener(adapter);
    }

    private void updateHover(int mouseX) {
        if (items.isEmpty() || cachedXs == null) return;
        int n = items.size();
        int closest = -1;
        float minDist = Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            float dist = Math.abs(cachedXs[i] - mouseX);
            if (dist < minDist && dist <= COL_WIDTH * 0.7f) {
                minDist = dist;
                closest = i;
            }
        }
        if (closest != hoverIndex) {
            hoverIndex = closest;
            repaint();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (items.isEmpty()) {
            g.setColor(getForeground());
            g.drawString(UiDefaults.text("Weather.daily.empty"), 30, getHeight() / 2);
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,    RenderingHints.VALUE_STROKE_PURE);

        int w = Math.max(getWidth(), getPreferredSize().width);
        int h = getHeight();

        int padL = 35, padR = 35;
        int n = items.size();

        boolean isDark = isDarkTheme();
        Color textColor = UiDefaults.foreground();
        Color subTextColor = UiDefaults.foreground();
        Color haloColor = isDark ? new Color(25, 30, 42, 220) : new Color(255, 255, 255, 230);
        Color gridLineColor = isDark ? new Color(255, 255, 255, 20) : new Color(180, 200, 230, 90);

        // Compute global high/low temperatures across all days
        int globalMax = Integer.MIN_VALUE;
        int globalMin = Integer.MAX_VALUE;
        for (DailyItem item : items) {
            if (item.maxTemp > globalMax) globalMax = item.maxTemp;
            if (item.minTemp < globalMin) globalMin = item.minTemp;
        }
        if (globalMax == globalMin) {
            globalMax += 2; globalMin -= 2;
        }
        int tempRange = globalMax - globalMin;
        if (tempRange < 1) tempRange = 1;

        // Vertical layout sections
        int topHeaderY = 24;    // Weekday & Date
        int dayEmojiY  = 62;    // Day Weather Emoji + Text
        int curveTopY  = 98;    // High curve top margin
        int curveBotY  = 180;   // Low curve bottom margin
        int curveHeight = curveBotY - curveTopY;

        int nightEmojiY = 215;  // Night Weather Emoji + Text
        int windY       = 246;  // Wind direction & scale
        int rainY       = 266;  // Precipitation badge

        // Map points to columns
        float colSpacing = (w - padL - padR) / (float) n;
        float[] xs = new float[n];
        float[] highYs = new float[n];
        float[] lowYs = new float[n];

        for (int i = 0; i < n; i++) {
            xs[i] = padL + i * colSpacing + colSpacing / 2f;
            DailyItem item = items.get(i);
            highYs[i] = curveTopY + (curveHeight * (1f - (item.maxTemp - globalMin) / (float) tempRange));
            lowYs[i]  = curveTopY + (curveHeight * (1f - (item.minTemp - globalMin) / (float) tempRange));
        }
        this.cachedXs = xs;
        this.cachedHighYs = highYs;
        this.cachedLowYs = lowYs;

        // --- 1. Hover column highlight pill ---
        if (hoverIndex >= 0 && hoverIndex < n) {
            float hx = padL + hoverIndex * colSpacing + 4;
            float hw = colSpacing - 8;
            g2.setColor(isDark ? new Color(64, 156, 255, 35) : new Color(64, 156, 255, 25));
            g2.fill(new RoundRectangle2D.Float(hx, 10, hw, h - 20, 12, 12));
            g2.setColor(isDark ? new Color(64, 156, 255, 70) : new Color(64, 156, 255, 60));
            g2.setStroke(new BasicStroke(1f));
            g2.draw(new RoundRectangle2D.Float(hx, 10, hw, h - 20, 12, 12));
        }

        // --- 2. Vertical column separator lines ---
        g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                1f, new float[]{3f, 4f}, 0f));
        g2.setColor(gridLineColor);
        for (int i = 1; i < n; i++) {
            int sepX = Math.round(padL + i * colSpacing);
            g2.drawLine(sepX, 16, sepX, h - 18);
        }

        // --- 3. Temperature span gradient band (between High Curve and Low Curve) ---
        GeneralPath bandPath = new GeneralPath();
        GeneralPath highPath = buildSmoothPath(xs, highYs, n);
        bandPath.append(highPath, false);
        // Connect from end of high curve down to low curve, then reverse
        for (int i = n - 1; i >= 0; i--) {
            if (i == n - 1) {
                bandPath.lineTo(xs[i], lowYs[i]);
            } else {
                float p0x = (i == n - 2) ? xs[n - 1] : xs[i + 2];
                float p0y = (i == n - 2) ? lowYs[n - 1] : lowYs[i + 2];
                float p1x = xs[i + 1];
                float p1y = lowYs[i + 1];
                float p2x = xs[i];
                float p2y = lowYs[i];
                float p3x = (i == 0) ? xs[0] : xs[i - 1];
                float p3y = (i == 0) ? lowYs[0] : lowYs[i - 1];

                float cp1x = p1x - (p0x - p2x) / 6f;
                float cp1y = p1y - (p0y - p2y) / 6f;
                float cp2x = p2x + (p1x - p3x) / 6f;
                float cp2y = p2y + (p1y - p3y) / 6f;
                bandPath.curveTo(cp1x, cp1y, cp2x, cp2y, p2x, p2y);
            }
        }
        bandPath.closePath();

        Color bandTop = new Color(HIGH_COLOR.getRed(), HIGH_COLOR.getGreen(), HIGH_COLOR.getBlue(), isDark ? 60 : 45);
        Color bandBot = new Color(LOW_COLOR.getRed(), LOW_COLOR.getGreen(), LOW_COLOR.getBlue(), isDark ? 50 : 35);
        g2.setPaint(new GradientPaint(0, curveTopY, bandTop, 0, curveBotY, bandBot));
        g2.fill(bandPath);

        // --- 4. Draw High & Low Curves ---
        // High temp curve
        g2.setColor(HIGH_COLOR);
        g2.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(buildSmoothPath(xs, highYs, n));

        // Low temp curve
        g2.setColor(LOW_COLOR);
        g2.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(buildSmoothPath(xs, lowYs, n));

        // --- 5. Draw per-day details ---
        Font weekFont   = UiDefaults.font(Font.BOLD, 12);
        Font dateFont   = UiDefaults.font(Font.PLAIN, 10);
        Font emojiFont  = new Font("Segoe UI Emoji", Font.PLAIN, 13);
        Font textFont   = UiDefaults.font(Font.PLAIN, 11);
        Font tempFont   = UiDefaults.font(Font.BOLD, 12);
        Font badgeFont  = UiDefaults.font(Font.PLAIN, 10);

        for (int i = 0; i < n; i++) {
            int ix = Math.round(xs[i]);
            int highY = Math.round(highYs[i]);
            int lowY  = Math.round(lowYs[i]);
            DailyItem item = items.get(i);

            // 1. Weekday
            g2.setFont(weekFont);
            FontMetrics wfm = g2.getFontMetrics();
            String weekStr = item.week;
            boolean isToday = "今天".equals(weekStr);
            g2.setColor(isToday ? HIGH_COLOR : textColor);
            g2.drawString(weekStr, ix - wfm.stringWidth(weekStr) / 2, topHeaderY);

            // 2. Date
            g2.setFont(dateFont);
            FontMetrics dfm = g2.getFontMetrics();
            g2.setColor(subTextColor);
            g2.drawString(item.date, ix - dfm.stringWidth(item.date) / 2, topHeaderY + 14);

            // 3. Day Emoji + Day Condition
            String dayEmoji = WeatherChartPanel.getWeatherEmoji(item.dayCondition);
            g2.setFont(emojiFont);
            FontMetrics efm = g2.getFontMetrics();
            g2.setColor(textColor);
            g2.drawString(dayEmoji, ix - efm.stringWidth(dayEmoji) / 2, dayEmojiY);

            g2.setFont(textFont);
            FontMetrics tfm = g2.getFontMetrics();
            g2.setColor(subTextColor);
            g2.drawString(item.dayCondition, ix - tfm.stringWidth(item.dayCondition) / 2, dayEmojiY + 14);

            // 4. High Temp Node & Label
            g2.setColor(HIGH_COLOR);
            g2.fillOval(ix - 4, highY - 4, 8, 8);
            g2.setColor(Color.WHITE);
            g2.fillOval(ix - 2, highY - 2, 4, 4);

            g2.setFont(tempFont);
            String maxStr = item.maxTemp + "°";
            int mw = g2.getFontMetrics().stringWidth(maxStr);
            drawHaloString(g2, maxStr, ix - mw / 2, highY - 8, HIGH_COLOR, haloColor);

            // 5. Low Temp Node & Label
            g2.setColor(LOW_COLOR);
            g2.fillOval(ix - 4, lowY - 4, 8, 8);
            g2.setColor(Color.WHITE);
            g2.fillOval(ix - 2, lowY - 2, 4, 4);

            g2.setFont(tempFont);
            String minStr = item.minTemp + "°";
            int lw = g2.getFontMetrics().stringWidth(minStr);
            drawHaloString(g2, minStr, ix - lw / 2, lowY + 18, LOW_COLOR, haloColor);

            // 6. Night Emoji + Night Condition
            String nightEmoji = WeatherChartPanel.getWeatherEmoji(item.nightCondition);
            g2.setFont(emojiFont);
            g2.setColor(textColor);
            g2.drawString(nightEmoji, ix - efm.stringWidth(nightEmoji) / 2, nightEmojiY);

            g2.setFont(textFont);
            g2.setColor(subTextColor);
            g2.drawString(item.nightCondition, ix - tfm.stringWidth(item.nightCondition) / 2, nightEmojiY + 14);

            // 7. Wind
            if (!item.windInfo.isEmpty()) {
                g2.setFont(badgeFont);
                FontMetrics wndfm = g2.getFontMetrics();
                String shortWind = item.windInfo;
                if (shortWind.length() > 6) {
                    shortWind = shortWind.substring(0, 5) + "…";
                }
                g2.setColor(subTextColor);
                g2.drawString(shortWind, ix - wndfm.stringWidth(shortWind) / 2, windY);
            }

            // 8. Rain badge (if precipitation > 0)
            if (item.precipitationMm > 0.05) {
                g2.setFont(badgeFont);
                String rainStr = String.format(Locale.US, "💧%.1fmm", item.precipitationMm);
                FontMetrics rfm = g2.getFontMetrics();
                int rw = rfm.stringWidth(rainStr) + 8;
                int rx = ix - rw / 2;
                g2.setColor(isDark ? new Color(24, 144, 255, 60) : new Color(24, 144, 255, 40));
                g2.fillRoundRect(rx, rainY - 11, rw, 15, 6, 6);
                g2.setColor(LOW_COLOR);
                g2.drawString(rainStr, rx + 4, rainY);
            }
        }

        // --- 6. Hover Details Tooltip Card ---
        if (hoverIndex >= 0 && hoverIndex < n) {
            drawDailyHoverCard(g2, hoverIndex, xs, w, h, isDark);
        }

        g2.dispose();
    }

    private void drawDailyHoverCard(Graphics2D g2, int idx, float[] xs, int totalW, int totalH, boolean isDark) {
        DailyItem item = items.get(idx);
        int ix = Math.round(xs[idx]);

        int cardW = 210;
        int cardH = 96;
        int cardX = ix - cardW / 2;
        if (cardX < 12) cardX = 12;
        if (cardX + cardW > totalW - 12) cardX = totalW - cardW - 12;
        int cardY = totalH - cardH - 14;

        Color cardBg = UiDefaults.background();
        Color cardBorder = UiDefaults.border();
        Color cardTitle = UiDefaults.foreground();
        Color cardBody = UiDefaults.foreground();

        g2.setColor(cardBg);
        g2.fill(new RoundRectangle2D.Float(cardX, cardY, cardW, cardH, 10, 10));
        g2.setColor(cardBorder);
        g2.setStroke(new BasicStroke(1f));
        g2.draw(new RoundRectangle2D.Float(cardX, cardY, cardW, cardH, 10, 10));

        int textX = cardX + 12;
        int textY = cardY + 20;

        // Line 1: Date + Week + Weather
        g2.setFont(UiDefaults.font(Font.BOLD, 12));
        g2.setColor(cardTitle);
        String dayEmoji = WeatherChartPanel.getWeatherEmoji(item.dayCondition);
        g2.drawString(item.date + " (" + item.week + ") · " + dayEmoji + " " + item.dayCondition, textX, textY);

        // Line 2: High / Low Temperature
        textY += 19;
        g2.setFont(UiDefaults.font(Font.BOLD, 12));
        g2.setColor(HIGH_COLOR);
        g2.drawString("最高 " + item.maxTemp + "°C", textX, textY);
        g2.setColor(cardBody);
        g2.drawString("  /  ", textX + 60, textY);
        g2.setColor(LOW_COLOR);
        g2.drawString("最低 " + item.minTemp + "°C", textX + 78, textY);
        g2.setColor(cardBody);
        g2.setFont(UiDefaults.font(Font.PLAIN, 10));
        g2.drawString(" (温差 " + (item.maxTemp - item.minTemp) + "°C)", textX + 138, textY);

        // Line 3: Rain & Humidity
        textY += 18;
        g2.setColor(cardBody);
        String sub1 = "降水: " + String.format(Locale.US, "%.1fmm", item.precipitationMm)
                + "  |  湿度: " + item.humidityPercent + "%";
        g2.drawString(sub1, textX, textY);

        // Line 4: Sunrise / Sunset or Wind
        textY += 17;
        String sub2 = (!item.sunrise.isEmpty() && !item.sunset.isEmpty())
                ? "日出: " + item.sunrise + "  日落: " + item.sunset
                : "风向风力: " + (item.windInfo.isEmpty() ? "微风" : item.windInfo);
        g2.drawString(sub2, textX, textY);
    }

    private void drawHaloString(Graphics2D g2, String str, int x, int y, Color textColor, Color haloColor) {
        g2.setColor(haloColor);
        g2.drawString(str, x - 1, y - 1);
        g2.drawString(str, x + 1, y - 1);
        g2.drawString(str, x - 1, y + 1);
        g2.drawString(str, x + 1, y + 1);
        g2.setColor(textColor);
        g2.drawString(str, x, y);
    }

    private GeneralPath buildSmoothPath(float[] xs, float[] ys, int n) {
        GeneralPath path = new GeneralPath();
        if (n == 0) return path;
        path.moveTo(xs[0], ys[0]);
        if (n == 1) return path;

        for (int i = 0; i < n - 1; i++) {
            float p0x = (i == 0) ? xs[0] : xs[i - 1];
            float p0y = (i == 0) ? ys[0] : ys[i - 1];
            float p1x = xs[i];
            float p1y = ys[i];
            float p2x = xs[i + 1];
            float p2y = ys[i + 1];
            float p3x = (i + 2 < n) ? xs[i + 2] : p2x;
            float p3y = (i + 2 < n) ? ys[i + 2] : p2y;

            float cp1x = p1x + (p2x - p0x) / 6f;
            float cp1y = p1y + (p2y - p0y) / 6f;
            float cp2x = p2x - (p3x - p1x) / 6f;
            float cp2y = p2y - (p3y - p1y) / 6f;

            path.curveTo(cp1x, cp1y, cp2x, cp2y, p2x, p2y);
        }
        return path;
    }

    private boolean isDarkTheme() {
        Color bg = getBackground();
        if (bg == null) bg = javax.swing.UIManager.getColor("Panel.background");
        if (bg == null) return false;
        double lum = 0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue();
        return lum < 128;
    }
}
