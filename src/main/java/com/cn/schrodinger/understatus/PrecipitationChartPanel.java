package com.cn.schrodinger.understatus;

import com.cn.schrodinger.understatus.weather.MinutelyPrecipitation;
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
import java.awt.geom.RoundRectangle2D;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import javax.swing.JPanel;

/**
 * Modern interactive precipitation chart panel.
 * Features gradient bars, intensity reference lines,
 * mouse hover crosshair & tooltip card, and dark/light theme adaptation.
 */
final class PrecipitationChartPanel extends JPanel {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private List<MinutelyPrecipitation.Point> points = List.of();
    private int hoverIndex = -1;
    private float[] cachedXs;
    private float cachedBarW = 10f;

    PrecipitationChartPanel() {
        setPreferredSize(new Dimension(760, 240));
        setOpaque(false);
        setupMouseListeners();
    }

    void setPoints(List<MinutelyPrecipitation.Point> points) {
        this.points = (points != null) ? List.copyOf(points) : List.of();
        this.hoverIndex = -1;
        revalidate();
        repaint();
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
        if (points.isEmpty() || cachedXs == null) return;
        int n = points.size();
        int closest = -1;
        float minDist = Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            float dist = Math.abs(cachedXs[i] - mouseX);
            if (dist < minDist && dist <= cachedBarW * 1.5f) {
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
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        boolean isDark = isDarkTheme();
        Color textColor = isDark ? new Color(220, 226, 235) : new Color(40, 50, 70);
        Color subTextColor = isDark ? new Color(150, 165, 185) : new Color(110, 125, 145);
        Color gridColor = isDark ? new Color(255, 255, 255, 25) : new Color(180, 200, 230, 90);

        if (points.isEmpty()) {
            g.setColor(textColor);
            g.drawString("暂无分钟级降水数据", 30, h / 2);
            g.dispose();
            return;
        }

        int padL = 50, padR = 25;
        int padTop = 35;
        int baseline = h - 38;
        int chartH = baseline - padTop;
        int n = points.size();

        double max = points.stream().mapToDouble(MinutelyPrecipitation.Point::precipitationMm)
                .max().orElse(0.1);
        max = Math.max(max, 0.5); // Minimum headroom for scale

        float slotW = (w - padL - padR) / (float) n;
        float barW = Math.max(4f, slotW - 3f);
        this.cachedBarW = barW;
        float[] xs = new float[n];

        // --- 1. Horizontal Reference Lines for Rain Intensity ---
        Font scaleFont = new Font("SansSerif", Font.PLAIN, 10);
        g.setFont(scaleFont);
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                1f, new float[]{3f, 4f}, 0f));

        double[] refLevels = new double[]{0.25, 1.0, 2.5};
        String[] refLabels = new String[]{"小雨 (0.25)", "中雨 (1.0)", "大雨 (2.5)"};
        for (int r = 0; r < refLevels.length; r++) {
            if (refLevels[r] <= max * 1.1) {
                int ry = (int) Math.round(baseline - chartH * (refLevels[r] / max));
                if (ry >= padTop && ry <= baseline) {
                    g.setColor(gridColor);
                    g.drawLine(padL, ry, w - padR, ry);
                    g.setColor(subTextColor);
                    g.drawString(refLabels[r], padL - g.getFontMetrics().stringWidth(refLabels[r]) - 6, ry + 4);
                }
            }
        }

        // Baseline
        g.setColor(gridColor);
        g.drawLine(padL, baseline, w - padR, baseline);

        // --- 2. Draw Bars ---
        for (int i = 0; i < n; i++) {
            MinutelyPrecipitation.Point point = points.get(i);
            float cx = padL + i * slotW + slotW / 2f;
            xs[i] = cx;
            float bx = cx - barW / 2f;

            int barH = (int) Math.round(chartH * (point.precipitationMm() / max));
            if (point.precipitationMm() > 0 && barH < 4) barH = 4; // ensure visible dot if raining
            int by = baseline - barH;

            boolean isSnow = "snow".equalsIgnoreCase(point.type());
            boolean isHovered = (i == hoverIndex);

            // Bar Gradient Colors
            Color topC = isSnow
                    ? (isHovered ? new Color(130, 220, 255) : new Color(80, 185, 240))
                    : (isHovered ? new Color(85, 185, 255) : new Color(55, 140, 245));
            Color botC = isSnow
                    ? (isHovered ? new Color(90, 190, 240) : new Color(50, 150, 215))
                    : (isHovered ? new Color(40, 130, 240) : new Color(30, 100, 210));

            g.setPaint(new GradientPaint(bx, by, topC, bx, baseline, botC));
            g.fill(new RoundRectangle2D.Float(bx, by, barW, barH, 4, 4));

            // Time label below baseline
            if (i % 4 == 0 || i == n - 1) {
                g.setColor(subTextColor);
                String timeStr = point.time().format(TIME_FMT);
                FontMetrics tfm = g.getFontMetrics();
                g.drawString(timeStr, cx - tfm.stringWidth(timeStr) / 2f, baseline + 18);
            }
        }
        this.cachedXs = xs;

        // --- 3. Hover Guide Line & Tooltip ---
        if (hoverIndex >= 0 && hoverIndex < n) {
            drawHoverTooltip(g, hoverIndex, xs, padTop, baseline, w, h, isDark);
        }

        g.dispose();
    }

    private void drawHoverTooltip(Graphics2D g, int idx, float[] xs, int padTop,
            int baseline, int totalW, int totalH, boolean isDark) {
        MinutelyPrecipitation.Point point = points.get(idx);
        int ix = Math.round(xs[idx]);

        // Vertical Guide Line
        g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                1f, new float[]{3f, 3f}, 0f));
        g.setColor(new Color(64, 156, 255, 160));
        g.drawLine(ix, padTop - 5, ix, baseline);

        // Tooltip Dimensions
        int cardW = 160;
        int cardH = 65;
        int cardX = ix - cardW / 2;
        if (cardX < 12) cardX = 12;
        if (cardX + cardW > totalW - 12) cardX = totalW - cardW - 12;
        int cardY = padTop + 10;

        Color cardBg = isDark ? new Color(24, 30, 42, 235) : new Color(255, 255, 255, 240);
        Color cardBorder = isDark ? new Color(70, 85, 110, 160) : new Color(190, 205, 225, 200);
        Color cardTitle = isDark ? new Color(240, 245, 255) : new Color(20, 30, 50);
        Color cardBody = isDark ? new Color(175, 190, 210) : new Color(75, 90, 110);

        g.setColor(cardBg);
        g.fill(new RoundRectangle2D.Float(cardX, cardY, cardW, cardH, 8, 8));
        g.setColor(cardBorder);
        g.setStroke(new BasicStroke(1f));
        g.draw(new RoundRectangle2D.Float(cardX, cardY, cardW, cardH, 8, 8));

        int textX = cardX + 10;
        int textY = cardY + 18;

        // Line 1: Time
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.setColor(cardTitle);
        g.drawString(point.time().format(TIME_FMT) + " 降水详情", textX, textY);

        // Line 2: Precipitation
        textY += 18;
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.setColor(new Color(24, 144, 255));
        String valStr = String.format(Locale.US, "强度: %.2f mm/h", point.precipitationMm());
        g.drawString(valStr, textX, textY);

        // Line 3: Description & Type
        textY += 16;
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g.setColor(cardBody);
        String intensityDesc = getIntensityDesc(point.precipitationMm(), point.type());
        g.drawString(intensityDesc, textX, textY);
    }

    private String getIntensityDesc(double mm, String type) {
        String kind = "snow".equalsIgnoreCase(type) ? "雪" : "雨";
        if (mm <= 0.01) return "目前无降" + kind;
        if (mm < 0.25)  return "微量降" + kind;
        if (mm < 1.0)   return "小" + kind;
        if (mm < 2.5)   return "中" + kind;
        if (mm < 5.0)   return "大" + kind;
        return "暴" + kind;
    }

    private boolean isDarkTheme() {
        Color bg = getBackground();
        if (bg == null) bg = javax.swing.UIManager.getColor("Panel.background");
        if (bg == null) return false;
        double lum = 0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue();
        return lum < 128;
    }
}
