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

        @Override
        public String asString() {
            // When an object is queried as string (e.g. primaryPollutant: {"name":"PM2.5"}),
            // extract the most descriptive text field if available
            JsonValue name = values.get("name");
            if (name != null && name != NullValue.INSTANCE && !(name instanceof ObjectValue)) {
                try {
                    return name.asString();
                } catch (Exception ignored) {}
            }
            JsonValue code = values.get("code");
            if (code != null && code != NullValue.INSTANCE && !(code instanceof ObjectValue)) {
                try {
                    return code.asString();
                } catch (Exception ignored) {}
            }
            JsonValue fullName = values.get("fullName");
            if (fullName != null && fullName != NullValue.INSTANCE && !(fullName instanceof ObjectValue)) {
                try {
                    return fullName.asString();
                } catch (Exception ignored) {}
            }
            JsonValue val = values.get("value");
            if (val != null && val != NullValue.INSTANCE && !(val instanceof ObjectValue)) {
                try {
                    return val.asString();
                } catch (Exception ignored) {}
            }
            return "";
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

        @Override
        public String asString() {
            return "";
        }
    }

    record StringValue(String value) implements JsonValue {
        @Override
        public String asString() {
            return value;
        }

        @Override
        public double asDouble() throws WeatherException {
            try {
                return Double.parseDouble(value.trim());
            } catch (Exception ex) {
                throw new WeatherException(WeatherException.Kind.RESPONSE,
                        "天气响应 JSON 字符串不是有效数字: " + value, ex);
            }
        }

        @Override
        public int asInt() throws WeatherException {
            try {
                return (int) Math.round(Double.parseDouble(value.trim()));
            } catch (Exception ex) {
                throw new WeatherException(WeatherException.Kind.RESPONSE,
                        "天气响应 JSON 字符串不是有效整数: " + value, ex);
            }
        }

        @Override
        public boolean asBoolean() {
            return "true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim());
        }
    }

    record NumberValue(BigDecimal value) implements JsonValue {
        @Override
        public String asString() {
            return value.toPlainString();
        }

        @Override
        public int asInt() {
            return value.intValue();
        }

        @Override
        public double asDouble() {
            return value.doubleValue();
        }

        @Override
        public boolean asBoolean() {
            return value.signum() != 0;
        }
    }

    record BooleanValue(boolean value) implements JsonValue {
        @Override
        public String asString() {
            return Boolean.toString(value);
        }

        @Override
        public boolean asBoolean() {
            return value;
        }

        @Override
        public int asInt() {
            return value ? 1 : 0;
        }

        @Override
        public double asDouble() {
            return value ? 1.0 : 0.0;
        }
    }

    enum NullValue implements JsonValue {
        INSTANCE;

        @Override
        public String asString() {
            return "";
        }

        @Override
        public int asInt() {
            return 0;
        }

        @Override
        public double asDouble() {
            return 0.0;
        }

        @Override
        public boolean asBoolean() {
            return false;
        }
    }
}
