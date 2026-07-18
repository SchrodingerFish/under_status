package com.cn.schrodinger.understatus.weather;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Immutable JSON value tree used by the QWeather response mappers. */
public sealed interface JsonValue permits JsonValue.ObjectValue, JsonValue.ArrayValue,
        JsonValue.StringValue, JsonValue.NumberValue, JsonValue.BooleanValue,
        JsonValue.NullValue {

    default ObjectValue asObject() throws WeatherException {
        throw typeError("对象");
    }

    default List<JsonValue> asArray() throws WeatherException {
        throw typeError("数组");
    }

    default String asString() throws WeatherException {
        throw typeError("字符串");
    }

    default int asInt() throws WeatherException {
        throw typeError("整数");
    }

    default double asDouble() throws WeatherException {
        throw typeError("数字");
    }

    default boolean asBoolean() throws WeatherException {
        throw typeError("布尔值");
    }

    private WeatherException typeError(String expected) {
        return new WeatherException(WeatherException.Kind.RESPONSE,
                "天气响应 JSON 字段类型错误，预期" + expected);
    }

    final class ObjectValue implements JsonValue {
        private final Map<String, JsonValue> values;

        ObjectValue(Map<String, JsonValue> values) {
            this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }

        @Override
        public ObjectValue asObject() {
            return this;
        }

        public Optional<JsonValue> optional(String key) {
            return Optional.ofNullable(values.get(key));
        }

        public JsonValue required(String key) throws WeatherException {
            JsonValue value = values.get(key);
            if (value == null) {
                throw new WeatherException(WeatherException.Kind.RESPONSE,
                        "天气响应缺少字段: " + key);
            }
            return value;
        }

        public Map<String, JsonValue> values() {
            return values;
        }
    }

    final class ArrayValue implements JsonValue {
        private final List<JsonValue> values;

        ArrayValue(List<JsonValue> values) {
            this.values = List.copyOf(values);
        }

        @Override
        public List<JsonValue> asArray() {
            return values;
        }
    }

    record StringValue(String value) implements JsonValue {
        @Override
        public String asString() {
            return value;
        }
    }

    record NumberValue(BigDecimal value) implements JsonValue {
        @Override
        public int asInt() throws WeatherException {
            try {
                return value.intValueExact();
            } catch (ArithmeticException ex) {
                throw new WeatherException(WeatherException.Kind.RESPONSE,
                        "天气响应 JSON 数字不是有效整数", ex);
            }
        }

        @Override
        public double asDouble() {
            return value.doubleValue();
        }
    }

    record BooleanValue(boolean value) implements JsonValue {
        @Override
        public boolean asBoolean() {
            return value;
        }
    }

    enum NullValue implements JsonValue {
        INSTANCE
    }
}
