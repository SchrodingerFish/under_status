package com.cn.schrodinger.understatus.settings;

import java.util.function.Supplier;

/** Storage for user documents, separate from small preference values. */
public interface ContentStore {
    String load(String key, Supplier<String> legacy);
    void save(String key, String value);
}
