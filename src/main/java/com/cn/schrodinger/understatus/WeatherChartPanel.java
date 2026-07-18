package com.cn.schrodinger.understatus;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.CubicCurve2D;
import java.awt.geom.GeneralPath;
import java.util.List;
import javax.swing.JPanel;

/**
 * Premium 24-hour temperature trend chart panel.
 * Bezier smoothed curves, gradient area fill, weather emoji row, bold labels.
 */
public class WeatherChartPanel extends JPanel {

    private static final Color CURVE_COLOR  = new Color(64, 156, 255);
    private static final Color FILL_TOP     = new Color(64, 156, 255, 70);
    private static final Color FILL_BOT     = new Color(64, 156, 255, 5);
    private static final Color DOT_OUTER    = new Color(64, 156, 255);
    private static final Color DOT_INNER    = Color.WHITE;
    private static final Color GRID_COLOR   = new Color(180, 200, 230, 120);
    private static final Color TEMP_COLOR   = new Color(20, 40, 80);
    private static final Color TIME_COLOR   = new Color(100, 120, 160);
    private static final Color ICON_COLOR   = new Color(60, 100, 180);

    private List<QWeatherService.HourlyForecast> forecasts;

    public WeatherChartPanel(List<QWeatherService.HourlyForecast> forecasts) {
        this.forecasts = forecasts;
        setOpaque(false);
    }

    public void setForecasts(List<QWeatherService.HourlyForecast> forecasts) {
        this.forecasts = forecasts;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (forecasts == null || forecasts.isEmpty()) {
            g.drawString("暂无天气数据", 30, getHeight() / 2);
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,    RenderingHints.VALUE_STROKE_PURE);

        int w = getWidth();
        int h = getHeight();

        int padL = 16, padR = 16;
        int padTop = 40;   // space for temp labels above line
        int padBot = 48;   // space for emoji + time below line

        int chartW = w - padL - padR;
        int chartH = h - padTop - padBot;

        int n = forecasts.size();

        // Compute temp range
        int minT = Integer.MAX_VALUE, maxT = Integer.MIN_VALUE;
        for (QWeatherService.HourlyForecast f : forecasts) {
            if (f.temp < minT) minT = f.temp;
            if (f.temp > maxT) maxT = f.temp;
        }
        if (minT == maxT) { minT -= 2; maxT += 2; }
        int range = maxT - minT;

        // Map temps to screen
        float[] xs = new float[n];
        float[] ys = new float[n];
        for (int i = 0; i < n; i++) {
            xs[i] = padL + (i * (float) chartW) / (n - 1);
            ys[i] = padTop + chartH - ((forecasts.get(i).temp - minT) * (float) chartH) / range;
        }

        // --- 1. Horizontal grid lines ---
        g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                1f, new float[]{4f, 4f}, 0f));
        g2.setColor(GRID_COLOR);
        int gridLines = 3;
        for (int gl = 0; gl <= gridLines; gl++) {
            int gy = padTop + (gl * chartH) / gridLines;
            g2.drawLine(padL, gy, w - padR, gy);
        }

        // --- 2. Gradient area fill under bezier curve ---
        GeneralPath fillPath = buildSmoothPath(xs, ys, n);
        fillPath.lineTo(xs[n - 1], padTop + chartH);
        fillPath.lineTo(xs[0],     padTop + chartH);
        fillPath.closePath();
        g2.setPaint(new GradientPaint(0, padTop, FILL_TOP, 0, padTop + chartH, FILL_BOT));
        g2.fill(fillPath);

        // --- 3. Bezier curve line ---
        g2.setColor(CURVE_COLOR);
        g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(buildSmoothPath(xs, ys, n));

        // --- 4. Dots, temperature labels, emoji + time labels ---
        Font tempFont = new Font("SansSerif", Font.BOLD, 11);
        Font timeFont = new Font("SansSerif", Font.PLAIN, 10);
        Font iconFont = new Font("Segoe UI Emoji", Font.PLAIN, 12);

        for (int i = 0; i < n; i++) {
            int ix = Math.round(xs[i]);
            int iy = Math.round(ys[i]);
            QWeatherService.HourlyForecast f = forecasts.get(i);

            // Node outer
            g2.setColor(DOT_OUTER);
            g2.fillOval(ix - 5, iy - 5, 10, 10);
            // Node inner
            g2.setColor(DOT_INNER);
            g2.fillOval(ix - 3, iy - 3, 6, 6);

            // Temperature label above dot
            g2.setFont(tempFont);
            String tempStr = f.temp + "°";
            int tw = g2.getFontMetrics().stringWidth(tempStr);
            // White halo for contrast
            g2.setColor(Color.WHITE);
            g2.drawString(tempStr, ix - tw / 2 - 1, iy - 10 - 1);
            g2.drawString(tempStr, ix - tw / 2 + 1, iy - 10 + 1);
            g2.setColor(TEMP_COLOR);
            g2.drawString(tempStr, ix - tw / 2, iy - 10);

            // Weather emoji row (below chart line)
            String emoji = getWeatherEmoji(f.text);
            g2.setFont(iconFont);
            int ew = g2.getFontMetrics().stringWidth(emoji);
            g2.setColor(ICON_COLOR);
            g2.drawString(emoji, ix - ew / 2, padTop + chartH + 18);

            // Time label below emoji
            g2.setFont(timeFont);
            int lw = g2.getFontMetrics().stringWidth(f.time);
            g2.setColor(TIME_COLOR);
            g2.drawString(f.time, ix - lw / 2, padTop + chartH + 36);
        }

        g2.dispose();
    }

    /** Build a catmull-rom-style smooth path through (xs, ys) */
    private GeneralPath buildSmoothPath(float[] xs, float[] ys, int n) {
        GeneralPath path = new GeneralPath();
        path.moveTo(xs[0], ys[0]);
        for (int i = 0; i < n - 1; i++) {
            float cp1x = xs[i] + (xs[i + 1] - xs[i]) / 3f;
            float cp1y = ys[i];
            float cp2x = xs[i] + 2f * (xs[i + 1] - xs[i]) / 3f;
            float cp2y = ys[i + 1];
            path.curveTo(cp1x, cp1y, cp2x, cp2y, xs[i + 1], ys[i + 1]);
        }
        return path;
    }

    private static String getWeatherEmoji(String text) {
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
