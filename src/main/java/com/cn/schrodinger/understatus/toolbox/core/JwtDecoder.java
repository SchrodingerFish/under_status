package com.cn.schrodinger.understatus.toolbox.core;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Standard compliance JSON Web Token (JWT) decoder.
 * Splits header/payload and applies local JSON formatting.
 *
 * @author peter/antigravity
 */
public class JwtDecoder {

    public static String decodeJwt(String token) {
        if (token == null || token.trim().isEmpty()) {
            return "";
        }
        token = token.trim();
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            return "无效的 JWT 格式 (Must contain at least Header and Payload parts separated by dots)";
        }
        try {
            String header = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            
            return "【HEADER】\n" + JsonFormatter.format(header) + "\n\n【PAYLOAD】\n" + JsonFormatter.format(payload);
        } catch (Exception ex) {
            return "JWT 解码失败 (Decoding failed): " + ex.getMessage();
        }
    }
}
