package com.cn.schrodinger.understatus.core;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/** Shared, bounded JSON codec independent of any service or UI. */
public final class JsonSupport {
    public static final int MAX_INPUT_LENGTH = 1_000_000;
    /** Absolute decimal scale limit before consumers can expand or convert numbers. */
    public static final int MAX_DECIMAL_SCALE = 1024;
    private static final ObjectMapper MAPPER = JsonMapper.builder(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .streamReadConstraints(StreamReadConstraints.builder()
                    .maxNestingDepth(128).maxStringLength(MAX_INPUT_LENGTH).maxNumberLength(1000).build())
            .build()).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    private JsonSupport() {}

    public static JsonNode parse(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("JSON 内容不能为空");
        if (text.length() > MAX_INPUT_LENGTH) throw new IllegalArgumentException("JSON 超过 1,000,000 字符限制");
        try {
            JsonNode result = MAPPER.readTree(text);
            validateDecimalScales(result);
            return result;
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("JSON 格式无效: " + ex.getOriginalMessage(), ex);
        }
    }

    private static void validateDecimalScales(JsonNode value) {
        if (value.isFloatingPointNumber()
                && Math.abs((long) value.decimalValue().scale()) > MAX_DECIMAL_SCALE) {
            throw new IllegalArgumentException("JSON 数字小数位/指数超过 1024 限制");
        }
        if (value.isContainerNode()) {
            for (JsonNode child : value) validateDecimalScales(child);
        }
    }

    public static String write(JsonNode value, boolean pretty) {
        try {
            return pretty ? MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value)
                    : MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("无法生成 JSON", ex);
        }
    }
}
