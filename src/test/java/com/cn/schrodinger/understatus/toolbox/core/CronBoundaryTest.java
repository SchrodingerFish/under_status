package com.cn.schrodinger.understatus.toolbox.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CronBoundaryTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-06T10:00:00Z"), ZoneOffset.UTC);

    @Test void unixAndQuartzMondayNeverMatchTuesday() {
        LocalDateTime monday = LocalDateTime.of(2026, 10, 12, 9, 0);
        assertEquals(monday, next("0 9 * * MON"));
        assertEquals(monday, next("0 0 9 ? * MON"));
        assertEquals(monday, next("0 9 * * 1"));
        assertEquals(monday, next("0 0 9 ? * 2"));
        assertEquals(next("0 9 * * 0"), next("0 9 * * 7"));
    }

    @Test void explicitYearAndRangesAreHonored() {
        assertEquals(LocalDateTime.of(2099, 1, 1, 0, 0), next("0 0 0 1 JAN ? 2099"));
        assertEquals(LocalDateTime.of(2026, 10, 6, 10, 10), next("0 10-20/5 * * * ?"));
        assertTrue(CronExplainer.getNextExecutionTimes("0 0 0 31 FEB ?", 1, CLOCK).isEmpty());
        assertTrue(CronExplainer.getNextExecutionTimes("0 0 0 1 JAN ? 2000", 1, CLOCK).isEmpty());
    }

    @Test void unixRestrictedDaysUseOrAndQuartzRequiresQuestionMark() {
        var runs = CronExplainer.getNextExecutionTimes("0 0 1 * MON", 5, CLOCK);
        assertEquals(LocalDateTime.of(2026, 11, 1, 0, 0), runs.get(3));
        assertThrows(IllegalArgumentException.class, () -> next("0 0 0 * * *"));
    }

    @Test void malformedAndUnsupportedFieldsFailPromptly() {
        for (String cron : new String[]{"*/0 * * * *", "*/-1 * * * *", "*/999999999999 * * * *",
                "60 * * * *", "0 0 32 * *", "0 0 * 13 *", "0 0 * * 8", "0 0 * * MONMON",
                "0 0 * * 5-1", "0 0 * * 1,", "0 0 ? * *", "0 0 L * *", "0 0 0 ? * MON#2",
                "0 0 0 ? * ?", "0 0 0 * * ? 2200", "0 0 0 * * ? 1970-2099/0"}) {
            assertThrows(IllegalArgumentException.class, () -> next(cron), cron);
        }
    }

    private LocalDateTime next(String expression) {
        return CronExplainer.getNextExecutionTimes(expression, 1, CLOCK).getFirst();
    }
}
