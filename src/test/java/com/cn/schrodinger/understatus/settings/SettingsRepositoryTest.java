package com.cn.schrodinger.understatus.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import org.junit.jupiter.api.Test;

class SettingsRepositoryTest {

    @Test
    void loadsDocumentedDefaultsFromAnEmptyStore() {
        SettingsRepository repository = new SettingsRepository(SettingsStore.inMemory(new HashMap<>()));

        UnderStatusSettings settings = repository.load();

        assertEquals("yyyy-MM-dd EEEE HH:mm:ss", settings.clockPattern());
        assertEquals(25, settings.pomodoroWorkMinutes());
        assertEquals(5, settings.pomodoroBreakMinutes());
        assertEquals(800, settings.toolboxWidth());
        assertEquals(600, settings.toolboxHeight());
        assertFalse(settings.showWeather());
    }

    @Test
    void clampsInvalidNumericValues() {
        HashMap<String, String> values = new HashMap<>();
        values.put("pomodoroWorkMinutes", "0");
        values.put("toolboxWidth", "100");

        UnderStatusSettings settings = new SettingsRepository(SettingsStore.inMemory(values)).load();

        assertEquals(1, settings.pomodoroWorkMinutes());
        assertEquals(480, settings.toolboxWidth());
    }

    @Test
    void storesHostInPreferencesAndApiKeyOnlyInSecretStore() {
        HashMap<String, String> values = new HashMap<>();
        HashMap<String, String> secretValues = new HashMap<>();
        SettingsRepository repository = new SettingsRepository(
                SettingsStore.inMemory(values), SecretStore.inMemory(secretValues));
        UnderStatusSettings defaults = repository.load();

        repository.save(new UnderStatusSettings(defaults.clockPattern(), 25, 5,
                true, true, true, true, true, true, true, true, true,
                true, "", "abc.def.qweatherapi.com", "secret", "上海", false, 800, 600));

        assertEquals("abc.def.qweatherapi.com", values.get("qweatherApiHost"));
        assertNull(values.get("qweatherApiKey"));
        assertFalse(values.containsKey("qweather" + "ProjectId"));
        assertEquals("secret", repository.load().qweatherApiKey());

        repository.saveFavorites("test_favorites");
        assertEquals("test_favorites", repository.loadFavorites());
    }
}
