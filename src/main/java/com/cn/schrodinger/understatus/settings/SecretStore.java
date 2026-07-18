package com.cn.schrodinger.understatus.settings;

import java.util.Map;

public interface SecretStore {
    String read(String key);
    void write(String key, String value);
    void delete(String key);

    static SecretStore inMemory(Map<String, String> values) {
        return new SecretStore() {
            @Override public String read(String key) { return values.getOrDefault(key, ""); }
            @Override public void write(String key, String value) { values.put(key, value); }
            @Override public void delete(String key) { values.remove(key); }
        };
    }
}
