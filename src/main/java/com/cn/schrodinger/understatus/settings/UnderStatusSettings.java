package com.cn.schrodinger.understatus.settings;

public record UnderStatusSettings(
        String clockPattern,
        int pomodoroWorkMinutes,
        int pomodoroBreakMinutes,
        boolean showClock,
        boolean showFormat,
        boolean showMemory,
        boolean showPomodoro,
        boolean showReadOnly,
        boolean showMetrics,
        boolean showToolbox,
        boolean showAlarms,
        boolean showWeather,
        String qweatherApiHost,
        String qweatherApiKey,
        String qweatherCity,
        boolean qweatherAutoIp,
        int toolboxWidth,
        int toolboxHeight) {

    public UnderStatusSettings {
        clockPattern = clockPattern == null || clockPattern.isBlank()
                ? SettingsRepository.DEFAULT_CLOCK_PATTERN : clockPattern;
        pomodoroWorkMinutes = Math.max(1, Math.min(180, pomodoroWorkMinutes));
        pomodoroBreakMinutes = Math.max(1, Math.min(60, pomodoroBreakMinutes));
        qweatherApiHost = qweatherApiHost == null ? "" : qweatherApiHost.trim();
        qweatherApiKey = qweatherApiKey == null ? "" : qweatherApiKey.trim();
        qweatherCity = qweatherCity == null || qweatherCity.isBlank() ? "北京" : qweatherCity.trim();
        toolboxWidth = Math.max(480, Math.min(3840, toolboxWidth));
        toolboxHeight = Math.max(360, Math.min(2160, toolboxHeight));
    }
}
