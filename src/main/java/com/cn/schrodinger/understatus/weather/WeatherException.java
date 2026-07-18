package com.cn.schrodinger.understatus.weather;

public final class WeatherException extends Exception {

    public enum Kind {
        CONFIGURATION,
        INVALID_REQUEST,
        AUTHENTICATION,
        FORBIDDEN,
        NOT_FOUND,
        RATE_LIMIT,
        UNSUPPORTED,
        TIMEOUT,
        NETWORK,
        UNAVAILABLE,
        RESPONSE
    }

    private final Kind kind;
    private final int httpStatus;

    public WeatherException(Kind kind, String message) {
        this(kind, message, -1, null);
    }

    public WeatherException(Kind kind, String message, Throwable cause) {
        this(kind, message, -1, cause);
    }

    public WeatherException(Kind kind, String message, int httpStatus) {
        this(kind, message, httpStatus, null);
    }

    private WeatherException(Kind kind, String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.httpStatus = httpStatus;
    }

    public Kind kind() { return kind; }

    public int httpStatus() { return httpStatus; }
}
