package com.cn.schrodinger.understatus;

import com.cn.schrodinger.understatus.weather.AirQualitySnapshot;
import com.cn.schrodinger.understatus.weather.DailyForecast;
import com.cn.schrodinger.understatus.weather.ForecastRange;
import com.cn.schrodinger.understatus.weather.GridDailyForecast;
import com.cn.schrodinger.understatus.weather.GridHourlyForecast;
import com.cn.schrodinger.understatus.weather.GridWeatherNow;
import com.cn.schrodinger.understatus.weather.HistoricalWeather;
import com.cn.schrodinger.understatus.weather.HourlyForecast;
import com.cn.schrodinger.understatus.weather.LocationContext;
import com.cn.schrodinger.understatus.weather.MinutelyPrecipitation;
import com.cn.schrodinger.understatus.weather.QWeatherConfig;
import com.cn.schrodinger.understatus.weather.WeatherDataService;
import com.cn.schrodinger.understatus.weather.WeatherDataService.Result;
import com.cn.schrodinger.understatus.weather.WeatherException;
import com.cn.schrodinger.understatus.weather.WeatherIndex;
import com.cn.schrodinger.understatus.weather.WeatherIndexRange;
import com.cn.schrodinger.understatus.weather.WeatherNow;
import com.cn.schrodinger.understatus.weather.WeatherReference;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.openide.util.RequestProcessor;

/** Lazy, modeless weather center backed by the complete QWeather integration. */
public final class WeatherDetailDialog extends JDialog {

    private final QWeatherConfig config;
    private final String city;
    private final boolean autoIp;
    private final Runnable refreshCallback;
    private final WeatherDataService data = QWeatherService.dataService();
    private final RequestProcessor processor = new RequestProcessor("UnderStatus Weather Detail", 3, true);
    private final WeatherDetailStateCoordinator states = new WeatherDetailStateCoordinator();
    private final Map<String, Runnable> loaders = new HashMap<>();
    private final JLabel heading = new JLabel("天气详情 · 正在定位…");
    private final JLabel status = new JLabel(" ");
    private final JTabbedPane rootTabs = new JTabbedPane();
    private final WeatherChartPanel cityHourlyChart = new WeatherChartPanel(List.of());
    private final PrecipitationChartPanel precipitationChart = new PrecipitationChartPanel();
    private volatile LocationContext location;

    public WeatherDetailDialog(Window owner, String apiHost, String apiKey, String city,
            boolean autoIp, Runnable refreshCallback) {
        super(owner, "和风天气详情", ModalityType.MODELESS);
        this.config = QWeatherService.config(apiHost, apiKey);
        this.city = city;
        this.autoIp = autoIp;
        this.refreshCallback = refreshCallback;
        setSize(920, 620);
        setMinimumSize(new java.awt.Dimension(760, 500));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        buildUi();
        setupFocusDismiss();
        resolveLocation();
    }

    private void buildUi() {
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 10));
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 16f));
        header.add(heading, BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton refresh = new JButton("刷新");
        refresh.addActionListener(e -> refreshActive(refresh));
        JButton close = new JButton("关闭");
        close.addActionListener(e -> dispose());
        actions.add(refresh);
        actions.add(close);
        header.add(actions, BorderLayout.EAST);

        rootTabs.addTab("城市天气", cityTab());
        rootTabs.addTab("格点天气", gridTab());
        rootTabs.addTab("空气质量", simpleTab("air", this::airText));
        rootTabs.addTab("天气指数", indicesTab());
        rootTabs.addTab("分钟降水", minutelyTab());
        rootTabs.addTab("天气时光机", historicalTab());
        rootTabs.addChangeListener(e -> loadRootSelection());
        rootTabs.setEnabled(false);

        status.setBorder(BorderFactory.createEmptyBorder(5, 12, 7, 12));
        setLayout(new BorderLayout());
        add(header, BorderLayout.NORTH);
        add(rootTabs, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
    }

    private JPanel cityTab() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("实时", simpleTab("city-now", this::cityNowText));
        tabs.addTab("每日", rangedTab("city-daily", new String[]{"7天", "3天", "10天", "15天", "30天"},
                i -> cityDailyText(new ForecastRange[]{ForecastRange.DAYS_7, ForecastRange.DAYS_3,
                    ForecastRange.DAYS_10, ForecastRange.DAYS_15, ForecastRange.DAYS_30}[i])));
        tabs.addTab("逐小时", cityHourlyTab());
        tabs.addChangeListener(e -> runLoader(switch (tabs.getSelectedIndex()) {
            case 1 -> "city-daily";
            case 2 -> "city-hourly";
            default -> "city-now";
        }));
        loaders.put("city-root", () -> runLoader(switch (tabs.getSelectedIndex()) {
            case 1 -> "city-daily";
            case 2 -> "city-hourly";
            default -> "city-now";
        }));
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(tabs);
        return panel;
    }

    private JPanel gridTab() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("实时", simpleTab("grid-now", this::gridNowText));
        tabs.addTab("每日", rangedTab("grid-daily", new String[]{"3天", "7天"},
                i -> gridDailyText(i == 0 ? ForecastRange.DAYS_3 : ForecastRange.DAYS_7)));
        tabs.addTab("逐小时", rangedTab("grid-hourly", new String[]{"24小时", "72小时"},
                i -> gridHourlyText(i == 0 ? 24 : 72)));
        tabs.addChangeListener(e -> runLoader(switch (tabs.getSelectedIndex()) {
            case 1 -> "grid-daily";
            case 2 -> "grid-hourly";
            default -> "grid-now";
        }));
        loaders.put("grid-root", () -> runLoader(switch (tabs.getSelectedIndex()) {
            case 1 -> "grid-daily";
            case 2 -> "grid-hourly";
            default -> "grid-now";
        }));
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(tabs);
        return panel;
    }

    private JPanel indicesTab() {
        return rangedTab("indices", new String[]{"1天", "3天"},
                i -> indicesText(i == 0 ? WeatherIndexRange.ONE_DAY : WeatherIndexRange.THREE_DAYS));
    }

    private JPanel cityHourlyTab() {
        JTextArea area = outputArea();
        area.setRows(6);
        JComboBox<String> range = new JComboBox<>(new String[]{"24小时", "72小时", "168小时"});
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(new JLabel("范围："));
        controls.add(range);
        Runnable action = () -> {
            String key = "city-hourly-" + range.getSelectedIndex();
            activate(key);
            loaders.put(key, () -> load(key, area,
                    () -> cityHourlyText(new int[]{24, 72, 168}[range.getSelectedIndex()]), false));
            loaders.get(key).run();
        };
        loaders.put("city-hourly", action);
        range.addActionListener(e -> action.run());
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(controls, BorderLayout.NORTH);
        panel.add(new JScrollPane(cityHourlyChart), BorderLayout.CENTER);
        panel.add(new JScrollPane(area), BorderLayout.SOUTH);
        return panel;
    }

    private JPanel minutelyTab() {
        JTextArea area = outputArea();
        area.setRows(5);
        Runnable action = () -> load("minutely", area, this::minutelyText, false);
        loaders.put("minutely", action);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(precipitationChart, BorderLayout.CENTER);
        panel.add(new JScrollPane(area), BorderLayout.SOUTH);
        return panel;
    }

    private JPanel historicalTab() {
        String[] dates = new String[10];
        for (int i = 0; i < dates.length; i++) dates[i] = i == 0 ? "昨天" : (i + 1) + "天前";
        return rangedTab("historical", dates,
                i -> historicalText(LocalDate.now(location.zoneId()).minusDays(i + 1L)));
    }

    private JPanel simpleTab(String key, Callable<String> loader) {
        JTextArea area = outputArea();
        Runnable action = () -> load(key, area, loader, false);
        loaders.put(key, action);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JScrollPane(area));
        return panel;
    }

    private JPanel rangedTab(String baseKey, String[] choices, IndexedLoader loader) {
        JTextArea area = outputArea();
        JComboBox<String> range = new JComboBox<>(choices);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(new JLabel("范围："));
        controls.add(range);
        Runnable action = () -> {
            String key = baseKey + "-" + range.getSelectedIndex();
            activate(key);
            loaders.put(key, () -> load(key, area,
                    () -> loader.load(range.getSelectedIndex()), false));
            loaders.get(key).run();
        };
        loaders.put(baseKey, action);
        range.addActionListener(e -> action.run());
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(controls, BorderLayout.NORTH);
        panel.add(new JScrollPane(area), BorderLayout.CENTER);
        return panel;
    }

    private JTextArea outputArea() {
        JTextArea area = new JTextArea("选择标签后加载数据…");
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        return area;
    }

    private void resolveLocation() {
        processor.post(() -> {
            try {
                location = data.resolve(config, city, autoIp, false);
                SwingUtilities.invokeLater(() -> {
                    heading.setText(location.name() + " · 天气详情");
                    rootTabs.setEnabled(true);
                    runLoader("city-root");
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> status.setText(userMessage(ex)));
            }
        });
    }

    private void load(String key, JTextArea area, Callable<String> loader, boolean force) {
        if (location == null || (!force && !states.shouldLoad(key))) return;
        states.set(key, WeatherDetailStateCoordinator.State.LOADING,
                "正在获取和风天气数据…");
        area.setText("正在加载…");
        updateStatusIfActive(key);
        processor.post(() -> {
            try {
                String text = loader.call();
                SwingUtilities.invokeLater(() -> {
                    area.setText(text.isBlank() ? "当前地区暂无该数据" : text);
                    area.setCaretPosition(0);
                    boolean stale = text.contains("⚠ 数据可能已过期");
                    WeatherDetailStateCoordinator.State state = text.isBlank()
                            ? WeatherDetailStateCoordinator.State.EMPTY
                            : stale ? WeatherDetailStateCoordinator.State.STALE
                                    : WeatherDetailStateCoordinator.State.READY;
                    states.set(key, state, stale ? "⚠ 当前显示过期缓存，请稍后刷新"
                            : "数据来源：QWeather 和风天气");
                    updateStatusIfActive(key);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    WeatherDetailStateCoordinator.State state = ex instanceof WeatherException weather
                            && weather.kind() == WeatherException.Kind.UNSUPPORTED
                            ? WeatherDetailStateCoordinator.State.UNSUPPORTED
                            : WeatherDetailStateCoordinator.State.ERROR;
                    states.set(key, state, "加载失败 · 其他标签不受影响");
                    area.setText(userMessage(ex));
                    updateStatusIfActive(key);
                });
            }
        });
    }

    private void runLoader(String key) {
        activate(key);
        Runnable loader = loaders.get(key);
        if (loader != null) loader.run();
    }

    private void activate(String key) {
        states.activate(key);
        status.setText(states.activeStatus());
    }

    private void updateStatusIfActive(String key) {
        if (states.isActive(key)) status.setText(states.activeStatus());
    }

    private void loadRootSelection() {
        String key = switch (rootTabs.getSelectedIndex()) {
            case 0 -> "city-root";
            case 1 -> "grid-root";
            case 2 -> "air";
            case 3 -> "indices";
            case 4 -> "minutely";
            case 5 -> "historical";
            default -> "city-now";
        };
        runLoader(key);
    }

    private void refreshActive(JButton button) {
        button.setEnabled(false);
        data.clearLocation(location);
        String activeKey = states.activeKey();
        states.clear(activeKey);
        Runnable loader = loaders.get(activeKey);
        if (loader != null) loader.run();
        if (refreshCallback != null) refreshCallback.run();
        javax.swing.Timer timer = new javax.swing.Timer(1200, e -> button.setEnabled(true));
        timer.setRepeats(false);
        timer.start();
    }

    private String cityNowText() throws Exception {
        Result<WeatherNow> result = data.now(config, location, false);
        WeatherNow w = result.value();
        return "实时天气\n\n温度 " + w.temperatureCelsius() + "°C　体感 "
                + w.feelsLikeCelsius() + "°C　" + w.condition() + "\n湿度 "
                + w.humidityPercent() + "%　风向 " + w.windDirection() + " "
                + w.windScale() + "级　风速 " + w.windSpeedKph() + " km/h\n气压 "
                + w.pressureHpa() + " hPa　能见度 " + w.visibilityKm() + " km　降水 "
                + w.precipitationMm() + " mm\n" + reference(w.reference()) + stale(result);
    }

    private String cityDailyText(ForecastRange range) throws Exception {
        Result<List<DailyForecast>> result = data.daily(config, location, range, false);
        List<DailyForecast> values = result.value();
        StringBuilder text = new StringBuilder("每日天气预报\n\n");
        for (DailyForecast d : values) text.append(d.date()).append("　")
                .append(d.dayCondition()).append('/').append(d.nightCondition()).append("　")
                .append(d.minimumTemperatureCelsius()).append("~")
                .append(d.maximumTemperatureCelsius()).append("°C　降水 ")
                .append(d.precipitationMm()).append("mm\n");
        if (!values.isEmpty()) text.append('\n').append(reference(values.get(0).reference()));
        return text.append(stale(result)).toString();
    }

    private String cityHourlyText(int hours) throws Exception {
        Result<List<HourlyForecast>> result = data.hourly(config, location, hours, false);
        List<HourlyForecast> values = result.value();
        List<QWeatherService.HourlyForecast> chartValues = values.stream()
                .map(h -> new QWeatherService.HourlyForecast(
                        h.time().atZoneSameInstant(location.zoneId())
                                .format(DateTimeFormatter.ofPattern("HH:mm")),
                        h.temperatureCelsius(), h.condition())).toList();
        SwingUtilities.invokeLater(() -> cityHourlyChart.setForecasts(chartValues));
        StringBuilder text = new StringBuilder("逐小时天气预报\n\n");
        for (HourlyForecast h : values) text.append(h.time().atZoneSameInstant(location.zoneId())
                .format(DateTimeFormatter.ofPattern("MM-dd HH:mm")))
                .append("　").append(h.condition()).append("　")
                .append(h.temperatureCelsius()).append("°C　降水 ")
                .append(h.precipitationMm()).append("mm　").append(h.windDirection())
                .append(h.windScale()).append("级\n");
        if (!values.isEmpty()) text.append('\n').append(reference(values.get(0).reference()));
        return text.append(stale(result)).toString();
    }

    private String gridNowText() throws Exception {
        Result<GridWeatherNow> result = data.gridNow(config, location, false);
        GridWeatherNow w = result.value();
        return "格点实时天气　坐标 " + location.coordinate() + "\n\n温度 "
                + w.temperatureCelsius() + "°C　" + w.condition() + "\n湿度 "
                + w.humidityPercent() + "%　风向 " + w.windDirection() + " "
                + w.windScale() + "级　降水 " + w.precipitationMm() + "mm\n"
                + reference(w.reference()) + stale(result);
    }

    private String gridDailyText(ForecastRange range) throws Exception {
        Result<List<GridDailyForecast>> result = data.gridDaily(config, location, range, false);
        List<GridDailyForecast> values = result.value();
        StringBuilder text = new StringBuilder("格点每日预报　坐标 ").append(location.coordinate()).append("\n\n");
        for (GridDailyForecast d : values) text.append(d.date()).append("　")
                .append(d.dayCondition()).append('/').append(d.nightCondition()).append("　")
                .append(d.minimumTemperatureCelsius()).append("~")
                .append(d.maximumTemperatureCelsius()).append("°C\n");
        if (!values.isEmpty()) text.append('\n').append(reference(values.get(0).reference()));
        return text.append(stale(result)).toString();
    }

    private String gridHourlyText(int hours) throws Exception {
        Result<List<GridHourlyForecast>> result = data.gridHourly(config, location, hours, false);
        List<GridHourlyForecast> values = result.value();
        StringBuilder text = new StringBuilder("格点逐小时预报　坐标 ").append(location.coordinate()).append("\n\n");
        for (GridHourlyForecast h : values) text.append(h.time().atZoneSameInstant(location.zoneId())
                .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))).append("　")
                .append(h.condition()).append("　").append(h.temperatureCelsius())
                .append("°C　降水 ").append(h.precipitationMm()).append("mm\n");
        if (!values.isEmpty()) text.append('\n').append(reference(values.get(0).reference()));
        return text.append(stale(result)).toString();
    }

    private String airText() throws Exception {
        Result<AirQualitySnapshot> result = data.air(config, location, false);
        AirQualitySnapshot a = result.value();
        StringBuilder text = new StringBuilder("实时空气质量\n\nAQI ").append(a.aqi())
                .append("　").append(a.category()).append("\n主要污染物：")
                .append(a.primaryPollutant().isBlank() ? "无" : a.primaryPollutant()).append("\n\n");
        a.pollutants().forEach((key, value) -> text.append(key).append("　").append(value).append("\n"));
        if (!a.attributionTag().isBlank()) {
            text.append("\n许可标识：").append(a.attributionTag()).append('\n');
        }
        return text.append('\n').append(reference(a.reference())).append(stale(result)).toString();
    }

    private String indicesText(WeatherIndexRange range) throws Exception {
        Result<List<WeatherIndex>> result = data.indices(config, location, range, false);
        List<WeatherIndex> values = result.value();
        StringBuilder text = new StringBuilder("全部可用天气生活指数\n\n");
        for (String group : List.of("健康", "出行", "生活")) {
            text.append("【").append(group).append("】\n");
            for (WeatherIndex i : values) if (group.equals(indexGroup(i.type()))) {
                text.append(i.date()).append("　").append(i.name()).append("　")
                        .append(i.category()).append(" (等级 ").append(i.level())
                        .append(")\n").append(i.description()).append("\n\n");
            }
        }
        if (!values.isEmpty()) text.append(reference(values.get(0).reference()));
        return text.append(stale(result)).toString();
    }

    private String minutelyText() throws Exception {
        Result<MinutelyPrecipitation> result = data.minutely(config, location, false);
        MinutelyPrecipitation m = result.value();
        SwingUtilities.invokeLater(() -> precipitationChart.setPoints(m.points()));
        StringBuilder text = new StringBuilder("分钟级降水（未来2小时，每5分钟）\n\n")
                .append(m.summary()).append("\n\n");
        for (MinutelyPrecipitation.Point p : m.points()) text.append(p.time().format(
                DateTimeFormatter.ofPattern("HH:mm"))).append("　").append(p.type())
                .append("　").append(p.precipitationMm()).append("mm\n");
        return text.append('\n').append(reference(m.reference())).append(stale(result)).toString();
    }

    private String historicalText(LocalDate date) throws Exception {
        Result<HistoricalWeather> result = data.historical(config, location, date, false);
        HistoricalWeather h = result.value();
        HistoricalWeather.Daily d = h.daily();
        StringBuilder text = new StringBuilder("天气时光机　").append(date).append("\n\n")
                .append("最高/最低温：").append(d.maximumTemperatureCelsius()).append("/")
                .append(d.minimumTemperatureCelsius()).append("°C　湿度 ")
                .append(d.humidityPercent()).append("%　降水 ").append(d.precipitationMm())
                .append("mm\n日出/日落：").append(d.sunrise()).append("/").append(d.sunset())
                .append("　月相：").append(d.moonPhase()).append("\n\n逐小时\n");
        for (HistoricalWeather.Hourly hour : h.hourly()) text.append(hour.time().format(
                DateTimeFormatter.ofPattern("HH:mm"))).append("　").append(hour.condition())
                .append("　").append(hour.temperatureCelsius()).append("°C　降水 ")
                .append(hour.precipitationMm()).append("mm\n");
        return text.append('\n').append(reference(h.reference())).append(stale(result)).toString();
    }

    private static String reference(WeatherReference reference) {
        return "数据来源：" + String.join(", ", reference.sources()) + "\n许可："
                + String.join(", ", reference.licenses()) + "\n" + reference.fxLink();
    }

    private static String stale(Result<?> result) {
        return result.stale() ? "\n\n⚠ 数据可能已过期" : "";
    }

    private static String indexGroup(int type) {
        return switch (type) {
            case 1, 4, 6, 15 -> "出行";
            case 7, 8, 9, 10, 16 -> "健康";
            default -> "生活";
        };
    }

    private static String userMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? "天气数据加载失败" : message;
    }

    @Override public void dispose() {
        processor.stop();
        super.dispose();
    }

    private void setupFocusDismiss() {
        addWindowFocusListener(new WindowFocusListener() {
            @Override public void windowGainedFocus(WindowEvent e) {}
            @Override public void windowLostFocus(WindowEvent e) {
                Window opposite = e.getOppositeWindow();
                for (Window current = opposite; current != null; current = current.getOwner()) {
                    if (current == WeatherDetailDialog.this) return;
                }
                if (opposite != null) SwingUtilities.invokeLater(WeatherDetailDialog.this::dispose);
            }
        });
    }

    @FunctionalInterface
    private interface IndexedLoader { String load(int index) throws Exception; }
}
