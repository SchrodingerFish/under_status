package com.cn.schrodinger.understatus.weather;

import java.time.LocalDate;

public record WeatherIndex(LocalDate date, int type, String name, String level,
        String category, String description, WeatherReference reference) {}
