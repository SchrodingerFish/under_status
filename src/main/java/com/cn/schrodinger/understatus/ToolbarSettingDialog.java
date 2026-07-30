package com.cn.schrodinger.understatus;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JPasswordField;
import javax.swing.SpinnerNumberModel;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import com.cn.schrodinger.understatus.settings.SettingsRepository;
import com.cn.schrodinger.understatus.settings.UnderStatusSettings;

/**
 * General Toolbar and Toolbox Configuration Dialog (Clock Style, Pomodoro, Weather, Visibility).
 * Supports toolbox popover dimensions and QWeather configs.
 *
 * @author peter/antigravity
 */
public class ToolbarSettingDialog extends JDialog {

    private final Runnable onSaveCallback;

    // Pomodoro Timer Elements
    private JSpinner pomodoroWorkSpinner;
    private JSpinner pomodoroBreakSpinner;

    // Clock Style Elements
    private JComboBox<String> presetComboBox;
    private JTextField customPatternField;
    private JLabel previewLabel;
    private Timer previewTimer;

    // Toolbar Components Checkboxes
    private JCheckBox clockShowCB;
    private JCheckBox formatShowCB;
    private JCheckBox memoryShowCB;
    private JCheckBox pomodoroShowCB;
    private JCheckBox readOnlyShowCB;
    private JCheckBox metricsShowCB;
    private JCheckBox noteShowCB;
    private JCheckBox alarmsShowCB;
    private JCheckBox musicShowCB;

    // Toolbox Size Presets
    private JComboBox<String> toolboxSizeComboBox;

    // Music API elements
    private JTextField musicApiHostField;

    // Weather elements
    private JCheckBox showWeatherCB;
    private JTextField apiHostField;
    private JPasswordField apiKeyField;
    private JCheckBox autoIpCB;
    private JTextField cityField;

    // Preset mapping
    private final String[] presetPatterns = {
        "yyyy-MM-dd EEEE HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "EEEE HH:mm:ss"
    };

    private final int[][] sizePresets = {
        {800,  600},
        {1024, 768},
        {1280, 720},
        {1920, 1080},
        {2560, 1440},
        {3840, 2160},
        {7680, 4320}
    };

    public ToolbarSettingDialog(java.awt.Window parent, Runnable onSaveCallback) {
        super(parent, ModalityType.APPLICATION_MODAL);
        this.onSaveCallback = onSaveCallback;
        setTitle("状态栏与工具箱配置 (under_status Toolbar Config)");
        initComponents();
        loadSettings();
        pack();
        setLocationRelativeTo(parent);
    }

    private void initComponents() {
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Tab 1: Clock Style
        tabbedPane.addTab("时间显示格式 (Clock)", createClockStyleTab());

        // Tab 2: Pomodoro
        tabbedPane.addTab("番茄钟设置 (Pomodoro)", createPomodoroTab());

        // Tab 3: Weather
        tabbedPane.addTab("天气配置 (Weather)", createWeatherTab());

        // Tab 4: Music
        tabbedPane.addTab("音乐配置 (Music)", createMusicTab());

        // Tab 5: Toolbar config
        tabbedPane.addTab("工具栏与常规配置 (Settings)", createToolbarConfigTab());

        // Bottom Buttons
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 10));
        JButton saveBtn = new JButton("保存全部 (Save All)");
        JButton cancelBtn = new JButton("取消 (Cancel)");

        saveBtn.addActionListener(e -> {
            saveSettings();
            if (onSaveCallback != null) {
                onSaveCallback.run();
            }
            dispose();
        });
        cancelBtn.addActionListener(e -> dispose());

        bottomPanel.add(saveBtn);
        bottomPanel.add(cancelBtn);

        setLayout(new BorderLayout());
        add(tabbedPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private JPanel createPomodoroTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(10, 10, 10, 10);

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("工作/专注时长 (分钟):"), gbc);
        pomodoroWorkSpinner = new JSpinner(new SpinnerNumberModel(25, 1, 180, 5));
        gbc.gridx = 1;
        panel.add(pomodoroWorkSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("短休/休息时长 (分钟):"), gbc);
        pomodoroBreakSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 60, 1));
        gbc.gridx = 1;
        panel.add(pomodoroBreakSpinner, gbc);

        return panel;
    }

    private JPanel createClockStyleTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 8, 8, 8);

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("预设显示样式:"), gbc);

        String[] presetNames = {
            "完整显示: 年月日 星期 时分秒 (默认)",
            "普通显示: 年月日 时分秒",
            "仅显示时间: 时分秒",
            "简易显示: 年月日 时分",
            "星期时间: 星期 时分秒",
            "自定义格式 (Custom Pattern)"
        };
        presetComboBox = new JComboBox<>(presetNames);
        presetComboBox.addActionListener(e -> {
            int idx = presetComboBox.getSelectedIndex();
            if (idx >= 0 && idx < presetPatterns.length) {
                customPatternField.setText(presetPatterns[idx]);
                customPatternField.setEditable(false);
            } else {
                customPatternField.setEditable(true);
            }
            updatePreview();
        });
        gbc.gridx = 1;
        panel.add(presetComboBox, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("格式化字符串:"), gbc);

        customPatternField = new JTextField(20);
        customPatternField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { updatePreview(); }
            @Override
            public void removeUpdate(DocumentEvent e) { updatePreview(); }
            @Override
            public void changedUpdate(DocumentEvent e) { updatePreview(); }
        });
        gbc.gridx = 1;
        panel.add(customPatternField, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("时间预览:"), gbc);

        previewLabel = new JLabel("Preview...");
        previewLabel.setFont(previewLabel.getFont().deriveFont(13f).deriveFont(java.awt.Font.BOLD));
        gbc.gridx = 1;
        panel.add(previewLabel, gbc);

        previewTimer = new Timer(1000, e -> updatePreview());
        previewTimer.start();

        return panel;
    }

    private JPanel createWeatherTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 8, 8, 8);

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        showWeatherCB = new JCheckBox("在底部状态栏启用天气显示 (Enable Weather Display)");
        panel.add(showWeatherCB, gbc);

        gbc.gridy = 1; gbc.gridwidth = 1;
        panel.add(new JLabel("和风 API Host:"), gbc);
        apiHostField = new JTextField(24);
        gbc.gridx = 1;
        panel.add(apiHostField, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("和风 API_KEY (API Key):"), gbc);
        apiKeyField = new JPasswordField(24);
        gbc.gridx = 1;
        panel.add(apiKeyField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        autoIpCB = new JCheckBox("根据 IP 自动定位当前城市 (Auto Geolocation via IP)");
        autoIpCB.addActionListener(e -> cityField.setEnabled(!autoIpCB.isSelected()));
        panel.add(autoIpCB, gbc);

        gbc.gridy = 4; gbc.gridwidth = 1;
        panel.add(new JLabel("手动设置城市 (Manual City):"), gbc);
        cityField = new JTextField(20);
        gbc.gridx = 1;
        panel.add(cityField, gbc);

        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
        JLabel hostHelp = new JLabel("Host 填控制台专属主机名，不含 https:// 或路径");
        panel.add(hostHelp, gbc);

        gbc.gridy = 6;
        JButton testButton = new JButton("测试天气连接");
        testButton.addActionListener(e -> testWeatherConnection(testButton));
        panel.add(testButton, gbc);

        return panel;
    }

    private JPanel createMusicTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 8, 8, 8);

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("自定义 API Endpoint (可选):"), gbc);
        musicApiHostField = new JTextField(24);
        gbc.gridx = 1;
        panel.add(musicApiHostField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2;
        JLabel helpLabel = new JLabel("默认: https://music-api.gdstudio.xyz/api.php（留空使用默认）");
        helpLabel.setFont(helpLabel.getFont().deriveFont(11f));
        helpLabel.setForeground(java.awt.Color.GRAY);
        panel.add(helpLabel, gbc);

        return panel;
    }

    private JPanel createToolbarConfigTab() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Size Config Panel at North
        JPanel sizePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        sizePanel.add(new JLabel("工具箱尺寸 (Toolbox Size):"));
        
        String[] sizeNames = {
            "800 × 600 (默认)",
            "1024 × 768",
            "1280 × 720 (720P HD)",
            "1920 × 1080 (1080P FHD)",
            "2560 × 1440 (2K QHD)",
            "3840 × 2160 (4K UHD)",
            "7680 × 4320 (8K)"
        };
        toolboxSizeComboBox = new JComboBox<>(sizeNames);
        sizePanel.add(toolboxSizeComboBox);
        mainPanel.add(sizePanel, BorderLayout.NORTH);

        // Checkboxes Panel at Center
        JPanel cbPanel = new JPanel(new GridLayout(5, 2, 10, 10));
        clockShowCB = new JCheckBox("显示时钟 (Clock Label)");
        formatShowCB = new JCheckBox("显示代码格式化按钮 (Formatter)");
        memoryShowCB = new JCheckBox("显示内存监控与一键GC (JVM GC)");
        pomodoroShowCB = new JCheckBox("显示番茄钟 (Pomodoro)");
        readOnlyShowCB = new JCheckBox("显示只读锁定开关 (Read-only Lock)");
        metricsShowCB = new JCheckBox("显示文档字数与编码 (File Metrics)");
        noteShowCB = new JCheckBox("显示开发者工具箱 (Developer Vault)");
        alarmsShowCB = new JCheckBox("显示定时闹钟按钮 (Alarms Status)");
        musicShowCB = new JCheckBox("显示在线音乐播放器 (Music)");

        cbPanel.add(clockShowCB);
        cbPanel.add(formatShowCB);
        cbPanel.add(memoryShowCB);
        cbPanel.add(pomodoroShowCB);
        cbPanel.add(readOnlyShowCB);
        cbPanel.add(metricsShowCB);
        cbPanel.add(noteShowCB);
        cbPanel.add(alarmsShowCB);
        cbPanel.add(musicShowCB);

        mainPanel.add(cbPanel, BorderLayout.CENTER);
        return mainPanel;
    }

    private void updatePreview() {
        String pattern = customPatternField.getText().trim();
        if (pattern.isEmpty()) {
            previewLabel.setText("(请输入格式)");
            return;
        }
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
            previewLabel.setText(LocalDateTime.now().format(formatter));
            previewLabel.setForeground(UIManager.getColor("Label.foreground"));
        } catch (IllegalArgumentException ex) {
            previewLabel.setText("格式错误! (Invalid Pattern)");
            previewLabel.setForeground(java.awt.Color.RED);
        }
    }

    private void loadSettings() {
        SettingsRepository settingsRepository = SettingsRepository.getDefault();
        UnderStatusSettings settings = settingsRepository.load();
        
        pomodoroWorkSpinner.setValue(settings.pomodoroWorkMinutes());
        pomodoroBreakSpinner.setValue(settings.pomodoroBreakMinutes());

        String savedPattern = settings.clockPattern();
        customPatternField.setText(savedPattern);

        int matchIdx = -1;
        for (int i = 0; i < presetPatterns.length; i++) {
            if (presetPatterns[i].equals(savedPattern)) {
                matchIdx = i;
                break;
            }
        }
        if (matchIdx >= 0) {
            presetComboBox.setSelectedIndex(matchIdx);
            customPatternField.setEditable(false);
        } else {
            presetComboBox.setSelectedIndex(5);
            customPatternField.setEditable(true);
        }
        updatePreview();

        clockShowCB.setSelected(settings.showClock());
        formatShowCB.setSelected(settings.showFormat());
        memoryShowCB.setSelected(settings.showMemory());
        pomodoroShowCB.setSelected(settings.showPomodoro());
        readOnlyShowCB.setSelected(settings.showReadOnly());
        metricsShowCB.setSelected(settings.showMetrics());
        noteShowCB.setSelected(settings.showToolbox());
        alarmsShowCB.setSelected(settings.showAlarms());
        musicShowCB.setSelected(settings.showMusic());
        musicApiHostField.setText(settings.musicApiHost());

        // Load popover size
        int savedW = settings.toolboxWidth();
        int savedH = settings.toolboxHeight();
        int sizeIdx = 0;
        for (int i = 0; i < sizePresets.length; i++) {
            if (sizePresets[i][0] == savedW && sizePresets[i][1] == savedH) {
                sizeIdx = i;
                break;
            }
        }
        toolboxSizeComboBox.setSelectedIndex(sizeIdx);

        // Load weather credentials
        showWeatherCB.setSelected(settings.showWeather());
        apiHostField.setText(settings.qweatherApiHost());
        apiKeyField.setText(settings.qweatherApiKey());
        boolean autoIp = settings.qweatherAutoIp();
        autoIpCB.setSelected(autoIp);
        cityField.setText(settings.qweatherCity());
        cityField.setEnabled(!autoIp);
    }

    private void saveSettings() {
        SettingsRepository settingsRepository = SettingsRepository.getDefault();
        UnderStatusSettings current = settingsRepository.load();

        String pattern = customPatternField.getText().trim();
        if (pattern.isEmpty()) {
            pattern = "yyyy-MM-dd EEEE HH:mm:ss";
        } else {
            try {
                DateTimeFormatter.ofPattern(pattern);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "时间格式输入无效，已重置为默认格式！", "格式错误", JOptionPane.WARNING_MESSAGE);
                pattern = "yyyy-MM-dd EEEE HH:mm:ss";
            }
        }
        // Save popover size
        int width = current.toolboxWidth();
        int height = current.toolboxHeight();
        int sizeIdx = toolboxSizeComboBox.getSelectedIndex();
        if (sizeIdx >= 0 && sizeIdx < sizePresets.length) {
            width = sizePresets[sizeIdx][0];
            height = sizePresets[sizeIdx][1];
        }

        settingsRepository.save(new UnderStatusSettings(
                pattern,
                (Integer) pomodoroWorkSpinner.getValue(),
                (Integer) pomodoroBreakSpinner.getValue(),
                clockShowCB.isSelected(),
                formatShowCB.isSelected(),
                memoryShowCB.isSelected(),
                pomodoroShowCB.isSelected(),
                readOnlyShowCB.isSelected(),
                metricsShowCB.isSelected(),
                noteShowCB.isSelected(),
                alarmsShowCB.isSelected(),
                showWeatherCB.isSelected(),
                musicShowCB.isSelected(),
                musicApiHostField.getText().trim(),
                apiHostField.getText().trim(),
                new String(apiKeyField.getPassword()).trim(),
                cityField.getText().trim(),
                autoIpCB.isSelected(),
                width,
                height));
    }

    private void testWeatherConnection(JButton button) {
        String host = apiHostField.getText().trim();
        String key = new String(apiKeyField.getPassword()).trim();
        String city = cityField.getText().trim();
        boolean autoIp = autoIpCB.isSelected();
        button.setEnabled(false);
        org.openide.util.RequestProcessor.getDefault().post(() -> {
            String message;
            int type;
            try {
                com.cn.schrodinger.understatus.weather.QWeatherConfig config =
                        new com.cn.schrodinger.understatus.weather.QWeatherConfig(
                                host, key, "zh", "m");
                com.cn.schrodinger.understatus.weather.JdkHttpTransport transport =
                        new com.cn.schrodinger.understatus.weather.JdkHttpTransport();
                com.cn.schrodinger.understatus.weather.LocationContext location =
                        new com.cn.schrodinger.understatus.weather.LocationResolver(transport)
                                .resolve(config, city, autoIp);
                com.cn.schrodinger.understatus.weather.WeatherNow now =
                        new com.cn.schrodinger.understatus.weather.QWeatherClient(transport)
                                .fetchNow(config, location);
                message = location.name() + "：" + now.condition() + " "
                        + now.temperatureCelsius() + "°C，连接成功";
                type = JOptionPane.INFORMATION_MESSAGE;
            } catch (Exception ex) {
                message = ex.getMessage() == null ? "天气连接失败" : ex.getMessage();
                type = JOptionPane.ERROR_MESSAGE;
            }
            String result = message;
            int resultType = type;
            javax.swing.SwingUtilities.invokeLater(() -> {
                button.setEnabled(true);
                JOptionPane.showMessageDialog(this, result, "天气连接测试", resultType);
            });
        });
    }

    @Override
    public void dispose() {
        if (previewTimer != null) {
            previewTimer.stop();
        }
        super.dispose();
    }
}
