package com.cn.schrodinger.understatus;

import com.cn.schrodinger.understatus.toolbox.core.ToolTask;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.UIManager;

/**
 * Text codecs (Base64 / URL) and intelligent Timestamp (s / ms) converter panel.
 * Features auto-detection of 10-digit/13-digit timestamps, relative time description,
 * one-click 'Now' time, and bidirectional conversion with inline feedback.
 *
 * @author peter/antigravity
 */
public class UtilsTabPanel extends JPanel {

    private final ToolTask tasks = new ToolTask(this);
    private JTextField codecInputField;
    private JTextField codecOutputField;
    private JLabel codecStatusLabel;

    private JTextField timestampField;
    private JTextField dateTimeField;
    private JLabel timeRelativeLabel;
    private JLabel timeStatusLabel;

    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final DateTimeFormatter millisFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public UtilsTabPanel() {
        initComponents();
        tasks.watch(codecInputField);
    }

    private void initComponents() {
        setLayout(new GridLayout(2, 1, 8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Subpanel 1: Codec
        JPanel codecPanel = new JPanel(new GridBagLayout());
        codecPanel.setBorder(BorderFactory.createTitledBorder("编解码 (Base64 / URL Codec)"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(3, 4, 3, 4);

        gbc.gridx = 0; gbc.gridy = 0;
        codecPanel.add(new JLabel("输入:"), gbc);

        codecInputField = new JTextField();
        gbc.gridx = 1; gbc.weightx = 1.0;
        codecPanel.add(codecInputField, gbc);

        JButton pasteInputBtn = new JButton("📋 粘贴");
        pasteInputBtn.addActionListener(e -> {
            String clip = CommonUtils.getClipboardText();
            if (clip != null && !clip.isBlank()) {
                codecInputField.setText(clip.trim());
            }
        });
        gbc.gridx = 2; gbc.weightx = 0.0;
        codecPanel.add(pasteInputBtn, gbc);

        // Buttons row
        JPanel codecBtnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JButton b64EncBtn = new JButton("B64 编码");
        b64EncBtn.addActionListener(e -> triggerCodec(1));
        JButton b64DecBtn = new JButton("B64 解码");
        b64DecBtn.addActionListener(e -> triggerCodec(2));
        JButton urlEncBtn = new JButton("URL 编码");
        urlEncBtn.addActionListener(e -> triggerCodec(3));
        JButton urlDecBtn = new JButton("URL 解码");
        urlDecBtn.addActionListener(e -> triggerCodec(4));
        JButton swapBtn = new JButton("🔄 交换上下");
        swapBtn.addActionListener(e -> {
            String out = codecOutputField.getText();
            if (!out.isEmpty()) {
                codecInputField.setText(out);
                codecOutputField.setText("");
            }
        });

        codecBtnRow.add(b64EncBtn);
        codecBtnRow.add(b64DecBtn);
        codecBtnRow.add(urlEncBtn);
        codecBtnRow.add(urlDecBtn);
        codecBtnRow.add(swapBtn);

        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 3;
        codecPanel.add(codecBtnRow, gbc);

        // Output row
        gbc.gridwidth = 1;
        gbc.gridy = 2; gbc.gridx = 0;
        codecPanel.add(new JLabel("输出:"), gbc);

        codecOutputField = new JTextField();
        codecOutputField.setEditable(false);
        gbc.gridx = 1; gbc.weightx = 1.0;
        codecPanel.add(codecOutputField, gbc);

        JButton copyOutputBtn = new JButton("复制");
        copyOutputBtn.addActionListener(e -> CommonUtils.copyToClipboard(codecOutputField.getText()));
        gbc.gridx = 2; gbc.weightx = 0.0;
        codecPanel.add(copyOutputBtn, gbc);

        codecStatusLabel = new JLabel(" ");
        codecStatusLabel.setFont(codecStatusLabel.getFont().deriveFont(11f));
        codecStatusLabel.setForeground(new Color(220, 50, 50));
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 3;
        codecPanel.add(codecStatusLabel, gbc);

        add(codecPanel);

        // Subpanel 2: Intelligent Timestamp converter
        JPanel timePanel = new JPanel(new GridBagLayout());
        timePanel.setBorder(BorderFactory.createTitledBorder("时间戳转换 (Unix Timestamp · 智能秒/毫秒自适应)"));
        GridBagConstraints gbc2 = new GridBagConstraints();
        gbc2.fill = GridBagConstraints.HORIZONTAL;
        gbc2.insets = new Insets(3, 4, 3, 4);

        // Row 0: Timestamp input & convert to Date
        gbc2.gridx = 0; gbc2.gridy = 0;
        timePanel.add(new JLabel("时间戳:"), gbc2);

        timestampField = new JTextField(String.valueOf(Instant.now().toEpochMilli()));
        gbc2.gridx = 1; gbc2.weightx = 1.0;
        timePanel.add(timestampField, gbc2);

        JPanel tsBtnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        JButton nowBtn = new JButton("⏱️ 现在");
        nowBtn.addActionListener(e -> fillCurrentTime());
        JButton toDateBtn = new JButton("转为日期 ➔");
        toDateBtn.addActionListener(e -> triggerTimestampToDate());

        tsBtnRow.add(nowBtn);
        tsBtnRow.add(toDateBtn);
        gbc2.gridx = 2; gbc2.weightx = 0.0;
        timePanel.add(tsBtnRow, gbc2);

        // Row 1: Datetime input & convert to Timestamp
        gbc2.gridx = 0; gbc2.gridy = 1;
        timePanel.add(new JLabel("日期时间:"), gbc2);

        dateTimeField = new JTextField(LocalDateTime.now().format(dateTimeFormatter));
        gbc2.gridx = 1; gbc2.weightx = 1.0;
        timePanel.add(dateTimeField, gbc2);

        JButton toTsBtn = new JButton("➔ 转时间戳");
        toTsBtn.addActionListener(e -> triggerDateToTimestamp());
        gbc2.gridx = 2; gbc2.weightx = 0.0;
        timePanel.add(toTsBtn, gbc2);

        // Row 2: Relative time & quick result feedback
        timeRelativeLabel = new JLabel("相对时间: 刚刚 (当前时间)");
        timeRelativeLabel.setFont(timeRelativeLabel.getFont().deriveFont(11f));
        timeRelativeLabel.setForeground(new Color(0, 120, 215));
        gbc2.gridx = 0; gbc2.gridy = 2; gbc2.gridwidth = 3;
        timePanel.add(timeRelativeLabel, gbc2);

        timeStatusLabel = new JLabel(" ");
        timeStatusLabel.setFont(timeStatusLabel.getFont().deriveFont(11f));
        gbc2.gridx = 0; gbc2.gridy = 3; gbc2.gridwidth = 3;
        timePanel.add(timeStatusLabel, gbc2);

        add(timePanel);
    }

    private void fillCurrentTime() {
        long nowMillis = System.currentTimeMillis();
        timestampField.setText(String.valueOf(nowMillis));
        LocalDateTime now = LocalDateTime.now();
        dateTimeField.setText(now.format(dateTimeFormatter));
        timeRelativeLabel.setText("相对时间: 刚刚 (秒: " + (nowMillis / 1000) + " | 毫秒: " + nowMillis + ")");
        timeStatusLabel.setText("已更新为当前系统时间");
        timeStatusLabel.setForeground(new Color(40, 160, 80));
    }

    private void triggerCodec(int mode) {
        tasks.transform(codecInputField, codecOutputField, codecStatusLabel, input -> switch (mode) {
            case 1 -> Base64.getEncoder().encodeToString(input.getBytes(StandardCharsets.UTF_8));
            case 2 -> new String(Base64.getDecoder().decode(input.trim()), StandardCharsets.UTF_8);
            case 3 -> URLEncoder.encode(input, StandardCharsets.UTF_8);
            case 4 -> URLDecoder.decode(input, StandardCharsets.UTF_8);
            default -> throw new IllegalArgumentException("Unknown codec");
        });
    }

    private void triggerTimestampToDate() {
        timeStatusLabel.setText(" ");
        String text = timestampField.getText().trim();
        if (text.isEmpty()) {
            timeStatusLabel.setText("请输入时间戳");
            timeStatusLabel.setForeground(new Color(220, 50, 50));
            return;
        }
        try {
            long val = Long.parseLong(text);
            boolean isMillis = (text.length() >= 12) || (val > 10000000000L);
            long millis = isMillis ? val : val * 1000L;
            long secs = isMillis ? val / 1000L : val;

            Instant instant = Instant.ofEpochMilli(millis);
            LocalDateTime dt = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());

            if (isMillis) {
                dateTimeField.setText(dt.format(millisFormatter));
            } else {
                dateTimeField.setText(dt.format(dateTimeFormatter));
            }

            String rel = calculateRelativeTime(instant);
            timeRelativeLabel.setText(String.format("相对时间: %s (自适应识别: %s | 秒: %d | 毫秒: %d)",
                    rel, isMillis ? "13位毫秒" : "10位秒级", secs, millis));
            timeStatusLabel.setText("转换成功");
            timeStatusLabel.setForeground(new Color(40, 160, 80));
        } catch (Exception ex) {
            timeStatusLabel.setText("时间戳无效: 请输入合法的 10 位(秒) 或 13 位(毫秒) 数字");
            timeStatusLabel.setForeground(new Color(220, 50, 50));
        }
    }

    private void triggerDateToTimestamp() {
        timeStatusLabel.setText(" ");
        String text = dateTimeField.getText().trim();
        if (text.isEmpty()) {
            timeStatusLabel.setText("请输入日期时间");
            timeStatusLabel.setForeground(new Color(220, 50, 50));
            return;
        }
        try {
            LocalDateTime dt;
            if (text.contains(".")) {
                dt = LocalDateTime.parse(text, millisFormatter);
            } else {
                dt = LocalDateTime.parse(text, dateTimeFormatter);
            }
            long millis = dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long secs = millis / 1000L;
            timestampField.setText(String.valueOf(millis));
            Instant instant = Instant.ofEpochMilli(millis);
            String rel = calculateRelativeTime(instant);
            timeRelativeLabel.setText(String.format("相对时间: %s (秒: %d | 毫秒: %d)", rel, secs, millis));
            timeStatusLabel.setText("已转换为毫秒与秒级时间戳 (当前填入毫秒)");
            timeStatusLabel.setForeground(new Color(40, 160, 80));
        } catch (Exception ex) {
            timeStatusLabel.setText("日期格式需为 yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd HH:mm:ss.SSS");
            timeStatusLabel.setForeground(new Color(220, 50, 50));
        }
    }

    private String calculateRelativeTime(Instant target) {
        Instant now = Instant.now();
        Duration diff = Duration.between(now, target);
        long sec = diff.getSeconds();
        if (Math.abs(sec) < 5) return "刚刚";
        if (sec > 0) {
            if (sec < 60) return sec + " 秒后";
            if (sec < 3600) return (sec / 60) + " 分钟后";
            if (sec < 86400) return (sec / 3600) + " 小时后";
            return (sec / 86400) + " 天后";
        } else {
            long absSec = -sec;
            if (absSec < 60) return absSec + " 秒前";
            if (absSec < 3600) return (absSec / 60) + " 分钟前";
            if (absSec < 86400) return (absSec / 3600) + " 小时前";
            return (absSec / 86400) + " 天前";
        }
    }
}
