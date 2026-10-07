package com.cn.schrodinger.understatus.toolbox.core;

import com.cn.schrodinger.understatus.core.JsonSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;

/** Inspects compact JWT claims. This utility does not verify signatures or trust. */
public final class JwtDecoder {
    private static final BigDecimal MIN_DATE = BigDecimal.valueOf(Instant.MIN.getEpochSecond());
    private static final BigDecimal MAX_DATE = BigDecimal.valueOf(Instant.MAX.getEpochSecond())
            .add(new BigDecimal("0.999999999"));
    private JwtDecoder() {}

    public static String decodeJwt(String token) { return decodeJwt(token, Clock.systemUTC()); }

    public static String decodeJwt(String token, Clock clock) {
        if (token == null || token.isBlank()) return "";
        try {
            ToolLimits.input(token);
            String[] parts = token.trim().split("\\.", -1);
            if (parts.length != 3 || parts[0].isEmpty() || parts[1].isEmpty()) {
                return "无效的 JWT 格式: Expected three compact JWT segments";
            }
            JsonNode header = decodeObject(parts[0]);
            JsonNode payload = decodeObject(parts[1]);
            if (!parts[2].isEmpty()) decodeBase64(parts[2]);
            Instant exp = numericDate(payload, "exp");
            Instant nbf = numericDate(payload, "nbf");
            Instant iat = numericDate(payload, "iat");
            Instant now = clock.instant();
            StringBuilder out = new StringBuilder("JWT 诊断: 未验证签名 (Signature unverified)，不能据此判断令牌可信或有效。\n");
            out.append(parts[2].isEmpty() ? "签名: 无签名 (Unsigned)\n" : "签名: 已附带，未验证\n");
            if (exp == null) out.append("过期状态: 未包含 exp，过期时间未知。\n");
            else if (!now.isBefore(exp)) out.append("过期状态: 已过期。\n");
            else out.append("过期状态: 尚未到 exp 时间。\n");
            if (nbf != null && now.isBefore(nbf)) out.append("生效状态: 尚未生效 (nbf 位于未来)。\n");
            appendText(out, header, "alg");
            appendText(out, header, "typ");
            appendText(out, payload, "sub");
            appendText(out, payload, "iss");
            JsonNode audience = payload.get("aud");
            if (audience != null) {
                if (audience.isArray()) {
                    for (JsonNode value : audience) if (!value.isTextual()) throw new IllegalArgumentException("aud must contain strings");
                } else if (!audience.isTextual()) throw new IllegalArgumentException("aud must be a string or string array");
                out.append("aud: ").append(audience).append('\n');
            }
            if (iat != null) out.append("iat: ").append(iat).append('\n');
            if (nbf != null) out.append("nbf: ").append(nbf).append('\n');
            if (exp != null) out.append("exp: ").append(exp).append('\n');
            out.append("\nHEADER\n").append(JsonSupport.write(header, true));
            out.append("\n\nPAYLOAD\n").append(JsonSupport.write(payload, true));
            return ToolLimits.output(out.toString());
        } catch (Exception ex) {
            return "JWT 解码失败 (Decoding failed): " + ex.getMessage();
        }
    }

    private static byte[] decodeBase64(String segment) {
        if (!segment.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("Expected unpadded Base64URL segment");
        return Base64.getUrlDecoder().decode(segment);
    }

    private static JsonNode decodeObject(String segment) throws java.nio.charset.CharacterCodingException {
        String json = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(decodeBase64(segment))).toString();
        JsonNode value = JsonSupport.parse(json);
        if (!value.isObject()) throw new IllegalArgumentException("JWT header/payload must be JSON objects");
        return value;
    }

    private static void appendText(StringBuilder out, JsonNode object, String name) {
        JsonNode value = object.get(name);
        if (value == null) return;
        if (!value.isTextual()) throw new IllegalArgumentException(name + " must be a string");
        out.append(name).append(": ").append(value.textValue()).append('\n');
    }

    private static Instant numericDate(JsonNode object, String name) {
        JsonNode value = object.get(name);
        if (value == null) return null;
        if (!value.isNumber()) throw new IllegalArgumentException(name + " must be a numeric date");
        try {
            BigDecimal seconds = value.decimalValue().stripTrailingZeros();
            // Inspect scale and magnitude before rounding can construct a power
            // of ten. Supported Instants have at most 17 integer digits and 9
            // fractional digits; trailing zeroes do not consume precision.
            if (seconds.scale() > 9 || (long) seconds.precision() - seconds.scale() > 17
                    || seconds.compareTo(MIN_DATE) < 0 || seconds.compareTo(MAX_DATE) > 0) {
                throw new IllegalArgumentException("Numeric date exceeds Instant range or nanosecond precision");
            }
            long whole = seconds.setScale(0, RoundingMode.FLOOR).longValueExact();
            int nanos = seconds.subtract(BigDecimal.valueOf(whole)).movePointRight(9).intValueExact();
            return Instant.ofEpochSecond(whole, nanos);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(name + " is outside the supported numeric date range/precision", ex);
        }
    }
}
