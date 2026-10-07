package com.cn.schrodinger.understatus.toolbox.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class DiffBoundaryTest {
    @Test void unrelatedLargeInputsUseDeterministicBoundedFallback() {
        String before = "old\n".repeat(2000), after = "new\n".repeat(2000);
        var result = DiffCalculator.calculateDetailed(before, after);
        assertTrue(result.coarse());
        assertEquals(before, reconstruct(result.lines(), -1));
        assertEquals(after, reconstruct(result.lines(), 1));
    }

    @Test void reconstructionPreservesEmptyTrailingAndRandomLines() {
        Random random = new Random(123);
        for (int trial = 0; trial < 200; trial++) {
            String before = randomText(random), after = randomText(random);
            var diff = DiffCalculator.calculateDiff(before, after);
            assertEquals(before, reconstruct(diff, -1));
            assertEquals(after, reconstruct(diff, 1));
        }
        assertTrue(DiffCalculator.calculateDiff("", "").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> DiffCalculator.calculateDiff("\n".repeat(20_001), ""));
        assertThrows(IllegalArgumentException.class, () -> DiffCalculator.calculateDiff("x".repeat(250_001), ""));
    }

    private String randomText(Random random) {
        return random.ints(random.nextInt(30), 0, 8).mapToObj(Integer::toString).collect(Collectors.joining("\n"));
    }
    private String reconstruct(List<DiffCalculator.DiffLine> lines, int type) {
        return lines.stream().filter(line -> line.type == 0 || line.type == type)
                .map(line -> line.text.substring(2)).collect(Collectors.joining("\n"));
    }
}
