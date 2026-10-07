package com.cn.schrodinger.understatus.toolbox.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class ToolboxRegressionTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-07T00:00:00Z"), ZoneOffset.UTC);

    @Test void cronRejectsInvalidStepsRangesAndUnsupportedSyntax() {
        for (String cron : new String[]{"*/0 * * * * ?", "*/-1 * * * * ?", "0 70 * * * ?",
                "0 0 0 L * ?", "0 0 0 ? * MON#2", "0 0 0 ? * MON/", "0 0 0 ? * * 2200"}) {
            assertTimeoutPreemptively(Duration.ofSeconds(1), () ->
                    assertThrows(IllegalArgumentException.class, () -> CronExplainer.getNextExecutionTimes(cron, 5, clock)), cron);
        }
    }

    @Test void cronHonorsWeekdayYearRangesAndUnixOrSemantics() {
        assertTrue(CronExplainer.getNextExecutionTimes("0 0 10 ? * MON", 5, clock).stream()
                .allMatch(time -> time.getDayOfWeek() == DayOfWeek.MONDAY));
        assertEquals(2099, CronExplainer.getNextExecutionTimes("0 0 0 1 1 ? 2099", 1, clock).get(0).getYear());
        assertTrue(CronExplainer.getNextExecutionTimes("0 0 * * 0", 3, clock).stream()
                .allMatch(time -> time.getDayOfWeek() == DayOfWeek.SUNDAY));
        assertTrue(CronExplainer.getNextExecutionTimes("0 0 * * 7", 3, clock).stream()
                .allMatch(time -> time.getDayOfWeek() == DayOfWeek.SUNDAY));
        assertEquals(12, CronExplainer.getNextExecutionTimes("0 0 1 * MON", 1, clock).get(0).getDayOfMonth());
        assertTrue(CronExplainer.getNextExecutionTimes("0 0 0 31 FEB ?", 1, clock).isEmpty());
        assertEquals(5, CronExplainer.getNextExecutionTimes("0 0-10/5 * * * ?", 2, clock).get(0).getMinute());
    }

    @Test void sqlPreservesLiteralsCommentsAndQuotedIdentifiers() {
        String sql = "select 'a  b from c', \"from\", [where], $$and  or$$ -- keep\nfrom users";
        for (String formatted : new String[]{SqlFormatter.format(sql), SqlFormatter.minify(sql)}) {
            assertTrue(formatted.contains("'a  b from c'"));
            assertTrue(formatted.contains("\"from\""));
            assertTrue(formatted.contains("[where]"));
            assertTrue(formatted.contains("$$and  or$$"));
            assertTrue(formatted.contains("-- keep\n"));
        }
        assertTrue(SqlFormatter.minify("select 1 -- keep\nfrom users").contains("-- keep\nfrom users"));
        assertTrue(SqlFormatter.format("select 'it''s from here'").contains("'it''s from here'"));
    }

    @Test void xmlPreservesMixedContentAndRejectsDoctype() {
        String mixed = "<p><b>A</b> <i>B</i></p>";
        assertEquals(mixed, XmlFormatter.minify(mixed));
        assertEquals(mixed, XmlFormatter.format(mixed));
        String preserve = "<p xml:space=\"preserve\">  <b>A</b>  </p>";
        assertEquals(preserve, XmlFormatter.minify(preserve));
        assertTrue(XmlFormatter.format("<!DOCTYPE a [<!ENTITY x SYSTEM 'file:///unavailable'>]><a>&x;</a>")
                .startsWith("XML Format Failed:"));
    }

    @Test void diffHandlesTenThousandUnrelatedLinesWithinBudget() {
        var diff = assertTimeoutPreemptively(Duration.ofSeconds(3), () ->
                DiffCalculator.calculateDiff("a\n".repeat(10_000), "b\n".repeat(10_000)));
        assertEquals(10_000, diff.stream().filter(line -> line.type == -1).count());
        assertEquals(10_000, diff.stream().filter(line -> line.type == 1).count());
        assertEquals(1, diff.stream().filter(line -> line.type == 0).count());
    }

    @Test void regexStopsBacktrackingAndCapsMatchesAndReplacement() {
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertThrows(IllegalArgumentException.class,
                () -> RegexEvaluator.evaluate("(a+)+$", "a".repeat(10000) + "!", 0, null)));
        var result = RegexEvaluator.evaluate("a", "a".repeat(600), 0, null);
        assertEquals(500, result.matches().size());
        assertTrue(result.truncated());
        assertThrows(IllegalArgumentException.class, () -> RegexEvaluator.evaluate("a", "a".repeat(600), 0, "b"));
        assertEquals("b12", RegexEvaluator.evaluate("a(\\d+)", "a12", 0, "b$1").output());
    }

    @Test void jsonRejectsInvalidAndDeepDocumentsAndJwtUsesTopLevelClaims() {
        assertThrows(IllegalArgumentException.class, () -> JsonFormatter.format("{\"a\":}"));
        assertThrows(IllegalArgumentException.class, () -> JsonFormatter.format("[".repeat(150) + "0" + "]".repeat(150)));
        String payload = "{\"nested\":{\"exp\":1},\"aud\":[\"one\",\"two\"]}";
        String token = encode("{\"alg\":\"none\"}") + "." + encode(payload) + ".";
        String output = JwtDecoder.decodeJwt(token);
        assertTrue(output.contains("未提供 exp"));
        assertTrue(output.contains("one, two"));
        assertTrue(output.contains("未验证签名"));
        assertFalse(output.contains("永久有效"));
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
