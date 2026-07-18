package com.cn.schrodinger.understatus.settings;

import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.HashMap;
import org.openide.util.NbPreferences;

public final class SettingsRepository {

    public static final String DEFAULT_CLOCK_PATTERN = "yyyy-MM-dd EEEE HH:mm:ss";
    private static final SettingsRepository DEFAULT = new SettingsRepository(
            new PreferencesSettingsStore(NbPreferences.forModule(SettingsRepository.class)),
            new KeyringSecretStore());
    private static final String WEATHER_SECRET = "com.cn.schrodinger.understatus.qweather.apiKey.v2";

    private final SettingsStore store;
    private final SecretStore secrets;

    public SettingsRepository(SettingsStore store) {
        this(store, SecretStore.inMemory(new HashMap<>()));
    }

    public SettingsRepository(SettingsStore store, SecretStore secrets) {
        this.store = Objects.requireNonNull(store);
        this.secrets = Objects.requireNonNull(secrets);
    }

    public static SettingsRepository getDefault() {
        return DEFAULT;
    }

    public UnderStatusSettings load() {
        return new UnderStatusSettings(
                validClockPattern(store.get("clockPattern", DEFAULT_CLOCK_PATTERN)),
                store.getInt("pomodoroWorkMinutes", 25),
                store.getInt("pomodoroBreakMinutes", 5),
                store.getBoolean("showClock", true),
                store.getBoolean("showFormat", true),
                store.getBoolean("showMemory", true),
                store.getBoolean("showPomodoro", true),
                store.getBoolean("showReadOnly", true),
                store.getBoolean("showMetrics", true),
                store.getBoolean("showNote", true),
                store.getBoolean("showAlarms", true),
                store.getBoolean("showWeather", false),
                store.get("qweatherApiHost", ""),
                secrets.read(WEATHER_SECRET),
                store.get("qweatherCity", "北京"),
                store.getBoolean("qweatherAutoIp", true),
                store.getInt("toolboxWidth", 800),
                store.getInt("toolboxHeight", 600));
    }

    public void save(UnderStatusSettings settings) {
        store.put("clockPattern", validClockPattern(settings.clockPattern()));
        store.putInt("pomodoroWorkMinutes", settings.pomodoroWorkMinutes());
        store.putInt("pomodoroBreakMinutes", settings.pomodoroBreakMinutes());
        store.putBoolean("showClock", settings.showClock());
        store.putBoolean("showFormat", settings.showFormat());
        store.putBoolean("showMemory", settings.showMemory());
        store.putBoolean("showPomodoro", settings.showPomodoro());
        store.putBoolean("showReadOnly", settings.showReadOnly());
        store.putBoolean("showMetrics", settings.showMetrics());
        store.putBoolean("showNote", settings.showToolbox());
        store.putBoolean("showAlarms", settings.showAlarms());
        store.putBoolean("showWeather", settings.showWeather());
        store.put("qweatherApiHost", settings.qweatherApiHost());
        if (settings.qweatherApiKey().isBlank()) secrets.delete(WEATHER_SECRET);
        else secrets.write(WEATHER_SECRET, settings.qweatherApiKey());
        store.put("qweatherCity", settings.qweatherCity());
        store.putBoolean("qweatherAutoIp", settings.qweatherAutoIp());
        store.putInt("toolboxWidth", settings.toolboxWidth());
        store.putInt("toolboxHeight", settings.toolboxHeight());
    }

    public String loadAlarms() { return store.get("alarmsList", ""); }
    public void saveAlarms(String alarms) { store.put("alarmsList", alarms == null ? "" : alarms); }
    public String loadNotes() { return store.get("notesListSerialized", ""); }
    public void saveNotes(String notes) { store.put("notesListSerialized", notes == null ? "" : notes); }

    private static String validClockPattern(String pattern) {
        try {
            DateTimeFormatter.ofPattern(pattern);
            return pattern;
        } catch (IllegalArgumentException ex) {
            return DEFAULT_CLOCK_PATTERN;
        }
    }
}
