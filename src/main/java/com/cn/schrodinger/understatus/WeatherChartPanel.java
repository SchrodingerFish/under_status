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
 * Modern interactive weather trend chart panel.
 * Supports multiple metrics (temp, humidity, precipitation, wind speed, pressure),
 * dynamic width with horizontal scrolling, mouse hover crosshair & tooltip card,
 * Y-axis scale markers, date separators, and dark/light theme adaptation.
 */
public class WeatherChartPanel extends JPanel {

    public enum Metric {
        TEMPERATURE("气温", "°C", new Color(64, 156, 255), new Color(255, 125, 45)),
        HUMIDITY("湿度", "%", new Color(46, 204, 113), new Color(38, 166, 154)),
        PRECIPITATION("降水量", "mm", new Color(52, 152, 219), new Color(41, 128, 185)),
        WIND_SPEED("风速", "km/h", new Color(155, 89, 182), new Color(142, 68, 173)),
        PRESSURE("气压", "hPa", new Color(243, 156, 18), new Color(230, 126, 34));

        public final String label;
        public final String unit;
        public final Color primaryColor;
        public final Color accentColor;

        Metric(String label, String unit, Color primaryColor, Color accentColor) {
            this.label = label;
            this.unit = unit;
            this.primaryColor = primaryColor;
            this.accentColor = accentColor;
        }

        @Override
        public String toString() {
            return label + " (" + unit + ")";
        }

        public double extractValue(QWeatherService.HourlyForecast f) {
            return switch (this) {
                case TEMPERATURE -> f.temp;
                case HUMIDITY -> f.humidity;
                case PRECIPITATION -> f.precipitation;
                case WIND_SPEED -> f.windSpeed;
                case PRESSURE -> f.pressure;
            };
        }

        public String formatValue(double val) {
            if (this == PRECIPITATION) {
                return String.format(Locale.US, "%.1f%s", val, unit);
            }
            return Math.round(val) + unit;
        }
    }

    private static final int POINT_SPACING = 55;
    private static final int DEFAULT_HEIGHT = 270;

    private List<QWeatherService.HourlyForecast> forecasts;
    private Metric currentMetric = Metric.TEMPERATURE;
    private int hoverIndex = -1;
    private float[] cachedXs;
    private float[] cachedYs;

    public WeatherChartPanel(List<QWeatherService.HourlyForecast> forecasts) {
        this.forecasts = forecasts == null ? List.of() : List.copyOf(forecasts);
        setOpaque(false);
        setupMouseListeners();
        updateAccessibleData();
    }

    public void setForecasts(List<QWeatherService.HourlyForecast> forecasts) {
        this.forecasts = forecasts == null ? List.of() : List.copyOf(forecasts);
        this.hoverIndex = -1;
        cachedXs = null;
        cachedYs = null;
        updateAccessibleData();
        revalidate();
        repaint();
    }

    public void setMetric(Metric metric) {
        if (metric != null && metric != this.currentMetric) {
            this.currentMetric = metric;
            updateAccessibleData();
            repaint();
        }
    }

    private void updateAccessibleData() {
        String name = UiDefaults.text("Weather.hourly.name", currentMetric.label);
        StringBuilder description = new StringBuilder();
        for (QWeatherService.HourlyForecast forecast : forecasts) {
            String time = forecast.fullTime == null || forecast.fullTime.isBlank()
                    ? forecast.date + " " + forecast.time : forecast.fullTime;
            description.append(UiDefaults.text("Weather.hourly.row", time, forecast.text,
                    currentMetric.label, currentMetric.formatValue(currentMetric.extractValue(forecast)),
                    forecast.temp, forecast.humidity, forecast.precipitation, forecast.windDirection,
                    forecast.windSpeed, forecast.pressure)).append("\n");
        }
        UiDefaults.textAlternative(this, name, description.isEmpty()
                ? UiDefaults.text("Weather.empty") : description.toString());
    }

    public Metric getMetric() {
        return currentMetric;
    }

    @Override
    public Dimension getPreferredSize() {
        int n = (forecasts != null) ? forecasts.size() : 0;
        int minW = 720;
        int padL = 55, padR = 35;
        int w = Math.max(minW, padL + padR + Math.max(1, n - 1) * POINT_SPACING);
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
        if (cachedXs == null || forecasts == null || forecasts.isEmpty()) return;
        int n = forecasts.size();
        int closest = -1;
        float minDist = Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            float dist = Math.abs(cachedXs[i] - mouseX);
            if (dist < minDist && dist <= POINT_SPACING * 0.85f) {
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
        if (forecasts == null || forecasts.isEmpty()) {
            g.setColor(getForeground());
            g.drawString(UiDefaults.text("Weather.empty"), 30, getHeight() / 2);
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,    RenderingHints.VALUE_STROKE_PURE);

        int w = Math.max(getWidth(), getPreferredSize().width);
        int h = getHeight();

        int padL = 55, padR = 35;
        int padTop = 45;   // space for value labels / max badge
        int padBot = 68;   // space for emoji + time + date separators

        int chartW = w - padL - padR;
        int chartH = h - padTop - padBot;

        int n = forecasts.size();

        // Theme colors
        boolean isDark = isDarkTheme();
        Color primary = currentMetric.primaryColor;
        Color textColor = UiDefaults.foreground();
        Color subTextColor = UiDefaults.foreground();
        Color gridColor = isDark ? new Color(255, 255, 255, 25) : new Color(180, 200, 230, 95);
        Color dateDividerColor = isDark ? new Color(255, 255, 255, 35) : new Color(160, 185, 220, 110);
        Color haloColor = isDark ? new Color(25, 30, 42, 220) : new Color(255, 255, 255, 230);

        // Compute metric range
        double minV = Double.MAX_VALUE, maxV = -Double.MAX_VALUE;
        int minIdx = 0, maxIdx = 0;
        for (int i = 0; i < n; i++) {
            double v = currentMetric.extractValue(forecasts.get(i));
            if (v < minV) { minV = v; minIdx = i; }
            if (v > maxV) { maxV = v; maxIdx = i; }
        }
        if (Math.abs(maxV - minV) < 0.001) {
            minV -= 2.0; maxV += 2.0;
        }

        // Add 15% headroom above and below so curves don't clip at top/bottom
        double rangePadding = Math.max(1.0, (maxV - minV) * 0.16);
        double dispMin = minV - rangePadding;
        double dispMax = maxV + rangePadding;
        double totalRange = dispMax - dispMin;

        // Map values to screen coordinates
        float[] xs = new float[n];
        float[] ys = new float[n];
        for (int i = 0; i < n; i++) {
            xs[i] = padL + (i * (float) chartW) / Math.max(1, n - 1);
            double val = currentMetric.extractValue(forecasts.get(i));
            ys[i] = (float) (padTop + chartH - ((val - dispMin) / totalRange) * chartH);
        }
        this.cachedXs = xs;
        this.cachedYs = ys;

        // --- 1. Horizontal grid lines & Y-axis scale labels ---
        Font scaleFont = UiDefaults.font(Font.PLAIN, 10);
        g2.setFont(scaleFont);
        g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                1f, new float[]{4f, 4f}, 0f));
        int gridLines = 4;
        for (int gl = 0; gl <= gridLines; gl++) {
            int gy = padTop + (gl * chartH) / gridLines;
            g2.setColor(gridColor);
            g2.drawLine(padL, gy, w - padR, gy);

            double gridVal = dispMax - (gl * totalRange) / gridLines;
            String scaleStr = currentMetric.formatValue(gridVal);
            FontMetrics sfm = g2.getFontMetrics();
            int sw = sfm.stringWidth(scaleStr);
            g2.setColor(subTextColor);
            g2.drawString(scaleStr, padL - sw - 8, gy + sfm.getAscent() / 2 - 2);
        }

        // --- 2. Date dividers (for multi-day forecasts like 72h, 168h) ---
        Font dateFont = UiDefaults.font(Font.BOLD, 10);
        g2.setFont(dateFont);
        for (int i = 1; i < n; i++) {
            QWeatherService.HourlyForecast curr = forecasts.get(i);
            QWeatherService.HourlyForecast prev = forecasts.get(i - 1);
            boolean dateChanged = !curr.date.isEmpty() && !curr.date.equals(prev.date);
            boolean isMidnight = curr.time.equals("00:00") || curr.time.equals("24:00");
            if (dateChanged || isMidnight) {
                int dx = Math.round(xs[i]);
                g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                        1f, new float[]{3f, 3f}, 0f));
                g2.setColor(dateDividerColor);
                g2.drawLine(dx, padTop - 12, dx, padTop + chartH + 30);

                // Date badge
                String dateBadge = curr.date.isEmpty() ? curr.time : curr.date;
                FontMetrics dfm = g2.getFontMetrics();
                int bw = dfm.stringWidth(dateBadge) + 10;
                int bh = dfm.getHeight() + 2;
                g2.setColor(isDark ? new Color(45, 55, 75, 200) : new Color(220, 230, 245, 220));
                g2.fillRoundRect(dx - bw / 2, padTop - 24, bw, bh, 6, 6);
                g2.setColor(primary);
                g2.drawString(dateBadge, dx - dfm.stringWidth(dateBadge) / 2, padTop - 24 + dfm.getAscent() + 1);
            }
        }

        // --- 3. Gradient area fill under bezier curve ---
        GeneralPath fillPath = buildSmoothPath(xs, ys, n);
        fillPath.lineTo(xs[n - 1], padTop + chartH);
        fillPath.lineTo(xs[0],     padTop + chartH);
        fillPath.closePath();

        Color fillTop = new Color(primary.getRed(), primary.getGreen(), primary.getBlue(), isDark ? 90 : 75);
        Color fillBot = new Color(primary.getRed(), primary.getGreen(), primary.getBlue(), 6);
        g2.setPaint(new GradientPaint(0, padTop, fillTop, 0, padTop + chartH, fillBot));
        g2.fill(fillPath);

        // --- 4. Bezier curve stroke ---
        g2.setColor(primary);
        g2.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(buildSmoothPath(xs, ys, n));

        // --- 5. Data Points & Labels ---
        Font valFont = UiDefaults.font(Font.BOLD, 11);
        Font timeFont = UiDefaults.font(Font.PLAIN, 10);
        Font iconFont = new Font("Segoe UI Emoji", Font.PLAIN, 13);

        // Time step thinning for large n
        int timeStep = 1;
        if (n > 72) {
            timeStep = 6;
        } else if (n > 24) {
            timeStep = 3;
        }

        for (int i = 0; i < n; i++) {
            int ix = Math.round(xs[i]);
            int iy = Math.round(ys[i]);
            QWeatherService.HourlyForecast f = forecasts.get(i);

            // Node circles
            boolean isMax = (i == maxIdx && Math.abs(maxV - minV) > 0.1);
            boolean isMin = (i == minIdx && Math.abs(maxV - minV) > 0.1);

            g2.setColor(isMax ? currentMetric.accentColor : primary);
            g2.fillOval(ix - 4, iy - 4, 8, 8);
            g2.setColor(Color.WHITE);
            g2.fillOval(ix - 2, iy - 2, 4, 4);

            // Value label above dot (if density allows or is extremum)
            boolean showVal = (n <= 36) || (i % timeStep == 0) || isMax || isMin;
            if (showVal) {
                g2.setFont(valFont);
                String valStr = currentMetric.formatValue(currentMetric.extractValue(f));
                int tw = g2.getFontMetrics().stringWidth(valStr);
                drawHaloString(g2, valStr, ix - tw / 2, iy - 9, isMax ? currentMetric.accentColor : textColor, haloColor);
            }

            // Weather emoji row (below chart baseline)
            boolean showEmoji = (n <= 36) || (i % timeStep == 0);
            if (showEmoji) {
                String emoji = getWeatherEmoji(f.text);
                g2.setFont(iconFont);
                int ew = g2.getFontMetrics().stringWidth(emoji);
                g2.setColor(textColor);
                g2.drawString(emoji, ix - ew / 2, padTop + chartH + 20);
            }

            // Time label below emoji
            boolean showTime = (n <= 24) || (i % timeStep == 0);
            if (showTime) {
                g2.setFont(timeFont);
                int lw = g2.getFontMetrics().stringWidth(f.time);
                g2.setColor(subTextColor);
                g2.drawString(f.time, ix - lw / 2, padTop + chartH + 38);
            }
        }

        // --- 6. Hover Crosshair & Floating Tooltip Card ---
        if (hoverIndex >= 0 && hoverIndex < n) {
            drawHoverDetails(g2, hoverIndex, xs, ys, padTop, chartH, w, h, isDark, primary);
        }

        g2.dispose();
    }

    private void drawHoverDetails(Graphics2D g2, int idx, float[] xs, float[] ys,
            int padTop, int chartH, int totalW, int totalH, boolean isDark, Color primary) {
        int ix = Math.round(xs[idx]);
        int iy = Math.round(ys[idx]);
        QWeatherService.HourlyForecast f = forecasts.get(idx);

        // Vertical guide line
        g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                1f, new float[]{4f, 3f}, 0f));
        g2.setColor(new Color(primary.getRed(), primary.getGreen(), primary.getBlue(), 160));
        g2.drawLine(ix, padTop - 10, ix, padTop + chartH + 42);

        // Pulsing glow circle at hovered point
        g2.setColor(new Color(primary.getRed(), primary.getGreen(), primary.getBlue(), 55));
        g2.fillOval(ix - 9, iy - 9, 18, 18);
        g2.setColor(primary);
        g2.fillOval(ix - 5, iy - 5, 10, 10);
        g2.setColor(Color.WHITE);
        g2.fillOval(ix - 2, iy - 2, 4, 4);

        // Tooltip Card dimensions
        int cardW = 195;
        int cardH = 92;
        int cardX = ix - cardW / 2;
        if (cardX < 12) cardX = 12;
        if (cardX + cardW > totalW - 12) cardX = totalW - cardW - 12;

        int cardY = iy - cardH - 16;
        if (cardY < 8) {
            cardY = iy + 16; // place below dot if too close to top
        }

        // Tooltip Background & Border
        Color cardBg = UiDefaults.background();
        Color cardBorder = UiDefaults.border();
        Color cardTitle = UiDefaults.foreground();
        Color cardBody = UiDefaults.foreground();

        g2.setColor(cardBg);
        g2.fill(new RoundRectangle2D.Float(cardX, cardY, cardW, cardH, 10, 10));
        g2.setColor(cardBorder);
        g2.setStroke(new BasicStroke(1f));
        g2.draw(new RoundRectangle2D.Float(cardX, cardY, cardW, cardH, 10, 10));

        // Tooltip Content
        int textX = cardX + 12;
        int textY = cardY + 20;

        // Line 1: Time + Emoji + Weather
        g2.setFont(UiDefaults.font(Font.BOLD, 12));
        g2.setColor(cardTitle);
        String headerTime = f.fullTime != null && !f.fullTime.isEmpty() ? f.fullTime : f.time;
        String titleStr = headerTime + " · " + getWeatherEmoji(f.text) + " " + f.text;
        g2.drawString(titleStr, textX, textY);

        // Line 2: Active Metric highlighted
        textY += 19;
        g2.setFont(UiDefaults.font(Font.BOLD, 12));
        g2.setColor(primary);
        double activeVal = currentMetric.extractValue(f);
        String mainValStr = currentMetric.label + ": " + currentMetric.formatValue(activeVal);
        if (currentMetric != Metric.TEMPERATURE) {
            mainValStr += "  (气温 " + f.temp + "°C)";
        }
        g2.drawString(mainValStr, textX, textY);

        // Line 3: Humidity & Precipitation
        textY += 18;
        g2.setFont(UiDefaults.font(Font.PLAIN, 10));
        g2.setColor(cardBody);
        String sub1 = "湿度: " + f.humidity + "%  |  降水: " + String.format(Locale.US, "%.1fmm", f.precipitation);
        g2.drawString(sub1, textX, textY);

        // Line 4: Wind & Pressure
        textY += 17;
        String windInfo = (f.windDirection.isEmpty() ? "" : f.windDirection + " ")
                + (f.windScale.isEmpty() ? "" : f.windScale) + " (" + f.windSpeed + "km/h)";
        String sub2 = "风速: " + windInfo + "  |  " + f.pressure + "hPa";
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

    /** Catmull-Rom style smooth curve interpolation */
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

    public static String getWeatherEmoji(String text) {
        if (text == null) return "🌡";
        if (text.contains("晴"))    return "☀";
        if (text.contains("多云"))  return "⛅";
        if (text.contains("阴"))    return "☁";
        if (text.contains("雷"))    return "⛈";
        if (text.contains("雨"))    return "🌧";
        if (text.contains("雪"))    return "❄";
        if (text.contains("雾"))    return "🌫";
        if (text.contains("风"))    return "💨";
        return "🌡";
    }
}
