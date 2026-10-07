package com.cn.schrodinger.understatus.toolbox.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ToolboxCoreTest {

    @Test
    void jsonPreservesWhitespaceInsideStrings() {
        assertEquals("{\"value\":\"a b\"}", JsonFormatter.minify("{ \"value\" : \"a b\" }"));
    }

    @Test
    void unicodeRoundTripPreservesChineseText() {
        String source = "天气 A";
        assertEquals(source, EncodingConverter.unicodeToString(EncodingConverter.stringToUnicode(source)));
    }

    @Test
    void jwtRejectsMissingPayload() {
        assertTrue(JwtDecoder.decodeJwt("header-only").contains("无效"));
    }

    @Test
    void mathRequiresClosingParenthesis() {
        assertThrows(IllegalArgumentException.class, () -> MathParser.eval("(1 + 2"));
    }

    @Test
    void diffReportsOneDeletionAndOneAddition() {
        List<DiffCalculator.DiffLine> lines = DiffCalculator.calculateDiff("a\nb", "a\nc");
        assertEquals(List.of(0, -1, 1), lines.stream().map(line -> line.type).toList());
    }

    @Test
    void jsonEscapeAndUnescapeRoundTrip() {
        String json = "{\"name\":\"test\",\"desc\":\"hello \\\"world\\\"\"}";
        String escaped = JsonFormatter.escape(json);
        assertTrue(escaped.contains("\\\""));
        String unescaped = JsonFormatter.unescape(escaped);
        assertEquals(json, unescaped);
    }

    @Test
    void cronCalculatesNextRuns() {
        List<java.time.LocalDateTime> runs = CronExplainer.getNextExecutionTimes("0 */5 * * * ?", 3);
        assertEquals(3, runs.size());
        assertTrue(runs.get(1).isAfter(runs.get(0)));
        assertTrue(runs.get(2).isAfter(runs.get(1)));
    }

    @Test
    void jwtDecodesWithExpirationDiagnosis() {
        // {"alg":"HS256","typ":"JWT"} . {"sub":"1234567890","name":"John Doe","iat":1516239022,"exp":1516249022}
        String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9."
                     + "eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyLCJleHAiOjE1MTYyNDkwMjJ9."
                     + "SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";
        String decoded = JwtDecoder.decodeJwt(token);
        assertTrue(decoded.contains("已过期"));
        assertTrue(decoded.contains("HS256"));
        assertTrue(decoded.contains("1234567890"));
    }

    @Test
    void hashCalculatorProducesCorrectDigest() {
        assertEquals("5d41402abc4b2a76b9719d911017c592", HashCalculator.calculateHash("hello", "MD5"));
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                HashCalculator.calculateHash("hello", "SHA-256"));
    }

    @Test
    void sqlFormatterIndentsAndCapitalizes() {
        String formatted = SqlFormatter.format("select id, name from users where active = 1 and age > 18");
        assertTrue(formatted.contains("SELECT"));
        assertTrue(formatted.contains("FROM"));
        assertTrue(formatted.contains("WHERE"));
        assertTrue(formatted.contains("  AND"));
    }

    @Test
    void diffPrunesPrefixAndSuffixEfficiently() {
        String a = "header1\nheader2\noldLine\nfooter1\nfooter2";
        String b = "header1\nheader2\nnewLine\nfooter1\nfooter2";
        List<DiffCalculator.DiffLine> lines = DiffCalculator.calculateDiff(a, b);
        assertEquals(6, lines.size());
        assertEquals(0, lines.get(0).type);
        assertEquals(0, lines.get(1).type);
        assertEquals(-1, lines.get(2).type);
        assertEquals(1, lines.get(3).type);
        assertEquals(0, lines.get(4).type);
        assertEquals(0, lines.get(5).type);
    }
}
