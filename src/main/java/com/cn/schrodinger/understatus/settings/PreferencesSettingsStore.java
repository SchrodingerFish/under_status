package com.cn.schrodinger.understatus.settings;

import java.util.Objects;
import java.util.prefs.Preferences;

public final class PreferencesSettingsStore implements SettingsStore {

    private final Preferences preferences;

    public PreferencesSettingsStore(Preferences preferences) {
        this.preferences = Objects.requireNonNull(preferences);
    }

    @Override public String get(String key, String defaultValue) { return preferences.get(key, defaultValue); }
    @Override public int getInt(String key, int defaultValue) { return preferences.getInt(key, defaultValue); }
    @Override public boolean getBoolean(String key, boolean defaultValue) { return preferences.getBoolean(key, defaultValue); }
    @Override public void put(String key, String value) { preferences.put(key, value); }
    @Override public void putInt(String key, int value) { preferences.putInt(key, value); }
    @Override public void putBoolean(String key, boolean value) { preferences.putBoolean(key, value); }
}
