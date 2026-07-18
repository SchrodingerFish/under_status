package com.cn.schrodinger.understatus;

import com.cn.schrodinger.understatus.alarm.Alarm;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;

/**
 * Sub-dialog for creating or editing a single alarm instance.
 * Allows custom message, time (HH:mm), and recurrence pattern.
 *
 * @author peter/antigravity
 */
public class AlarmEditDialog extends JDialog {

    private final Alarm alarm;
    private boolean saved = false;

    private JSpinner hourSpinner;
    private JSpinner minuteSpinner;
    private JTextField messageField;
    private JCheckBox enabledCheckBox;
    private JComboBox<String> repeatModeComboBox;
    
    // Checkboxes for Monday to Sunday
    private JCheckBox[] dayCheckBoxes;
    private JPanel daysPanel;

    public AlarmEditDialog(java.awt.Dialog owner, Alarm alarm, boolean isNew) {
        super(owner, isNew ? "添加闹钟 (Add Alarm)" : "修改闹钟 (Edit Alarm)", true);
        this.alarm = alarm;
        initComponents();
        loadAlarmData();
        updateDaysEnabledState();
        pack();
        setLocationRelativeTo(owner);
    }

    private void initComponents() {
        JPanel contentPanel = new JPanel(new GridBagLayout());
        contentPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 6, 6, 6);

        // 1. Time Selection
        gbc.gridx = 0;
        gbc.gridy = 0;
        contentPanel.add(new JLabel("时间 (Time):"), gbc);

        JPanel timePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        hourSpinner = new JSpinner(new SpinnerNumberModel(12, 0, 23, 1));
        minuteSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 59, 1));
        timePanel.add(hourSpinner);
        timePanel.add(new JLabel(":"));
        timePanel.add(minuteSpinner);

        gbc.gridx = 1;
        contentPanel.add(timePanel, gbc);

        // 2. Message Field
        gbc.gridx = 0;
        gbc.gridy = 1;
        contentPanel.add(new JLabel("提醒消息 (Message):"), gbc);

        messageField = new JTextField(20);
        gbc.gridx = 1;
        contentPanel.add(messageField, gbc);

        // 3. Enabled State
        gbc.gridx = 0;
        gbc.gridy = 2;
        contentPanel.add(new JLabel("启用状态 (Enabled):"), gbc);

        enabledCheckBox = new JCheckBox("开启该闹钟 (Enable this alarm)");
        enabledCheckBox.setSelected(true);
        gbc.gridx = 1;
        contentPanel.add(enabledCheckBox, gbc);

        // 4. Repeat Mode ComboBox
        gbc.gridx = 0;
        gbc.gridy = 3;
        contentPanel.add(new JLabel("重复模式 (Repeat):"), gbc);

        String[] modes = {"仅一次 (Once)", "每天 (Daily)", "工作日 (Weekdays)", "自定义 (Custom)"};
        repeatModeComboBox = new JComboBox<>(modes);
        repeatModeComboBox.addActionListener(e -> {
            updateDaysBasedOnPreset();
            updateDaysEnabledState();
        });
        gbc.gridx = 1;
        contentPanel.add(repeatModeComboBox, gbc);

        // 5. Weekday Checkboxes (Mon - Sun)
        gbc.gridx = 0;
        gbc.gridy = 4;
        contentPanel.add(new JLabel("选择重复天 (Days):"), gbc);

        daysPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        dayCheckBoxes = new JCheckBox[7];
        String[] shortWeekDays = {"一", "二", "三", "四", "五", "六", "日"};
        for (int i = 0; i < 7; i++) {
            dayCheckBoxes[i] = new JCheckBox(shortWeekDays[i]);
            daysPanel.add(dayCheckBoxes[i]);
        }
        gbc.gridx = 1;
        contentPanel.add(daysPanel, gbc);

        // 6. Action Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        JButton saveBtn = new JButton("保存 (Save)");
        JButton cancelBtn = new JButton("取消 (Cancel)");

        saveBtn.addActionListener(e -> {
            saveAlarmData();
            saved = true;
            dispose();
        });
        cancelBtn.addActionListener(e -> dispose());

        buttonPanel.add(saveBtn);
        buttonPanel.add(cancelBtn);

        setLayout(new BorderLayout());
        add(contentPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void loadAlarmData() {
        // Parse time
        String[] timeParts = alarm.time.split(":");
        if (timeParts.length == 2) {
            hourSpinner.setValue(Integer.parseInt(timeParts[0]));
            minuteSpinner.setValue(Integer.parseInt(timeParts[1]));
        }
        messageField.setText(alarm.message);
        enabledCheckBox.setSelected(alarm.enabled);

        // Map repeat mode
        if ("ONCE".equals(alarm.repeatMode)) {
            repeatModeComboBox.setSelectedIndex(0);
        } else if ("DAILY".equals(alarm.repeatMode)) {
            repeatModeComboBox.setSelectedIndex(1);
        } else if ("WEEKDAYS".equals(alarm.repeatMode)) {
            repeatModeComboBox.setSelectedIndex(2);
        } else if ("CUSTOM".equals(alarm.repeatMode)) {
            repeatModeComboBox.setSelectedIndex(3);
        }

        // Load repeat days
        for (int i = 0; i < 7; i++) {
            dayCheckBoxes[i].setSelected(alarm.repeatDays[i]);
        }
    }

    private void updateDaysBasedOnPreset() {
        int idx = repeatModeComboBox.getSelectedIndex();
        if (idx == 0) { // ONCE
            for (JCheckBox cb : dayCheckBoxes) cb.setSelected(false);
        } else if (idx == 1) { // DAILY
            for (JCheckBox cb : dayCheckBoxes) cb.setSelected(true);
        } else if (idx == 2) { // WEEKDAYS
            for (int i = 0; i < 5; i++) dayCheckBoxes[i].setSelected(true);
            for (int i = 5; i < 7; i++) dayCheckBoxes[i].setSelected(false);
        }
    }

    private void updateDaysEnabledState() {
        // Only enabled for "Custom" mode
        boolean isCustom = (repeatModeComboBox.getSelectedIndex() == 3);
        for (JCheckBox cb : dayCheckBoxes) {
            cb.setEnabled(isCustom);
        }
    }

    private void saveAlarmData() {
        // Save time
        alarm.time = String.format("%02d:%02d", (Integer) hourSpinner.getValue(), (Integer) minuteSpinner.getValue());
        alarm.message = messageField.getText().trim();
        alarm.enabled = enabledCheckBox.isSelected();

        // Save mode
        int idx = repeatModeComboBox.getSelectedIndex();
        if (idx == 0) alarm.repeatMode = "ONCE";
        else if (idx == 1) alarm.repeatMode = "DAILY";
        else if (idx == 2) alarm.repeatMode = "WEEKDAYS";
        else if (idx == 3) alarm.repeatMode = "CUSTOM";

        // Save repeat days
        for (int i = 0; i < 7; i++) {
            alarm.repeatDays[i] = dayCheckBoxes[i].isSelected();
        }
    }

    public boolean isSaved() {
        return saved;
    }
}
