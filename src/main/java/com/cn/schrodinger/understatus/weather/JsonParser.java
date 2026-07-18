package com.cn.schrodinger.understatus.weather;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Strict, dependency-free JSON parser for QWeather API responses. */
public final class JsonParser {

    private JsonParser() {}

    public static JsonValue parse(String json) throws WeatherException {
        if (json == null) {
            throw error("响应为空");
        }
        Cursor cursor = new Cursor(json);
        JsonValue value = cursor.readValue();
        cursor.skipWhitespace();
        if (!cursor.atEnd()) {
            throw error("JSON 末尾存在多余内容");
        }
        return value;
    }

    private static WeatherException error(String message) {
        return new WeatherException(WeatherException.Kind.RESPONSE,
                "天气响应 JSON 无效: " + message);
    }

    private static final class Cursor {
        private final String input;
        private int position;

        Cursor(String input) {
            this.input = input;
        }

        JsonValue readValue() throws WeatherException {
            skipWhitespace();
            if (atEnd()) {
                throw error("缺少值");
            }
            return switch (input.charAt(position)) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> new JsonValue.StringValue(readString());
                case 't' -> readLiteral("true", new JsonValue.BooleanValue(true));
                case 'f' -> readLiteral("false", new JsonValue.BooleanValue(false));
                case 'n' -> readLiteral("null", JsonValue.NullValue.INSTANCE);
                default -> readNumber();
            };
        }

        private JsonValue readObject() throws WeatherException {
            position++;
            skipWhitespace();
            Map<String, JsonValue> values = new LinkedHashMap<>();
            if (consume('}')) {
                return new JsonValue.ObjectValue(values);
            }
            while (true) {
                skipWhitespace();
                if (atEnd() || input.charAt(position) != '"') {
                    throw error("对象键必须是字符串");
                }
                String key = readString();
                if (values.containsKey(key)) {
                    throw error("对象包含重复字段: " + key);
                }
                skipWhitespace();
                expect(':');
                values.put(key, readValue());
                skipWhitespace();
                if (consume('}')) {
                    return new JsonValue.ObjectValue(values);
                }
                expect(',');
            }
        }

        private JsonValue readArray() throws WeatherException {
            position++;
            skipWhitespace();
            List<JsonValue> values = new ArrayList<>();
            if (consume(']')) {
                return new JsonValue.ArrayValue(values);
            }
            while (true) {
                values.add(readValue());
                skipWhitespace();
                if (consume(']')) {
                    return new JsonValue.ArrayValue(values);
                }
                expect(',');
            }
        }

        private String readString() throws WeatherException {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (!atEnd()) {
                char value = input.charAt(position++);
                if (value == '"') {
                    return result.toString();
                }
                if (value == '\\') {
                    if (atEnd()) {
                        throw error("字符串转义不完整");
                    }
                    char escaped = input.charAt(position++);
                    switch (escaped) {
                        case '"', '\\', '/' -> result.append(escaped);
                        case 'b' -> result.append('\b');
                        case 'f' -> result.append('\f');
                        case 'n' -> result.append('\n');
                        case 'r' -> result.append('\r');
                        case 't' -> result.append('\t');
                        case 'u' -> result.append(readUnicode());
                        default -> throw error("不支持的字符串转义");
                    }
                } else {
                    if (value < 0x20) {
                        throw error("字符串包含控制字符");
                    }
                    result.append(value);
                }
            }
            throw error("字符串未结束");
        }

        private char readUnicode() throws WeatherException {
            if (position + 4 > input.length()) {
                throw error("Unicode 转义不完整");
            }
            String digits = input.substring(position, position + 4);
            position += 4;
            try {
                return (char) Integer.parseInt(digits, 16);
            } catch (NumberFormatException ex) {
                throw error("Unicode 转义无效");
            }
        }

        private JsonValue readNumber() throws WeatherException {
            int start = position;
            consume('-');
            if (atEnd()) {
                throw error("数字不完整");
            }
            if (consume('0')) {
                if (!atEnd() && Character.isDigit(input.charAt(position))) {
                    throw error("数字不能包含前导零");
                }
            } else {
                readDigits(true);
            }
            if (consume('.')) {
                readDigits(true);
            }
            if (!atEnd() && (input.charAt(position) == 'e' || input.charAt(position) == 'E')) {
                position++;
                if (!atEnd() && (input.charAt(position) == '+' || input.charAt(position) == '-')) {
                    position++;
                }
                readDigits(true);
            }
            try {
                return new JsonValue.NumberValue(new BigDecimal(input.substring(start, position)));
            } catch (NumberFormatException ex) {
                throw error("数字格式无效");
            }
        }

        private void readDigits(boolean required) throws WeatherException {
            int start = position;
            while (!atEnd() && Character.isDigit(input.charAt(position))) {
                position++;
            }
            if (required && start == position) {
                throw error("数字缺少数字位");
            }
        }

        private JsonValue readLiteral(String literal, JsonValue value) throws WeatherException {
            if (!input.startsWith(literal, position)) {
                throw error("未知字面量");
            }
            position += literal.length();
            return value;
        }

        private void expect(char expected) throws WeatherException {
            if (!consume(expected)) {
                throw error("缺少字符 " + expected);
            }
        }

        private boolean consume(char expected) {
            if (!atEnd() && input.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (!atEnd()) {
                char value = input.charAt(position);
                if (value == ' ' || value == '\n' || value == '\r' || value == '\t') {
                    position++;
                } else {
                    return;
                }
            }
        }

        private boolean atEnd() {
            return position >= input.length();
        }
    }
}
