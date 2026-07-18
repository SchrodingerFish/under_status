package com.cn.schrodinger.understatus;

import com.cn.schrodinger.understatus.alarm.Alarm;
import com.cn.schrodinger.understatus.alarm.AlarmScheduler;
import com.cn.schrodinger.understatus.pomodoro.PomodoroEngine;
import com.cn.schrodinger.understatus.statusbar.EditorMetrics;
import com.cn.schrodinger.understatus.statusbar.EditorMetricsTracker;
import com.cn.schrodinger.understatus.statusbar.LatestTask;
import com.cn.schrodinger.understatus.weather.WeatherDisplayPolicy;
import com.cn.schrodinger.understatus.weather.WeatherDisplayState;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyledDocument;
import org.netbeans.api.editor.EditorRegistry;
import org.netbeans.modules.editor.indent.api.Reformat;
import org.openide.awt.StatusDisplayer;
import org.openide.loaders.DataObject;
import org.openide.text.NbDocument;
import org.openide.util.RequestProcessor;
import com.cn.schrodinger.understatus.settings.SettingsRepository;
import com.cn.schrodinger.understatus.settings.UnderStatusSettings;

/**
 * Main bottom toolbar UI panel.
 * Prominent Toolbox button styled with accent colors. Added settings gear button.
 *
 * @author peter/antigravity
 */
public class BottomToolbarView extends JPanel {

    private static final long WEATHER_REFRESH_INTERVAL_MILLIS = 20 * 60 * 1000L;
    private static final long WEATHER_RETRY_INTERVAL_MILLIS = 60 * 1000L;

    // Sub-components
    private JLabel clockLabel;
    private JButton formatButton;
    private JLabel memoryLabel;
    private JButton pomodoroButton;
    private JButton readOnlyButton;
    private JLabel metricsLabel;
    private JButton noteButton; // "🧰 工具宝库"
    private JButton alarmButton;
    private JButton settingButton; // "⚙️"

    // Clock formatting
    private DateTimeFormatter timeFormatter = null;
    private String rawPattern = "";

    // Pomodoro Engine
    private final PomodoroEngine pomodoroEngine = new PomodoroEngine();
    private final AlarmScheduler alarmScheduler = new AlarmScheduler();

    // Alarms Cache
    private final List<Alarm> alarms = new ArrayList<>();

    // Visibilities
    private boolean showClock = true;
    private boolean showFormat = true;
    private boolean showMemory = true;
    private boolean showPomodoro = true;
    private boolean showReadOnly = true;
    private boolean showMetrics = true;
    private boolean showNote = true;
    private boolean showAlarms = true;

    // Weather states
    private JLabel weatherLabel;
    private boolean showWeather = false;
    private String qweatherApiHost = "";
    private String qweatherApiKey = "";
    private String qweatherCity = "北京";
    private boolean qweatherAutoIp = true;
    private long lastWeatherUpdate = 0;
    private String cachedWeatherText = "";
    private boolean isFetchingWeather = false;
    private Timer updateTimer;
    private final RequestProcessor weatherProcessor = new RequestProcessor("UnderStatus Weather", 1, true);
    private final LatestTask<String> weatherTask = new LatestTask<>(weatherProcessor);
    private final EditorMetricsTracker metricsTracker = new EditorMetricsTracker(this::applyEditorMetrics);

    public BottomToolbarView() {
        initComponents();
        loadSettings();
        startTimer();
        metricsTracker.start();
    }

    private void initComponents() {
        setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
        setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));

        // Weather Label
        weatherLabel = new JLabel("");
        weatherLabel.setFont(UIManager.getFont("Label.font").deriveFont(11f));
        weatherLabel.setToolTipText("当前当地天气 (和风天气 API)");
        weatherLabel.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        // Click to show detailed forecast dialog
        weatherLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (qweatherApiHost.isBlank() || qweatherApiKey.isBlank()) {
                    showGeneralSettingsDialog();
                    return;
                }
                java.awt.Window parent = SwingUtilities.getWindowAncestor(BottomToolbarView.this);
                WeatherDetailDialog dialog = new WeatherDetailDialog(parent, qweatherApiHost,
                        qweatherApiKey, qweatherCity, qweatherAutoIp, () -> {
                    // Force immediate weather refresh after manual refresh inside dialog
                    lastWeatherUpdate = 0;
                });

                // Position dialog ABOVE the weather label so it doesn't go below the taskbar
                java.awt.Point pt = weatherLabel.getLocationOnScreen();
                int dialogW = dialog.getWidth();
                int dialogH = dialog.getHeight();

                // Get usable screen bounds (respects taskbar insets)
                java.awt.GraphicsConfiguration gc = weatherLabel.getGraphicsConfiguration();
                java.awt.Rectangle screenBounds = (gc != null)
                        ? gc.getBounds()
                        : new java.awt.Rectangle(java.awt.Toolkit.getDefaultToolkit().getScreenSize());
                java.awt.Insets insets = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(
                        gc != null ? gc : java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                                .getDefaultScreenDevice().getDefaultConfiguration());
                int usableBottom = screenBounds.y + screenBounds.height - insets.bottom;

                // Preferred position: above the label
                int x = pt.x;
                int y = pt.y - dialogH - 4;

                // Clamp horizontally
                if (x + dialogW > screenBounds.x + screenBounds.width - insets.right) {
                    x = screenBounds.x + screenBounds.width - insets.right - dialogW - 4;
                }
                if (x < screenBounds.x + insets.left) {
                    x = screenBounds.x + insets.left;
                }
                // Clamp vertically: ensure it stays above the taskbar
                if (y + dialogH > usableBottom) {
                    y = usableBottom - dialogH - 4;
                }
                if (y < screenBounds.y + insets.top) {
                    y = screenBounds.y + insets.top;
                }

                dialog.setLocation(x, y);
                dialog.setVisible(true);
            }
        });
        // 1. Clock Label
        clockLabel = new JLabel("加载中...");
        clockLabel.setFont(UIManager.getFont("Label.font").deriveFont(11f));

        // 2. Format Button
        formatButton = createFlatButton("⚡ 格式化 (Format)", "点击一键格式化当前激活的编辑器 (Format active editor)");
        formatButton.addActionListener(e -> triggerFormat());

        // 3. Memory Monitor Label
        memoryLabel = new JLabel("📊 --M/--M");
        memoryLabel.setFont(UIManager.getFont("Label.font").deriveFont(11f));
        memoryLabel.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        memoryLabel.setToolTipText("JVM堆内存使用量 (点击触发垃圾回收垃圾清理)");
        memoryLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    System.gc();
                    StatusDisplayer.getDefault().setStatusText("JVM垃圾清理已触发 (JVM GC Triggered)");
                    updateMemoryInfo();
                }
            }
        });

        // 4. Pomodoro Button
        pomodoroButton = createFlatButton("🍅 番茄钟", "点击开始/暂停，双击重置番茄钟");
        pomodoroButton.addActionListener(e -> togglePomodoro());
        pomodoroButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    resetPomodoro();
                }
            }
        });

        // 5. Read-Only Lock Button
        readOnlyButton = createFlatButton("🔓 可写", "点击切换当前文件只读/可写状态");
        readOnlyButton.addActionListener(e -> toggleReadOnly());

        // 6. Metrics Label
        metricsLabel = new JLabel("📄 无活跃编辑器");
        metricsLabel.setFont(UIManager.getFont("Label.font").deriveFont(11f));
        metricsLabel.setToolTipText("当前激活文件的行数、字数及编码格式");

        // 7. Toolbox button (Highly Prominent Highlight)
        noteButton = new JButton("🧰 工具宝库");
        noteButton.setToolTipText("开启草稿便笺、计算器、取色器、编解码、时间戳等工具箱");
        noteButton.setFont(UIManager.getFont("Button.font").deriveFont(11f).deriveFont(java.awt.Font.BOLD));
        noteButton.setFocusPainted(false);
        noteButton.setContentAreaFilled(true);
        noteButton.setBackground(new Color(0, 120, 215, 35)); // Accent blue tint
        noteButton.setForeground(UIManager.getColor("Button.foreground"));
        noteButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0, 120, 215, 100), 1),
                BorderFactory.createEmptyBorder(2, 8, 2, 8)
        ));
        noteButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                noteButton.setBackground(new Color(0, 120, 215, 75));
            }
            @Override
            public void mouseExited(MouseEvent e) {
                noteButton.setBackground(new Color(0, 120, 215, 35));
            }
        });
        noteButton.addActionListener(e -> showQuickNoteCalcDialog());

        // 8. Alarm Status Button
        alarmButton = createFlatButton("⏰ 闹钟", "点击配置定时闹钟 (Alarms settings only)");
        alarmButton.addActionListener(e -> showAlarmSettingsDialog());

        // 9. Settings gear button (Always visible at the end)
        settingButton = createFlatButton("⚙️", "点击打开状态栏与工具箱配置 (Configure status bar & toolbox)");
        settingButton.addActionListener(e -> showGeneralSettingsDialog());
    }

    private JSeparator createSeparator() {
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setMaximumSize(new Dimension(3, 16));
        sep.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        return sep;
    }

    private JButton createFlatButton(String text, String tooltip) {
        JButton btn = new JButton(text);
        UiDefaults.describe(btn, text.replaceAll("[^\\p{L}\\p{N} ]", "").trim(), tooltip);
        btn.setFont(UIManager.getFont("Button.font").deriveFont(11f));
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setMargin(new Insets(2, 6, 2, 6));
        btn.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (btn.isEnabled()) {
                    btn.setContentAreaFilled(true);
                    btn.setBackground(new Color(128, 128, 128, 60));
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                btn.setContentAreaFilled(false);
            }
        });

        return btn;
    }

    private void startTimer() {
        updateTimer = new Timer(1000, e -> {
            LocalDateTime now = LocalDateTime.now();
            
            // 1. Update Clock
            if (showClock) {
                if (timeFormatter != null) {
                    try {
                        clockLabel.setText(now.format(timeFormatter));
                    } catch (Exception ex) {
                        clockLabel.setText(now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd EEEE HH:mm:ss")));
                    }
                } else {
                    clockLabel.setText(now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd EEEE HH:mm:ss")));
                }
            }

            // 2. Update Memory
            if (showMemory) {
                updateMemoryInfo();
            }

            // 3. Update Pomodoro
            if (showPomodoro) {
                tickPomodoro();
            }

            // 4. Update Read-only status
            if (showReadOnly) {
                updateReadOnlyState();
            }
            // 5. Refresh editor metrics (covers focus changes the registry may miss)
            if (showMetrics) {
                metricsTracker.refreshNow();
            }
            // 6. Check Alarms
            checkAlarms(now);

            // 6. Update Weather asynchronously
            refreshWeather(false);
        });
        updateTimer.start();
    }

    private void refreshWeather(boolean force) {
        if (!showWeather) {
            return;
        }
        if (qweatherApiHost.isBlank() || qweatherApiKey.isBlank()) {
            cachedWeatherText = "";
            weatherLabel.setText("🌤 天气：待配置");
            weatherLabel.setToolTipText("点击配置和风天气 API Host 和 API Key");
            isFetchingWeather = false;
            rebuildToolbarLayout();
            return;
        }

        long now = System.currentTimeMillis();
        long interval = cachedWeatherText.isEmpty()
                ? WEATHER_RETRY_INTERVAL_MILLIS : WEATHER_REFRESH_INTERVAL_MILLIS;
        if (!force && (isFetchingWeather || now - lastWeatherUpdate < interval)) {
            return;
        }

        isFetchingWeather = true;
        lastWeatherUpdate = now;
        if (cachedWeatherText.isEmpty()) {
            weatherLabel.setText("🌤 正在加载天气…");
            rebuildToolbarLayout();
        }

        String apiHost = qweatherApiHost;
        String apiKey = qweatherApiKey;
        String city = qweatherCity;
        boolean autoIp = qweatherAutoIp;
        weatherTask.submit(
                () -> QWeatherService.fetchWeather(apiHost, apiKey, city, autoIp),
                result -> {
                    isFetchingWeather = false;
                    if (result != null && !result.isBlank()) {
                        cachedWeatherText = result;
                        weatherLabel.setText(result);
                        weatherLabel.setToolTipText("和风天气 · 点击查看详情");
                    } else {
                        cachedWeatherText = "";
                        weatherLabel.setText("⚠ 天气获取失败，请检查 API Key 或网络");
                    }
                    rebuildToolbarLayout();
                },
                error -> {
                    isFetchingWeather = false;
                    cachedWeatherText = "";
                    weatherLabel.setText("⚠ 天气获取失败，请检查 API Key 或网络");
                    rebuildToolbarLayout();
                });
    }

    @Override
    public void removeNotify() {
        if (updateTimer != null) updateTimer.stop();
        weatherTask.close();
        weatherProcessor.stop();
        metricsTracker.close();
        super.removeNotify();
    }

    private void updateMemoryInfo() {
        long totalMemory = Runtime.getRuntime().totalMemory() / (1024 * 1024);
        long freeMemory = Runtime.getRuntime().freeMemory() / (1024 * 1024);
        long usedMemory = totalMemory - freeMemory;
        memoryLabel.setText(String.format("📊 %dM/%dM", usedMemory, totalMemory));
    }

    private void tickPomodoro() {
        boolean transitioned = pomodoroEngine.tick();
        if (transitioned) {
            if ("BREAK".equals(pomodoroEngine.getState())) {
                triggerPopupAlert("🍅 番茄钟提醒", "专注阶段结束！现在是休息时间，请起身活动放松一下。");
            } else {
                triggerPopupAlert("🍅 番茄钟提醒", "休息结束！重新开始新一轮的高效专注吧。");
            }
        }
        updatePomodoroText();
    }

    private void updatePomodoroText() {
        if (!pomodoroEngine.isRunning()) {
            pomodoroButton.setText("🍅 番茄钟 (未开启)");
        } else {
            int min = pomodoroEngine.getTimeLeft() / 60;
            int sec = pomodoroEngine.getTimeLeft() % 60;
            String modeStr = "WORK".equals(pomodoroEngine.getState()) ? "专注" : "休息";
            pomodoroButton.setText(String.format("🍅 %s: %02d:%02d", modeStr, min, sec));
        }
    }

    private void togglePomodoro() {
        pomodoroEngine.setRunning(!pomodoroEngine.isRunning());
        updatePomodoroText();
    }

    private void resetPomodoro() {
        pomodoroEngine.reset();
        updatePomodoroText();
        StatusDisplayer.getDefault().setStatusText("番茄工作钟已重置");
    }

    private void updateReadOnlyState() {
        DataObject dobj = FileUtils.getActiveDataObject();
        if (dobj != null) {
            boolean writable = FileUtils.isFileWritable(dobj);
            readOnlyButton.setText(writable ? "🔓 可读写" : "🔒 只读");
            readOnlyButton.setEnabled(true);
        } else {
            readOnlyButton.setText("🔒 无活跃文件");
            readOnlyButton.setEnabled(false);
        }
    }

    private void toggleReadOnly() {
        DataObject dobj = FileUtils.getActiveDataObject();
        if (dobj != null) {
            boolean currentWritable = FileUtils.isFileWritable(dobj);
            boolean success = FileUtils.toggleFileReadOnly(dobj);
            if (success) {
                updateReadOnlyState();
                StatusDisplayer.getDefault().setStatusText(currentWritable ? "文件已设为只读模式 (Read-only)" : "文件已解除只读模式 (Writable)");
            }
        }
    }

    private void applyEditorMetrics(EditorMetrics metrics) {
        if (metricsLabel == null) {
            return;
        }
        if (metrics == null || !metrics.available()) {
            metricsLabel.setText("📄 无活跃编辑器");
            return;
        }
        metricsLabel.setText(String.format("📄 %dL | %d字 | %s",
                metrics.lines(), metrics.characters(), metrics.encoding()));
    }

    private void showQuickNoteCalcDialog() {
        Window parent = SwingUtilities.getWindowAncestor(this);
        QuickNoteCalcDialog dialog = new QuickNoteCalcDialog(parent);
        
        java.awt.Point btnLocation = noteButton.getLocationOnScreen();
        int popupWidth = dialog.getWidth();
        int popupHeight = dialog.getHeight();

        java.awt.GraphicsConfiguration gc = noteButton.getGraphicsConfiguration();
        java.awt.Rectangle screenBounds = (gc != null) ? gc.getBounds() : new java.awt.Rectangle(java.awt.Toolkit.getDefaultToolkit().getScreenSize());

        int x = btnLocation.x;
        int y = btnLocation.y - popupHeight - 5;

        if (x + popupWidth > screenBounds.x + screenBounds.width) {
            x = screenBounds.x + screenBounds.width - popupWidth - 10;
        }
        if (x < screenBounds.x) {
            x = screenBounds.x + 10;
        }
        if (y < screenBounds.y) {
            y = screenBounds.y + 10;
        }

        dialog.setLocation(x, y);
        dialog.setVisible(true);
    }

    public void loadSettings() {
        SettingsRepository settingsRepository = SettingsRepository.getDefault();
        UnderStatusSettings settings = settingsRepository.load();

        // 1. Clock Format
        String savedPattern = settings.clockPattern();
        if (!savedPattern.equals(rawPattern) || timeFormatter == null) {
            rawPattern = savedPattern;
            try {
                timeFormatter = DateTimeFormatter.ofPattern(rawPattern);
            } catch (Exception ex) {
                timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd EEEE HH:mm:ss");
            }
        }

        // 2. Pomodoro config
        pomodoroEngine.init(settings.pomodoroWorkMinutes(), settings.pomodoroBreakMinutes());
        updatePomodoroText();

        // 3. Alarms
        String serializedAlarms = settingsRepository.loadAlarms();
        alarms.clear();
        alarms.addAll(Alarm.deserializeList(serializedAlarms));
        updateAlarmButtonText();

        // 4. Component Visibilities
        showClock = settings.showClock();
        formatButton.setVisible(showFormat = settings.showFormat());
        memoryLabel.setVisible(showMemory = settings.showMemory());
        pomodoroButton.setVisible(showPomodoro = settings.showPomodoro());
        readOnlyButton.setVisible(showReadOnly = settings.showReadOnly());
        metricsLabel.setVisible(showMetrics = settings.showMetrics());
        noteButton.setVisible(showNote = settings.showToolbox());
        alarmButton.setVisible(showAlarms = settings.showAlarms());

        // 5. Weather settings
        boolean prevShowWeather = showWeather;
        String previousApiHost = qweatherApiHost;
        String previousApiKey = qweatherApiKey;
        String previousCity = qweatherCity;
        boolean previousAutoIp = qweatherAutoIp;
        showWeather = settings.showWeather();
        qweatherApiHost = settings.qweatherApiHost();
        qweatherApiKey = settings.qweatherApiKey();
        qweatherCity = settings.qweatherCity();
        qweatherAutoIp = settings.qweatherAutoIp();

        WeatherDisplayState weatherState = WeatherDisplayPolicy.evaluate(
                prevShowWeather, previousApiHost, previousApiKey, previousCity, previousAutoIp,
                showWeather, qweatherApiHost, qweatherApiKey, qweatherCity, qweatherAutoIp);
        if (!java.util.Objects.equals(previousApiHost, qweatherApiHost)
                || !java.util.Objects.equals(previousApiKey, qweatherApiKey)
                || !java.util.Objects.equals(previousCity, qweatherCity)
                || previousAutoIp != qweatherAutoIp) {
            QWeatherService.clearCache();
        }
        if (!weatherState.visible()) {
            lastWeatherUpdate = 0;
            cachedWeatherText = "";
            weatherLabel.setText("");
        } else if (!weatherState.text().isEmpty()) {
            cachedWeatherText = "";
            weatherLabel.setText(weatherState.text());
            if (weatherState.refreshImmediately()) {
                lastWeatherUpdate = 0;
            }
        }

        rebuildToolbarLayout();
        if (weatherState.refreshImmediately()) {
            refreshWeather(true);
        }
    }

    private void rebuildToolbarLayout() {
        removeAll();
        boolean first = true;

        if (showWeather) {
            add(weatherLabel);
            first = false;
        }
        if (showClock) {
            if (!first) add(createSeparator());
            add(clockLabel);
            first = false;
        }
        if (showFormat) {
            if (!first) add(createSeparator());
            add(formatButton);
            first = false;
        }
        if (showMemory) {
            if (!first) add(createSeparator());
            add(memoryLabel);
            first = false;
        }
        if (showPomodoro) {
            if (!first) add(createSeparator());
            add(pomodoroButton);
            first = false;
        }
        if (showReadOnly) {
            if (!first) add(createSeparator());
            add(readOnlyButton);
            first = false;
        }
        if (showMetrics) {
            if (!first) add(createSeparator());
            add(metricsLabel);
            first = false;
        }
        if (showNote) {
            if (!first) add(createSeparator());
            add(noteButton);
            first = false;
        }
        if (showAlarms) {
            if (!first) add(createSeparator());
            add(alarmButton);
        }

        // Setting gear is always appended
        add(createSeparator());
        add(settingButton);

        revalidate();
        repaint();
    }

    private void updateAlarmButtonText() {
        int activeCount = 0;
        for (Alarm alarm : alarms) {
            if (alarm.enabled) {
                activeCount++;
            }
        }
        if (activeCount > 0) {
            alarmButton.setText(String.format("⏰ 闹钟: %d个开启", activeCount));
        } else {
            alarmButton.setText("⏰ 闹钟 (未启用)");
        }
    }

    private void showAlarmSettingsDialog() {
        Frame parentFrame = (Frame) SwingUtilities.getAncestorOfClass(Frame.class, this);
        AlarmSettingDialog dialog = new AlarmSettingDialog(parentFrame, true, this::loadSettings);
        dialog.setVisible(true);
    }

    private void showGeneralSettingsDialog() {
        Frame parentFrame = (Frame) SwingUtilities.getAncestorOfClass(Frame.class, this);
        ToolbarSettingDialog dialog = new ToolbarSettingDialog(parentFrame, this::loadSettings);
        dialog.setVisible(true);
    }

    private void checkAlarms(LocalDateTime now) {
        List<Alarm> due = alarmScheduler.dueAlarms(alarms, now);
        for (Alarm alarm : due) {
            triggerPopupAlert("⏰ 闹钟提醒", alarm.message);
        }
        if (!due.isEmpty()) {
            saveAlarmsToPreferences();
        }
    }

    private void saveAlarmsToPreferences() {
        SettingsRepository.getDefault().saveAlarms(Alarm.serializeList(alarms));
        updateAlarmButtonText();
    }

    private void triggerPopupAlert(String title, String message) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane pane = new JOptionPane(
                    message, 
                    JOptionPane.INFORMATION_MESSAGE, 
                    JOptionPane.DEFAULT_OPTION
            );
            JDialog dialog = pane.createDialog(null, title);
            dialog.setModal(false);
            dialog.setVisible(true);
        });
    }

    private void triggerFormat() {
        JTextComponent editor = EditorRegistry.lastFocusedComponent();
        if (editor == null) {
            StatusDisplayer.getDefault().setStatusText("没有激活的编辑器以进行格式化");
            return;
        }

        final Document doc = editor.getDocument();
        final Reformat reformat = Reformat.get(doc);
        if (reformat == null) {
            StatusDisplayer.getDefault().setStatusText("当前编辑器不支持格式化");
            return;
        }

        Runnable reformatTask = () -> {
            reformat.lock();
            try {
                reformat.reformat(0, doc.getLength());
                StatusDisplayer.getDefault().setStatusText("代码格式化完成");
            } catch (Exception ex) {
                StatusDisplayer.getDefault().setStatusText("格式化失败: " + ex.getMessage());
            } finally {
                reformat.unlock();
            }
        };

        if (doc instanceof StyledDocument) {
            NbDocument.runAtomic((StyledDocument) doc, reformatTask);
        } else {
            SwingUtilities.invokeLater(reformatTask);
        }
    }
}
