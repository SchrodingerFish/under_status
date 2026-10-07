package com.cn.schrodinger.understatus.toolbox.core;

import com.cn.schrodinger.understatus.core.JsonSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;

/** Strict JSON formatting and JSON string escaping. */
public final class JsonFormatter {
    private JsonFormatter() {}

    public static String format(String json) {
        ToolLimits.input(json);
        return ToolLimits.output(JsonSupport.write(JsonSupport.parse(json), true));
    }

    public static String minify(String json) {
        ToolLimits.input(json);
        return ToolLimits.output(JsonSupport.write(JsonSupport.parse(json), false));
    }

    public static String escape(String text) {
        if (text == null) return "";
        ToolLimits.input(text);
        String quoted = JsonSupport.write(TextNode.valueOf(text), false);
        return ToolLimits.output(quoted.substring(1, quoted.length() - 1));
    }

    public static String unescape(String text) {
        if (text == null) return "";
        ToolLimits.input(text);
        String value = text;
        if (!value.startsWith("\"")) value = "\"" + value + "\"";
        JsonNode node = JsonSupport.parse(value);
        if (!node.isTextual()) throw new IllegalArgumentException("Expected a JSON string");
        return node.textValue();
    }
}
