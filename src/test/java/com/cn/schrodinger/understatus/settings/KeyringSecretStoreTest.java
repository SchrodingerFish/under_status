package com.cn.schrodinger.understatus.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class KeyringSecretStoreTest {
    @Test
    void savedSecretCanBeReadImmediately() {
        KeyringSecretStore store = new KeyringSecretStore();
        String key = "understatus.test." + UUID.randomUUID();

        try {
            store.write(key, "weather-secret");

            assertEquals("weather-secret", store.read(key));
        } finally {
            store.delete(key);
        }
    }
}
