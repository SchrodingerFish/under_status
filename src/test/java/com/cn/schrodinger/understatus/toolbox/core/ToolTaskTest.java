package com.cn.schrodinger.understatus.toolbox.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.awt.event.HierarchyEvent;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ToolTaskTest {
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 1, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(8), runnable -> {
                Thread worker = new Thread(runnable, "toolbox-test");
                worker.setDaemon(true);
                return worker;
            });
    private final AtomicBoolean visible = new AtomicBoolean(true);
    private final JPanel owner = new JPanel() {
        @Override public boolean isShowing() { return visible.get(); }
    };
    private final ToolTask task = new ToolTask(owner, executor);

    @AfterEach void close() { executor.shutdownNow(); }

    @Test void computationRunsOffEdtAndResultsRunOnEdt() throws Exception {
        AtomicBoolean workerWasEdt = new AtomicBoolean(true), resultWasEdt = new AtomicBoolean();
        CountDownLatch complete = new CountDownLatch(1);
        SwingUtilities.invokeAndWait(() -> task.submit(() -> {
            workerWasEdt.set(SwingUtilities.isEventDispatchThread());
            return "done";
        }, value -> { resultWasEdt.set(SwingUtilities.isEventDispatchThread()); complete.countDown(); }, error -> complete.countDown()));
        assertTrue(complete.await(2, TimeUnit.SECONDS));
        assertFalse(workerWasEdt.get());
        assertTrue(resultWasEdt.get());
    }

    @Test void editsAndNewRequestsDiscardEvenUncooperativeOldResults() throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicReference<String> shown = new AtomicReference<>("");
        JTextArea input = new JTextArea("old");
        SwingUtilities.invokeAndWait(() -> {
            task.watch(input);
            task.submit(() -> {
                started.countDown();
                awaitIgnoringInterrupt(release);
                return "stale";
            }, shown::set, shown::set);
        });
        assertTrue(started.await(2, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> {
            input.setText("new");
            task.submit(() -> "current", shown::set, shown::set);
        });
        release.countDown();
        drain();
        assertEquals("current", shown.get());
    }

    @Test void hierarchyRemovalCancelsAndNeverPublishesAfterReopen() throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicReference<String> shown = new AtomicReference<>("");
        SwingUtilities.invokeAndWait(() -> task.submit(() -> {
            started.countDown();
            awaitIgnoringInterrupt(release);
            return "closed";
        }, shown::set, shown::set));
        assertTrue(started.await(2, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> {
            visible.set(false);
            owner.dispatchEvent(new HierarchyEvent(owner, HierarchyEvent.HIERARCHY_CHANGED,
                    owner, null, HierarchyEvent.SHOWING_CHANGED));
            visible.set(true);
        });
        release.countDown();
        drain();
        assertEquals("", shown.get());
    }

    @Test void invalidFormattingPreservesSource() throws Exception {
        JTextArea input = new JTextArea("{broken}");
        javax.swing.JLabel status = new javax.swing.JLabel();
        SwingUtilities.invokeAndWait(() -> task.transform(input, input, status, JsonFormatter::format));
        drain();
        assertEquals("{broken}", input.getText());
        assertTrue(status.getText().startsWith("处理失败"));
    }

    @Test void typingAloneInvalidatesAnOutstandingResult() throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicReference<String> shown = new AtomicReference<>("");
        JTextArea input = new JTextArea("old");
        SwingUtilities.invokeAndWait(() -> {
            task.watch(input);
            task.submit(() -> {
                started.countDown();
                awaitIgnoringInterrupt(release);
                return "stale";
            }, shown::set, shown::set);
        });
        assertTrue(started.await(2, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> input.setText("new"));
        release.countDown();
        drain();
        assertEquals("", shown.get());
    }

    @Test void rapidlyReplacedRequestsDoNotFillTheQueue() throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicReference<String> shown = new AtomicReference<>("");
        java.util.concurrent.atomic.AtomicInteger errors = new java.util.concurrent.atomic.AtomicInteger();
        SwingUtilities.invokeAndWait(() -> task.submit(() -> {
            started.countDown();
            awaitIgnoringInterrupt(release);
            return "old";
        }, shown::set, error -> errors.incrementAndGet()));
        assertTrue(started.await(2, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> {
            for (int i = 0; i < 40; i++) {
                String value = Integer.toString(i);
                task.submit(() -> value, shown::set, error -> errors.incrementAndGet());
            }
        });
        release.countDown();
        drain();
        assertEquals("39", shown.get());
        assertEquals(0, errors.get());
    }

    private void drain() throws Exception {
        executor.submit(() -> {}).get(3, TimeUnit.SECONDS);
        SwingUtilities.invokeAndWait(() -> {});
    }

    private static void awaitIgnoringInterrupt(CountDownLatch latch) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            try { if (latch.await(deadline - System.nanoTime(), TimeUnit.NANOSECONDS)) return; }
            catch (InterruptedException ignored) { /* Deliberately simulate non-cooperative library work. */ }
        }
    }
}
