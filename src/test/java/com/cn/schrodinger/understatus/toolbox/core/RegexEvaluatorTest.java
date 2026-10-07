package com.cn.schrodinger.understatus.toolbox.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class RegexEvaluatorTest {
    @Test void catastrophicComputationActuallyTerminates() throws InterruptedException {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                RegexEvaluator.evaluate("(a+)+$", "a".repeat(50_000) + "!", 0, null, Duration.ofMillis(30));
            } catch (Throwable ex) { failure.set(ex); }
        });
        worker.setDaemon(true);
        worker.start();
        worker.join(2000);
        boolean alive = worker.isAlive();
        worker.interrupt();
        assertFalse(alive, "Regex computation must end, rather than only abandoning its Future");
        assertNotNull(failure.get());
        assertTrue(failure.get() instanceof IllegalArgumentException);
    }

    @Test void replacementRetainsJavaGroupSyntaxAndIsBounded() {
        String regex = "(?<word>[a-z]+)([0-9]+)", input = "a12 b34";
        for (String replacement : new String[]{"${word}/$2", "$0:$1", "\\$1", "$12"}) {
            assertEquals(Pattern.compile(regex).matcher(input).replaceAll(replacement),
                    RegexEvaluator.evaluate(regex, input, 0, replacement).output());
        }
        assertThrows(IllegalArgumentException.class,
                () -> RegexEvaluator.evaluate("(.*)", "a".repeat(100_000), 0, "$1$1$1"));
        assertThrows(IllegalArgumentException.class,
                () -> RegexEvaluator.evaluate("a", "a".repeat(501), 0, "b"));
    }

    @Test void matchCountAndInputHaveLimits() {
        var result = RegexEvaluator.evaluate("a", "a".repeat(501), 0, null);
        assertEquals(500, result.spans().size());
        assertTrue(result.truncated());
        assertThrows(IllegalArgumentException.class,
                () -> RegexEvaluator.evaluate("x", "x".repeat(100_001), 0, null));
    }
}
