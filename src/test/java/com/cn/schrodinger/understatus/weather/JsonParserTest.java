package com.cn.schrodinger.understatus.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonParserTest {

    @Test
    void parsesNestedObjectsArraysEscapesAndNumbers() throws Exception {
        JsonValue root = JsonParser.parse(
                "{\"now\":{\"temp\":\"31\",\"text\":\"晴\\n天\"},\"items\":[1,true,null]}");

        assertEquals("31", root.asObject().required("now").asObject()
                .required("temp").asString());
        assertEquals("晴\n天", root.asObject().required("now").asObject()
                .required("text").asString());
        assertEquals(3, root.asObject().required("items").asArray().size());
        assertEquals(1, root.asObject().required("items").asArray().get(0).asInt());
        assertTrue(root.asObject().required("items").asArray().get(1).asBoolean());
    }

    @Test
    void parsesIntegerDecimalAndExponentNumbers() throws Exception {
        JsonValue root = JsonParser.parse(
                "{\"integer\":-12,\"decimal\":1.25,\"exponent\":6.02e2}");

        assertEquals(-12, root.asObject().required("integer").asInt());
        assertEquals(1.25, root.asObject().required("decimal").asDouble());
        assertEquals(602.0, root.asObject().required("exponent").asDouble());
    }

    @Test
    void rejectsInvalidJsonAndHostWithPath() {
        WeatherException error = assertThrows(
                WeatherException.class, () -> JsonParser.parse("{bad"));

        assertEquals(WeatherException.Kind.RESPONSE, error.kind());
        assertThrows(IllegalArgumentException.class,
                () -> new QWeatherConfig(
                        "https://abc.def.qweatherapi.com/path", "key", "zh", "m"));
    }

    @Test
    void rejectsUnterminatedStrings() {
        assertResponseError("{\"text\":\"unfinished}");
    }

    @Test
    void rejectsInvalidEscapes() {
        assertResponseError("{\"text\":\"bad\\xescape\"}");
    }

    @Test
    void rejectsMissingCommas() {
        assertResponseError("{\"first\":1 \"second\":2}");
        assertResponseError("[1 2]");
    }

    @Test
    void rejectsTrailingInput() {
        assertResponseError("true false");
    }

    @Test
    void rejectsDuplicateObjectKeys() {
        assertResponseError("{\"code\":\"200\",\"code\":\"401\"}");
    }

    @Test
    void returnsEmptyForMissingOptionalFields() throws Exception {
        JsonValue root = JsonParser.parse("{\"code\":\"200\"}");

        assertTrue(root.asObject().optional("missing").isEmpty());
    }

    @Test
    void rejectsMissingRequiredFields() throws Exception {
        JsonValue root = JsonParser.parse("{\"code\":\"200\"}");

        WeatherException error = assertThrows(
                WeatherException.class, () -> root.asObject().required("missing"));
        assertEquals(WeatherException.Kind.RESPONSE, error.kind());
    }

    @Test
    void buildsHttpsEndpointWithEncodedQueryAndWithoutApiKey() {
        QWeatherConfig config = new QWeatherConfig(
                " abc.def.qweatherapi.com ", " secret ", " zh ", " m ");

        URI endpoint = config.endpoint("/v7/weather/now",
                Map.of("location", "上海 浦东", "lang", "zh"));

        assertEquals("https", endpoint.getScheme());
        assertEquals("abc.def.qweatherapi.com", endpoint.getHost());
        assertEquals("/v7/weather/now", endpoint.getPath());
        assertEquals("lang=zh&location=%E4%B8%8A%E6%B5%B7%20%E6%B5%A6%E4%B8%9C",
                endpoint.getRawQuery());
        assertFalse(endpoint.toString().contains("secret"));
        assertEquals("secret", config.apiKey());
        assertEquals("zh", config.language());
        assertEquals("m", config.unit());
    }

    @Test
    void rejectsHostsOutsideQWeatherApiDomain() {
        assertThrows(IllegalArgumentException.class,
                () -> new QWeatherConfig("example.com", "key", "zh", "m"));
        assertThrows(IllegalArgumentException.class,
                () -> new QWeatherConfig("abc.def.qweatherapi.com:443", "key", "zh", "m"));
    }

    private static void assertResponseError(String json) {
        WeatherException error = assertThrows(
                WeatherException.class, () -> JsonParser.parse(json));
        assertEquals(WeatherException.Kind.RESPONSE, error.kind());
    }
}
