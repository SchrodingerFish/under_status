package com.cn.schrodinger.understatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WeatherDetailStateCoordinatorTest {
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
