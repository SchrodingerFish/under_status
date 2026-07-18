package com.cn.schrodinger.understatus;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Text codecs and Epoch seconds timestamp converter panel.
 *
 * @author peter/antigravity
 */
public class UtilsTabPanel extends JPanel {

    private JTextField codecInputField;
    private JTextField codecOutputField;
    private JTextField epochSecondsField;
    private JTextField epochDateTimeField;
    private final DateTimeFormatter epochFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public UtilsTabPanel() {
        initComponents();
    }

    private void initComponents() {
        setLayout(new GridLayout(2, 1, 5, 5));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Subpanel 1: Codec
        JPanel codecPanel = new JPanel(new GridBagLayout());
        codecPanel.setBorder(BorderFactory.createTitledBorder("编解码 (Base64 / URL)"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 4, 4, 4);

        gbc.gridx = 0; gbc.gridy = 0;
        codecPanel.add(new JLabel("输入:"), gbc);
        codecInputField = new JTextField();
        gbc.gridx = 1; gbc.gridwidth = 3;
        codecPanel.add(codecInputField, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = 1;
        
        JButton b64EncBtn = new JButton("B64编码");
        b64EncBtn.addActionListener(e -> triggerCodec(1));
        gbc.gridx = 0; codecPanel.add(b64EncBtn, gbc);

        JButton b64DecBtn = new JButton("B64解码");
        b64DecBtn.addActionListener(e -> triggerCodec(2));
        gbc.gridx = 1; codecPanel.add(b64DecBtn, gbc);

        JButton urlEncBtn = new JButton("URL编码");
        urlEncBtn.addActionListener(e -> triggerCodec(3));
        gbc.gridx = 2; codecPanel.add(urlEncBtn, gbc);

        JButton urlDecBtn = new JButton("URL解码");
        urlDecBtn.addActionListener(e -> triggerCodec(4));
        gbc.gridx = 3; codecPanel.add(urlDecBtn, gbc);

        gbc.gridy = 2; gbc.gridx = 0;
        codecPanel.add(new JLabel("输出:"), gbc);
        codecOutputField = new JTextField();
        codecOutputField.setEditable(false);
        gbc.gridx = 1; gbc.gridwidth = 2;
        codecPanel.add(codecOutputField, gbc);

        JButton copyOutputBtn = new JButton("复制");
        copyOutputBtn.addActionListener(e -> CommonUtils.copyToClipboard(codecOutputField.getText()));
        gbc.gridx = 3; gbc.gridwidth = 1;
        codecPanel.add(copyOutputBtn, gbc);

        add(codecPanel);

        // Subpanel 2: Epoch timestamp converter
        JPanel timePanel = new JPanel(new GridBagLayout());
        timePanel.setBorder(BorderFactory.createTitledBorder("时间戳转换 (Unix Timestamp)"));
        GridBagConstraints gbc2 = new GridBagConstraints();
        gbc2.fill = GridBagConstraints.HORIZONTAL;
        gbc2.insets = new Insets(4, 4, 4, 4);

        gbc2.gridx = 0; gbc2.gridy = 0;
        timePanel.add(new JLabel("时间戳(秒):"), gbc2);
        
        epochSecondsField = new JTextField(String.valueOf(Instant.now().getEpochSecond()));
        gbc2.gridx = 1;
        timePanel.add(epochSecondsField, gbc2);

        JButton toDateBtn = new JButton("转换 ➔");
        toDateBtn.addActionListener(e -> triggerTimestampToDate());
        gbc2.gridx = 2;
        timePanel.add(toDateBtn, gbc2);

        gbc2.gridx = 0; gbc2.gridy = 1;
        timePanel.add(new JLabel("日期时间:"), gbc2);

        epochDateTimeField = new JTextField("2026-07-16 12:00:00");
        gbc2.gridx = 1;
        timePanel.add(epochDateTimeField, gbc2);

        JButton toSecBtn = new JButton("➔ 转换");
        toSecBtn.addActionListener(e -> triggerDateToTimestamp());
        gbc2.gridx = 2;
        timePanel.add(toSecBtn, gbc2);

        add(timePanel);
    }

    private void triggerCodec(int mode) {
        String input = codecInputField.getText();
        if (input.isEmpty()) {
            codecOutputField.setText("");
            return;
        }
        try {
            switch (mode) {
                case 1:
                    codecOutputField.setText(Base64.getEncoder().encodeToString(input.getBytes(StandardCharsets.UTF_8)));
                    break;
                case 2:
                    codecOutputField.setText(new String(Base64.getDecoder().decode(input), StandardCharsets.UTF_8));
                    break;
                case 3:
                    codecOutputField.setText(URLEncoder.encode(input, StandardCharsets.UTF_8.name()));
                    break;
                case 4:
                    codecOutputField.setText(URLDecoder.decode(input, StandardCharsets.UTF_8.name()));
                    break;
            }
        } catch (Exception ex) {
            codecOutputField.setText("转换失败 (Conversion Failed): " + ex.getMessage());
        }
    }

    private void triggerTimestampToDate() {
        try {
            long sec = Long.parseLong(epochSecondsField.getText().trim());
            Instant instant = Instant.ofEpochSecond(sec);
            LocalDateTime dt = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
            epochDateTimeField.setText(dt.format(epochFormatter));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "时间戳无效 (Invalid Timestamp)", "转换失败", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void triggerDateToTimestamp() {
        try {
            String dateText = epochDateTimeField.getText().trim();
            LocalDateTime dt = LocalDateTime.parse(dateText, epochFormatter);
            long sec = dt.atZone(ZoneId.systemDefault()).toInstant().getEpochSecond();
            epochSecondsField.setText(String.valueOf(sec));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "日期格式需为 yyyy-MM-dd HH:mm:ss", "转换失败", JOptionPane.ERROR_MESSAGE);
        }
    }
}
