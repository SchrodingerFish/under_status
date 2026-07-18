package com.cn.schrodinger.understatus.settings;

import java.util.Arrays;
import org.netbeans.api.keyring.Keyring;

public final class KeyringSecretStore implements SecretStore {
    @Override
    public String read(String key) {
        char[] value = Keyring.read(key);
        if (value == null) return "";
        try {
            return new String(value);
        } finally {
            Arrays.fill(value, '\0');
        }
    }

    @Override
    public void write(String key, String value) {
        Keyring.save(key, value.toCharArray(), "UnderStatus QWeather API Key");
    }

    @Override public void delete(String key) { Keyring.delete(key); }
}
