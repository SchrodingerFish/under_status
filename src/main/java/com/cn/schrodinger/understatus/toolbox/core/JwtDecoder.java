package com.cn.schrodinger.understatus.toolbox.core;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Standard compliance JSON Web Token (JWT) decoder and diagnostics analyzer.
 * Splits header/payload, formats claims, and calculates expiration status.
 *
 * @author peter/antigravity
 */
public class JwtDecoder {

    private static final DateTimeFormatter TIME_FMT = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private static final Pattern PATTERN_ALG = Pattern.compile("\"alg\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern PATTERN_TYP = Pattern.compile("\"typ\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern PATTERN_SUB = Pattern.compile("\"sub\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern PATTERN_ISS = Pattern.compile("\"iss\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern PATTERN_AUD = Pattern.compile("\"aud\"\\s*:\\s*\"([^\"]*)\"");

    private static final Pattern PATTERN_EXP = Pattern.compile("\"exp\"\\s*:\\s*(\\d+)");
    private static final Pattern PATTERN_IAT = Pattern.compile("\"iat\"\\s*:\\s*(\\d+)");
    private static final Pattern PATTERN_NBF = Pattern.compile("\"nbf\"\\s*:\\s*(\\d+)");

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

            StringBuilder sb = new StringBuilder();
            sb.append("════════════════════ 🎫 JWT 诊断分析 (Diagnostics) ════════════════════\n");

            // Extract claims
            String alg = extractStringClaim(header, PATTERN_ALG);
            String typ = extractStringClaim(header, PATTERN_TYP);
            Long exp = extractLongClaim(payload, PATTERN_EXP);
            Long iat = extractLongClaim(payload, PATTERN_IAT);
            Long nbf = extractLongClaim(payload, PATTERN_NBF);
            String sub = extractStringClaim(payload, PATTERN_SUB);
            String iss = extractStringClaim(payload, PATTERN_ISS);
            String aud = extractStringClaim(payload, PATTERN_AUD);

            long now = Instant.now().getEpochSecond();

            // Status calculation
            if (exp != null) {
                long diff = exp - now;
                if (diff > 0) {
                    sb.append("【令牌状态】: 🟢 有效中 (剩余 ").append(formatDuration(diff)).append(")\n");
                } else {
                    sb.append("【令牌状态】: 🔴 已过期 (已过期 ").append(formatDuration(-diff)).append(")\n");
                }
            } else {
                sb.append("【令牌状态】: ⚪ 永久有效 (未包含 exp 过期时间)\n");
            }

            if (nbf != null && nbf > now) {
                sb.append("【生效预警】: 🟡 尚未生效 (将于 ").append(formatDuration(nbf - now)).append(" 后生效)\n");
            }

            if (alg != null || typ != null) {
                sb.append("【算法类型】: ")
                  .append(alg != null ? "算法 " + alg : "")
                  .append(typ != null ? " | 类型 " + typ : "")
                  .append("\n");
            }

            if (iat != null) {
                sb.append("【签发时间 (iat)】: ").append(TIME_FMT.format(Instant.ofEpochSecond(iat)))
                  .append(" (").append(iat).append(")\n");
            }
            if (nbf != null) {
                sb.append("【生效时间 (nbf)】: ").append(TIME_FMT.format(Instant.ofEpochSecond(nbf)))
                  .append(" (").append(nbf).append(")\n");
            }
            if (exp != null) {
                sb.append("【过期时间 (exp)】: ").append(TIME_FMT.format(Instant.ofEpochSecond(exp)))
                  .append(" (").append(exp).append(")\n");
            }

            if (sub != null) sb.append("【主体身份 (sub)】: ").append(sub).append("\n");
            if (iss != null) sb.append("【签发机构 (iss)】: ").append(iss).append("\n");
            if (aud != null) sb.append("【受众群体 (aud)】: ").append(aud).append("\n");

            if (parts.length >= 3 && !parts[2].isEmpty()) {
                sb.append("【数字签名 (sig)】: 已附带签名 (长度 ").append(parts[2].length()).append(" 字符)\n");
            } else {
                sb.append("【数字签名 (sig)】: ⚠️ 无签名 (Unsigned)\n");
            }

            sb.append("═══════════════════════════════════════════════════════════════════════\n\n");
            sb.append("【HEADER · 标头】\n").append(JsonFormatter.format(header)).append("\n\n");
            sb.append("【PAYLOAD · 载荷】\n").append(JsonFormatter.format(payload));

            return sb.toString();
        } catch (Exception ex) {
            return "JWT 解码失败 (Decoding failed): " + ex.getMessage();
        }
    }

    private static String formatDuration(long seconds) {
        if (seconds < 0) seconds = -seconds;
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (days > 0) {
            return days + " 天 " + hours + " 小时 " + minutes + " 分";
        } else if (hours > 0) {
            return hours + " 小时 " + minutes + " 分 " + secs + " 秒";
        } else if (minutes > 0) {
            return minutes + " 分 " + secs + " 秒";
        } else {
            return secs + " 秒";
        }
    }

    private static Long extractLongClaim(String json, Pattern pattern) {
        Matcher m = pattern.matcher(json);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static String extractStringClaim(String json, Pattern pattern) {
        Matcher m = pattern.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }
}

