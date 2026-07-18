package com.cn.schrodinger.understatus.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WeatherDisplayPolicyTest {
    @Test void enabledWithoutHostOrKeyRemainsVisible() {
        WeatherDisplayState state = WeatherDisplayPolicy.evaluate(false, "", "", "北京", true,
                true, "", "", "北京", true);
        assertTrue(state.visible());
        assertEquals("🌤 天气：待配置", state.text());
        assertFalse(state.refreshImmediately());
    }

    @Test void completeConfigurationRefreshesImmediately() {
        WeatherDisplayState state = WeatherDisplayPolicy.evaluate(true, "", "", "北京", true,
                true, "abc.def.qweatherapi.com", "key", "北京", true);
        assertTrue(state.refreshImmediately());
        assertEquals("🌤 正在加载天气…", state.text());
    }

    @Test void hostKeyCityAndLocationChangesRefresh() {
        assertTrue(state("old.example", "key", "北京", false,
                "abc.def.qweatherapi.com", "key", "北京", false).refreshImmediately());
        assertTrue(state("abc.def.qweatherapi.com", "old", "北京", false,
                "abc.def.qweatherapi.com", "new", "北京", false).refreshImmediately());
        assertTrue(state("abc.def.qweatherapi.com", "key", "北京", false,
                "abc.def.qweatherapi.com", "key", "上海", true).refreshImmediately());
    }

    private static WeatherDisplayState state(String oldHost, String oldKey, String oldCity,
            boolean oldAuto, String host, String key, String city, boolean autoIp) {
        return WeatherDisplayPolicy.evaluate(true, oldHost, oldKey, oldCity, oldAuto,
                true, host, key, city, autoIp);
    }
}
