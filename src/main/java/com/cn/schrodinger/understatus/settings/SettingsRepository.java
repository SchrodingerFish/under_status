package com.cn.schrodinger.understatus.settings;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Objects;
import java.nio.file.Path;
import org.openide.util.NbPreferences;

public final class SettingsRepository {

    public static final String DEFAULT_CLOCK_PATTERN = "yyyy-MM-dd EEEE HH:mm:ss";
    private static final class DefaultHolder {
        private static final SettingsRepository INSTANCE = new SettingsRepository(
                new PreferencesSettingsStore(NbPreferences.forModule(SettingsRepository.class)),
                new KeyringSecretStore(), new FileContentStore(contentDirectory()));
    }
    private static final String WEATHER_SECRET = "com.cn.schrodinger.understatus.qweather.apiKey.v2";

    private final SettingsStore store;
    private final SecretStore secrets;
    private final ContentStore content;

    public SettingsRepository(SettingsStore store) {
        this(store, SecretStore.inMemory(new HashMap<>()));
    }

    public SettingsRepository(SettingsStore store, SecretStore secrets) {
        this(store, secrets, null);
    }

    public SettingsRepository(SettingsStore store, SecretStore secrets, ContentStore content) {
        this.store = Objects.requireNonNull(store);
        this.secrets = Objects.requireNonNull(secrets);
        this.content = content;
    }

    public static SettingsRepository getDefault() {
        return DefaultHolder.INSTANCE;
    }

    public String loadMusicApiHost() { return store.get("musicApiHost", ""); }

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
                store.getBoolean("showMusic", true),
                store.get("musicApiHost", ""),
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
        store.putBoolean("showMusic", settings.showMusic());
        store.put("musicApiHost", settings.musicApiHost());
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
    public String loadNotes() { return loadNotesResult().value(); }
    public ContentStore.ReadResult loadNotesResult() { return loadContent("notes", "notesListSerialized"); }
    public void saveNotes(String notes) { saveContent("notes", "notesListSerialized", notes); }
    public String loadFavorites() { return loadFavoritesResult().value(); }
    public ContentStore.ReadResult loadFavoritesResult() { return loadContent("favorites", "musicFavoritesSerialized"); }
    public void saveFavorites(String favorites) { saveContent("favorites", "musicFavoritesSerialized", favorites); }
    /** @deprecated Use loadNotesResult/loadFavoritesResult to associate warnings with their payload. */
    @Deprecated
    public String contentWarning() { return content == null ? "" : content.warning(); }

    private synchronized ContentStore.ReadResult loadContent(String key, String legacyKey) {
        if (content == null) return new ContentStore.ReadResult(store.get(legacyKey, ""), "");
        var saved = content.readResult(key);
        if (saved.isPresent()) return saved.get();
        String legacy = store.get(legacyKey, "");
        // Preserve the original preference as a migration backup.
        if (!legacy.isEmpty()) content.write(key, legacy);
        return new ContentStore.ReadResult(legacy, "");
    }

    private synchronized void saveContent(String key, String legacyKey, String value) {
        String text = value == null ? "" : value;
        if (content == null) store.put(legacyKey, text);
        else content.write(key, text);
    }

    private static Path contentDirectory() {
        String userDirectory = System.getProperty("netbeans.user");
        return userDirectory == null || userDirectory.isBlank()
                ? Path.of(System.getProperty("user.home"), ".understatus", "content")
                : Path.of(userDirectory, "config", "understatus", "content");
    }

    private static String validClockPattern(String pattern) {
        try {
            DateTimeFormatter.ofPattern(pattern);
            return pattern;
        } catch (IllegalArgumentException ex) {
            return DEFAULT_CLOCK_PATTERN;
        }
    }
}
