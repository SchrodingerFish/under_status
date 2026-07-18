package com.cn.schrodinger.understatus.weather;

import java.util.List;

public record WeatherReference(String fxLink, List<String> sources, List<String> licenses) {
    public static final WeatherReference EMPTY = new WeatherReference("", List.of(), List.of());

    public WeatherReference {
        fxLink = fxLink == null ? "" : fxLink;
        sources = sources == null ? List.of() : List.copyOf(sources);
        licenses = licenses == null ? List.of() : List.copyOf(licenses);
    }
}
