package com.cn.schrodinger.understatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WeatherDetailStateCoordinatorTest {
    @Test void delayedOldRangeCannotCompleteLatestSelection() {
        WeatherDetailStateCoordinator states = new WeatherDetailStateCoordinator();
        var older = states.begin("daily", "daily-7");
        var latest = states.begin("daily", "daily-3");
        assertTrue(states.complete(latest, WeatherDetailStateCoordinator.State.READY, "three days"));
        assertFalse(states.complete(older, WeatherDetailStateCoordinator.State.STALE, "seven days"));
        assertEquals(WeatherDetailStateCoordinator.State.READY, states.state("daily-3"));
        assertTrue(states.shouldLoad("daily-7"));
    }

    @Test void repeatedRefreshAcceptsOnlyLatestAndRetainsLoadingUntilCompletion() {
        WeatherDetailStateCoordinator states = new WeatherDetailStateCoordinator();
        var older = states.begin("now", "now");
        var latest = states.begin("now", "now");
        assertFalse(states.complete(older, WeatherDetailStateCoordinator.State.ERROR, "old failure"));
        assertEquals(WeatherDetailStateCoordinator.State.LOADING, states.state("now"));
        assertTrue(states.complete(latest, WeatherDetailStateCoordinator.State.STALE, "cached"));
        assertEquals(WeatherDetailStateCoordinator.State.STALE, states.state("now"));
    }

    @Test void selectingCachedRangeAndClosingSuppressQueuedCompletions() {
        WeatherDetailStateCoordinator states = new WeatherDetailStateCoordinator();
        var older = states.begin("hourly", "hourly-24");
        states.select("hourly", "hourly-72");
        assertFalse(states.accepts(older));
        var latest = states.begin("hourly", "hourly-72");
        var independent = states.begin("air", "air");
        assertTrue(states.accepts(latest));
        assertTrue(states.accepts(independent));
        states.close();
        assertFalse(states.complete(latest, WeatherDetailStateCoordinator.State.READY, "late"));
        assertFalse(states.complete(independent, WeatherDetailStateCoordinator.State.ERROR, "late"));
        assertTrue(states.isClosed());
    }
    @Test void tracksTabsIndependentlyAndCanRefreshOne() {
        WeatherDetailStateCoordinator states = new WeatherDetailStateCoordinator();
        assertTrue(states.shouldLoad("city-now"));
        states.set("city-now", WeatherDetailStateCoordinator.State.READY);
        states.set("air", WeatherDetailStateCoordinator.State.ERROR);
        assertFalse(states.shouldLoad("city-now"));
        assertEquals(WeatherDetailStateCoordinator.State.ERROR, states.state("air"));
        states.clear("city-now");
        assertTrue(states.shouldLoad("city-now"));
    }

    @Test void restoresStatusWhenReturningToLoadedTab() {
        WeatherDetailStateCoordinator states = new WeatherDetailStateCoordinator();
        states.activate("city-now");
        states.set("city-now", WeatherDetailStateCoordinator.State.READY,
                "数据来源：QWeather 和风天气");
        states.activate("air");
        states.set("air", WeatherDetailStateCoordinator.State.LOADING,
                "正在获取和风天气数据…");

        states.activate("city-now");

        assertEquals("数据来源：QWeather 和风天气", states.activeStatus());
    }

    @Test void keepsActiveStatusWhenAnotherTabCompletesLate() {
        WeatherDetailStateCoordinator states = new WeatherDetailStateCoordinator();
        states.activate("city-now");
        states.set("city-now", WeatherDetailStateCoordinator.State.LOADING,
                "正在获取和风天气数据…");
        states.activate("air");
        states.set("air", WeatherDetailStateCoordinator.State.LOADING,
                "正在获取和风天气数据…");

        states.set("city-now", WeatherDetailStateCoordinator.State.READY,
                "数据来源：QWeather 和风天气");

        assertFalse(states.isActive("city-now"));
        assertEquals("正在获取和风天气数据…", states.activeStatus());
    }
}
