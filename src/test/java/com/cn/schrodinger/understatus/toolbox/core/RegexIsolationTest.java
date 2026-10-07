package com.cn.schrodinger.understatus.toolbox.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegexIsolationTest {
    @TempDir Path temporary;

    @Test void workerRunsFromItsModuleJarWithoutIdeClasspath() throws Exception {
        Path jar = temporary.resolve("module with spaces.jar");
        List<Class<?>> classes = new ArrayList<>(List.of(RegexEvaluator.class, RegexWorkerProcess.class, ToolLimits.class));
        classes.addAll(List.of(RegexEvaluator.class.getDeclaredClasses()));
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            for (Class<?> type : classes) {
                String resource = type.getName().replace('.', '/') + ".class";
                out.putNextEntry(new JarEntry(resource));
                try (var input = type.getResourceAsStream("/" + resource)) { input.transferTo(out); }
                out.closeEntry();
            }
        }
        try (URLClassLoader isolated = new URLClassLoader(new java.net.URL[]{jar.toUri().toURL()}, null)) {
            Class<?> evaluator = isolated.loadClass(RegexEvaluator.class.getName());
            Object result = evaluator.getMethod("evaluate", String.class, String.class, int.class, String.class)
                    .invoke(null, "([0-9]+)", "a12", 0, "$1$1");
            assertEquals("a1212", result.getClass().getMethod("output").invoke(result));
        }
        assertNoWorkerProcesses();
    }

    @Test void wallDeadlineKillsAndReapsTheWorker() {
        long start = System.nanoTime();
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> RegexEvaluator.evaluate("a+$", "a".repeat(99_999) + "!", 0, null, Duration.ofSeconds(2)));
        assertTrue(error.getMessage().contains("2 秒"), error.getMessage());
        assertTrue(Duration.ofNanos(System.nanoTime() - start).compareTo(Duration.ofSeconds(4)) < 0);
        assertNoWorkerProcesses();
    }

    @Test void cancellationReapsTheWorkerAndNullablePatternsRemainSupported() throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            try { RegexEvaluator.evaluate("a+$", "a".repeat(99_999) + "!", 0, null, Duration.ofSeconds(2)); }
            catch (Throwable failure) { error.set(failure); }
        });
        caller.setDaemon(true);
        caller.start();
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (workerProcesses().isEmpty() && caller.isAlive() && System.nanoTime() < deadline) Thread.sleep(5);
        caller.interrupt();
        caller.join(2000);
        assertFalse(caller.isAlive());
        assertTrue(error.get() instanceof java.util.concurrent.CancellationException, String.valueOf(error.get()));
        assertNoWorkerProcesses();
        for (String pattern : new String[]{"(?:){2147483647}", "(?:|){32}z", "(?:(?:){10000}){10000}"}) {
            // These patterns need not touch charAt; the process still has an independent wall deadline.
            RegexEvaluator.evaluate(pattern, "", 0, null);
        }
        assertNoWorkerProcesses();
    }

    @Test void protocolPreservesUtf16CodeUnitsAndLargeResultsWithoutPipeDeadlock() {
        String text = "\ud800" + "x".repeat(90_000);
        assertEquals(text, RegexEvaluator.evaluate("z", text, 0, "").output());
    }

    private static List<ProcessHandle> workerProcesses() {
        return ProcessHandle.current().children().filter(ProcessHandle::isAlive).toList();
    }

    private static void assertNoWorkerProcesses() { assertTrue(workerProcesses().isEmpty(), "Regex child process leaked"); }
}
