package com.cn.schrodinger.understatus.statusbar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LatestTaskTest {

    @Test
    void closeRejectsNewWork() {
        LatestTask<String> task = new LatestTask<>(Runnable::run);
        task.close();
        assertThrows(IllegalStateException.class, () -> task.submit(() -> "x", value -> {}, error -> {}));
    }

    @Test
    void latestResultWins() throws Exception {
        Queue<Runnable> queue = new ArrayDeque<>();
        LatestTask<String> task = new LatestTask<>(queue::add);
        AtomicInteger callbacks = new AtomicInteger();
        task.submit(() -> "old", value -> callbacks.incrementAndGet(), error -> {});
        task.submit(() -> "new", value -> callbacks.addAndGet(10), error -> {});
        while (!queue.isEmpty()) queue.remove().run();
        flushEdt();
        assertEquals(10, callbacks.get());
    }

    private static void flushEdt() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {});
    }
}
