package com.cn.schrodinger.understatus.weather;

import java.net.URI;
import java.util.Map;

@FunctionalInterface
public interface HttpTransport {
    String get(URI uri) throws Exception;

    default String get(URI uri, Map<String, String> headers) throws Exception {
        return get(uri);
    }
}
