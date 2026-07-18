package com.cn.schrodinger.understatus;

import com.cn.schrodinger.understatus.weather.MinutelyPrecipitation;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.swing.JPanel;

final class PrecipitationChartPanel extends JPanel {
    private List<MinutelyPrecipitation.Point> points = List.of();

    PrecipitationChartPanel() { setPreferredSize(new Dimension(760, 230)); }

    void setPoints(List<MinutelyPrecipitation.Point> points) {
        this.points = List.copyOf(points);
        repaint();
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (points.isEmpty()) {
            g.setColor(getForeground());
            g.drawString("暂无分钟降水数据", 20, 30);
            g.dispose();
            return;
        }
        double max = points.stream().mapToDouble(MinutelyPrecipitation.Point::precipitationMm)
                .max().orElse(1);
        max = Math.max(max, 0.1);
        int baseline = getHeight() - 36;
        int width = Math.max(6, (getWidth() - 32) / points.size());
        for (int i = 0; i < points.size(); i++) {
            MinutelyPrecipitation.Point point = points.get(i);
            int height = (int) Math.round((baseline - 20) * point.precipitationMm() / max);
            g.setColor("snow".equals(point.type()) ? new Color(90, 170, 235)
                    : new Color(55, 125, 220));
            g.fillRoundRect(16 + i * width, baseline - height, Math.max(3, width - 2),
                    height, 4, 4);
            if (i % 3 == 0) {
                g.setColor(getForeground());
                g.drawString(point.time().format(DateTimeFormatter.ofPattern("HH:mm")),
                        12 + i * width, baseline + 18);
            }
        }
        g.dispose();
    }
}
