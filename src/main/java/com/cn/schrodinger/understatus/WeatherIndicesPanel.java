package com.cn.schrodinger.understatus;

import com.cn.schrodinger.understatus.weather.WeatherIndex;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JToggleButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.UIManager;

/**
 * Modern visual card panel for weather life indices (生活气象指数).
 * Features categorized filter chips, status badges, visual icon headers,
 * and adaptive dark/light card themes.
 */
public class WeatherIndicesPanel extends JPanel {

    private List<WeatherIndex> allIndices = List.of();
    private String selectedCategory = UiDefaults.text("Weather.indices.all");
    private final JPanel cardsContainer = new JPanel();
    private final List<JToggleButton> filterButtons = new ArrayList<>();

    public WeatherIndicesPanel() {
        setLayout(new BorderLayout(0, 8));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // Top category filter bar
        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        filterBar.setOpaque(false);
        String[] categories = new String[]{UiDefaults.text("Weather.indices.all"), UiDefaults.text("Weather.indices.health"), UiDefaults.text("Weather.indices.travel"), UiDefaults.text("Weather.indices.life")};
        for (String cat : categories) {
            JToggleButton btn = new JToggleButton(cat);
            btn.setFont(UiDefaults.font(Font.PLAIN, 12));
            UiDefaults.describe(btn, cat, UiDefaults.text("Weather.indices.filterHint", cat));
            btn.setMargin(new Insets(3, 12, 3, 12));
            updateButtonAppearance(btn, cat.equals(selectedCategory));
            btn.addActionListener(e -> {
                selectedCategory = cat;
                for (JToggleButton b : filterButtons) {
                    updateButtonAppearance(b, b.getText().equals(selectedCategory));
                }
                renderCards();
            });
            filterButtons.add(btn);
            filterBar.add(btn);
        }
        add(filterBar, BorderLayout.NORTH);

        // Center scrollable cards container
        cardsContainer.setOpaque(false);
        cardsContainer.setLayout(new GridLayout(0, 2, 10, 10)); // 2 columns responsive grid

        JScrollPane scrollPane = new JScrollPane(cardsContainer);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);
        renderCards();
    }

    public void setIndices(List<WeatherIndex> indices) {
        this.allIndices = (indices != null) ? List.copyOf(indices) : List.of();
        renderCards();
    }

    private void renderCards() {
        cardsContainer.removeAll();
        StringBuilder accessibleData = new StringBuilder(UiDefaults.text("Weather.indices.filterHint", selectedCategory));
        if (allIndices.isEmpty()) {
            UiDefaults.textAlternative(this, UiDefaults.text("Weather.indices.name"), UiDefaults.text("Weather.indices.empty"));
            JLabel emptyLabel = new JLabel(UiDefaults.text("Weather.indices.empty"));
            emptyLabel.setHorizontalAlignment(JLabel.CENTER);
            emptyLabel.setFont(UiDefaults.font(Font.PLAIN, 13));
            emptyLabel.setForeground(UiDefaults.foreground());
            cardsContainer.setLayout(new BorderLayout());
            cardsContainer.add(emptyLabel, BorderLayout.CENTER);
            cardsContainer.revalidate();
            cardsContainer.repaint();
            return;
        }

        cardsContainer.setLayout(new GridLayout(0, 2, 10, 10));
        boolean isDark = isDarkTheme();

        for (WeatherIndex item : allIndices) {
            String group = resolveGroup(item.type());
            if (!UiDefaults.text("Weather.indices.all").equals(selectedCategory) && !group.equals(selectedCategory)) {
                continue;
            }
            accessibleData.append("\n").append(UiDefaults.text("Weather.indices.row",
                    item.date(), item.name(), item.level(), item.category(), item.description()));
            cardsContainer.add(createIndexCard(item, isDark));
        }

        if (cardsContainer.getComponentCount() == 0) accessibleData.append("\n").append(UiDefaults.text("Weather.indices.empty"));
        UiDefaults.textAlternative(this, UiDefaults.text("Weather.indices.name"), accessibleData.toString());
        cardsContainer.revalidate();
        cardsContainer.repaint();
    }

    private JPanel createIndexCard(WeatherIndex index, boolean isDark) {
        JPanel card = new JPanel(new BorderLayout(8, 6));
        Color bg = UiDefaults.background();
        Color border = UiDefaults.border();
        card.setBackground(bg);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border, 1, true),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));

        // Top Header: Icon + Name + Level Badge
        JPanel topRow = new JPanel(new BorderLayout(6, 0));
        topRow.setOpaque(false);

        String emoji = getIndexEmoji(index.type());
        JLabel titleLabel = new JLabel(emoji + " " + index.name());
        titleLabel.setFont(UiDefaults.font(Font.BOLD, 13));
        titleLabel.setForeground(UiDefaults.foreground());
        topRow.add(titleLabel, BorderLayout.WEST);

        // Level Badge with semantic color
        String levelCategory = (index.category() != null && !index.category().isEmpty())
                ? index.category() : ("等级 " + index.level());
        JLabel badgeLabel = new JLabel(" " + levelCategory + " ");
        badgeLabel.setOpaque(true);
        badgeLabel.setFont(UiDefaults.font(Font.BOLD, 11));
        badgeLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

        Color badgeBg = getBadgeColor(index.level(), index.category(), isDark);
        Color badgeFg = UiDefaults.onColor(badgeBg);
        badgeLabel.setBackground(badgeBg);
        badgeLabel.setForeground(badgeFg);
        topRow.add(badgeLabel, BorderLayout.EAST);

        card.add(topRow, BorderLayout.NORTH);

        // Center Description (Multi-line text)
        JTextArea descArea = new JTextArea(index.description());
        descArea.setWrapStyleWord(true);
        descArea.setLineWrap(true);
        descArea.setEditable(false);
        descArea.setOpaque(false);
        descArea.setFont(UiDefaults.font(Font.PLAIN, 12));
        descArea.setForeground(UiDefaults.foreground());
        descArea.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        card.add(descArea, BorderLayout.CENTER);

        return card;
    }

    private Color getBadgeColor(String levelStr, String category, boolean isDark) {
        int level = 1;
        try {
            if (levelStr != null && !levelStr.isEmpty()) {
                level = Integer.parseInt(levelStr);
            }
        } catch (NumberFormatException ignored) {}

        if (category != null) {
            if (category.contains("优") || category.contains("适宜") || category.contains("舒适")) {
                return new Color(46, 175, 90);
            }
            if (category.contains("较") || category.contains("良")) {
                return new Color(24, 144, 255);
            }
            if (category.contains("注意") || category.contains("中等")) {
                return new Color(245, 158, 11);
            }
            if (category.contains("不适") || category.contains("极强") || category.contains("差")) {
                return new Color(239, 68, 68);
            }
        }

        return switch (level) {
            case 1 -> new Color(46, 175, 90);
            case 2 -> new Color(24, 144, 255);
            case 3 -> new Color(245, 158, 11);
            case 4 -> new Color(249, 115, 22);
            default -> new Color(239, 68, 68);
        };
    }

    private void updateButtonAppearance(JToggleButton btn, boolean active) {
        btn.setSelected(active);
        btn.setBackground(active ? UiDefaults.color("List.selectionBackground", UiDefaults.background())
                : UiDefaults.color("Button.background", UiDefaults.background()));
        btn.setForeground(active ? UiDefaults.color("List.selectionForeground", UiDefaults.foreground())
                : UiDefaults.color("Button.foreground", UiDefaults.foreground()));
    }

    private static String resolveGroup(int type) {
        return switch (type) {
            case 1, 4, 6, 15 -> UiDefaults.text("Weather.indices.travel");
            case 7, 8, 9, 10, 16 -> UiDefaults.text("Weather.indices.health");
            default -> UiDefaults.text("Weather.indices.life");
        };
    }

    private static String getIndexEmoji(int type) {
        return switch (type) {
            case 1 -> "🏃"; // 运动
            case 2 -> "🚗"; // 洗车
            case 3 -> "👕"; // 穿衣
            case 4 -> "🎣"; // 钓鱼
            case 5 -> "☀️"; // 紫外线
            case 6 -> "✈️"; // 旅游
            case 7 -> "🤧"; // 过敏
            case 8 -> "😊"; // 舒适度
            case 9 -> "💊"; // 感冒
            case 10 -> "🍃"; // 扩散
            case 11 -> "❄️"; // 空调
            case 12 -> "🕶️"; // 太阳镜
            case 13 -> "💄"; // 化妆
            case 14 -> "👔"; // 晾晒
            case 15 -> "🚦"; // 交通
            case 16 -> "🧴"; // 防晒
            default -> "💡";
        };
    }

    private boolean isDarkTheme() {
        Color bg = getBackground();
        if (bg == null) bg = UIManager.getColor("Panel.background");
        if (bg == null) return false;
        double lum = 0.299 * bg.getRed() + 0.587 * bg.getGreen() + 0.114 * bg.getBlue();
        return lum < 128;
    }
}
