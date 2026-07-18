package com.cn.schrodinger.understatus.settings;

import java.util.Map;

public interface SettingsStore {

    String get(String key, String defaultValue);

    int getInt(String key, int defaultValue);

    boolean getBoolean(String key, boolean defaultValue);

    void put(String key, String value);

    void putInt(String key, int value);

    void putBoolean(String key, boolean value);

    static SettingsStore inMemory(Map<String, String> values) {
        return new SettingsStore() {
            @Override
            public String get(String key, String defaultValue) {
                return values.getOrDefault(key, defaultValue);
            }

            @Override
            public int getInt(String key, int defaultValue) {
                try {
                    return Integer.parseInt(values.get(key));
                } catch (RuntimeException ex) {
                    return defaultValue;
                }
            }

            @Override
            public boolean getBoolean(String key, boolean defaultValue) {
                String value = values.get(key);
                return value == null ? defaultValue : Boolean.parseBoolean(value);
            }

            @Override
            public void put(String key, String value) {
                values.put(key, value);
            }

            @Override
            public void putInt(String key, int value) {
                values.put(key, Integer.toString(value));
            }

            @Override
            public void putBoolean(String key, boolean value) {
                values.put(key, Boolean.toString(value));
            }
        };
    }
}
