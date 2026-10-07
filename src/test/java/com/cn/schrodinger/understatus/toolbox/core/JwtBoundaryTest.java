package com.cn.schrodinger.understatus.toolbox.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class JwtBoundaryTest {
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochSecond(100), ZoneOffset.UTC);

    @Test void nestedClaimsNeverDetermineTopLevelStatus() {
        String result = decode("{\"nested\":{\"exp\":1},\"aud\":[\"first\",\"second\"]}");
        assertTrue(result.contains("未包含 exp"));
        assertTrue(result.contains("未验证签名"));
        assertTrue(result.contains("first"));
        assertFalse(result.contains("永久有效"));
        assertFalse(result.contains("已过期"));
    }

    @Test void futureNotBeforeIsNotReportedAsValid() {
        String result = decode("{\"exp\":200,\"nbf\":150}");
        assertTrue(result.contains("尚未生效"));
        assertFalse(result.contains("有效中"));
        assertTrue(decode("{\"exp\":100}").contains("已过期"));
        assertTrue(decode("{\"exp\":-1}").contains("已过期"));
    }

    @Test void malformedClaimsAndOutOfRangeDatesAreRejected() {
        for (String payload : new String[]{"[]", "{bad}", "{\"exp\":9223372036854775808}",
                "{\"exp\":\"200\"}", "{\"aud\":[1]}", "{\"nbf\":null}", "{} {}"}) {
            assertTrue(decode(payload).contains("解码失败"), payload);
        }
    }

    @Test void fractionalNumericDatesRetainNanosecondPrecision() {
        assertTrue(decode("{\"exp\":1516239022.123456789}").contains("2018-01-18T01:30:22.123456789Z"));
        assertTrue(decode("{\"exp\":-0.000000001}").contains("1969-12-31T23:59:59.999999999Z"));
        assertTrue(decode("{\"exp\":-1.25}").contains("1969-12-31T23:59:58.750Z"));
        assertTrue(decode("{\"exp\":1.250000000000}").contains("1970-01-01T00:00:01.250Z"));
        assertTrue(decode("{\"exp\":1e-9}").contains("1970-01-01T00:00:00.000000001Z"));
        assertTrue(decode("{\"exp\":1e2}").contains("1970-01-01T00:01:40Z"));
        for (String value : new String[]{"1e-10", "-1e-10"}) {
            assertTrue(decode("{\"exp\":" + value + "}").contains("解码失败"));
        }
    }

    @Test void instantRangeIsCheckedWithoutRoundingValidEndpoints() {
        assertTrue(decode("{\"exp\":" + Instant.MIN.getEpochSecond() + "}").contains(Instant.MIN.toString()));
        assertTrue(decode("{\"exp\":" + Instant.MAX.getEpochSecond() + ".999999999}").contains(Instant.MAX.toString()));
        assertTrue(decode("{\"exp\":" + (Instant.MAX.getEpochSecond() + 1) + "}").contains("解码失败"));
        assertTrue(decode("{\"exp\":" + Instant.MIN.getEpochSecond() + ".000000001}").contains("解码失败"));
    }

    @Test void extremeExponentsFinishWithinAHeapLimitedKillableProcess() throws Exception {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        String javaCommand = Path.of(System.getProperty("java.home"), "bin", executable).toString();
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Process process = new ProcessBuilder(javaCommand, "-Xmx64m", "-cp", classpath,
                ExponentProbe.class.getName()).redirectErrorStream(true).start();
        boolean completed;
        try {
            completed = process.waitFor(5, TimeUnit.SECONDS);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                assertTrue(process.waitFor(5, TimeUnit.SECONDS), "The bounded probe must be reaped");
            }
        }
        assertTrue(completed, "Tiny exponent claims must not monopolize a computation worker");
        assertEquals(0, process.exitValue(), new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }

    /** Never run these inputs on the test runner's heap or an interrupt-only timeout. */
    public static final class ExponentProbe {
        public static void main(String[] args) {
            JwtBoundaryTest fixture = new JwtBoundaryTest();
            for (String value : new String[]{"1e100000000", "-1e100000000", "1e-100000000", "-1e-100000000",
                    "1e2147483647", "1e-2147483647"}) {
                for (String claim : new String[]{"exp", "nbf", "iat"}) {
                    String payload = "{\"" + claim + "\":" + value + "}";
                    if (!fixture.decode(payload).contains("解码失败")) throw new AssertionError(payload);
                }
                String json = "{\"number\":" + value + "}";
                assertThrowsIllegalArgument(() -> JsonFormatter.format(json));
                assertThrowsIllegalArgument(() -> JsonFormatter.minify(json));
            }
            if (!fixture.decode("{\"exp\":0e-100000000}").contains("1970-01-01T00:00:00Z")) {
                throw new AssertionError("Exact zero should remain representable");
            }
        }

        private static void assertThrowsIllegalArgument(Runnable action) {
            try { action.run(); }
            catch (IllegalArgumentException expected) { return; }
            throw new AssertionError("Extreme JSON decimals must be rejected before numeric conversion");
        }
    }

    private String decode(String payload) {
        return JwtDecoder.decodeJwt(encode("{\"alg\":\"none\"}") + "." + encode(payload) + ".", CLOCK);
    }
    private String encode(String text) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
}
