package com.cn.schrodinger.understatus.toolbox.core;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;

/** Dependency-free worker protocol. The parent owns and always reaps the isolated JVM. */
public final class RegexWorkerProcess {
    static final Duration WALL_BUDGET = Duration.ofSeconds(2);
    private static final int MAGIC = 0x52584731;
    private static final int MAX_ERROR = 8000;
    private RegexWorkerProcess() {}

    static RegexEvaluator.Result evaluate(String regex, String text, int flags, String replacement, Duration budget) {
        Path input = null, output = null;
        Process process = null;
        long deadline = System.nanoTime() + WALL_BUDGET.toNanos();
        try {
            ToolLimits.checkInterrupted();
            input = Files.createTempFile("understatus-regex-input-", ".bin");
            output = Files.createTempFile("understatus-regex-output-", ".bin");
            try (var stream = new DataOutputStream(new java.io.BufferedOutputStream(Files.newOutputStream(input)))) {
                stream.writeInt(MAGIC);
                writeText(stream, regex, 2048);
                writeText(stream, text, RegexEvaluator.MAX_INPUT);
                stream.writeInt(flags);
                stream.writeBoolean(replacement != null);
                if (replacement != null) writeText(stream, replacement, 2048);
                stream.writeLong(budget.toNanos());
            }
            var source = RegexWorkerProcess.class.getProtectionDomain().getCodeSource();
            if (source == null || !source.getLocation().getProtocol().equals("file")) {
                throw new IOException("Cannot locate the regex module on disk");
            }
            String classPath = Path.of(source.getLocation().toURI()).toString();
            String executable = System.getProperty("os.name", "").startsWith("Windows") ? "java.exe" : "java";
            String javaCommand = Path.of(System.getProperty("java.home"), "bin", executable).toString();
            process = new ProcessBuilder(javaCommand, "-Xmx64m", "-cp", classPath, RegexWorkerProcess.class.getName(),
                    input.toString(), output.toString())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0 || !process.waitFor(remaining, TimeUnit.NANOSECONDS)) {
                throw new IllegalArgumentException("正则计算超时（2 秒，包含隔离进程启动）；请简化表达式或缩短文本");
            }
            ToolLimits.checkInterrupted();
            if (process.exitValue() != 0 || Files.size(output) > 1_000_000) {
                throw new IllegalArgumentException("正则隔离进程失败或超过资源限制");
            }
            try (var stream = new DataInputStream(new java.io.BufferedInputStream(Files.newInputStream(output)))) {
                if (stream.readInt() != MAGIC) throw new IOException("Invalid regex worker response");
                if (!stream.readBoolean()) throw new IllegalArgumentException(readText(stream, MAX_ERROR));
                String result = readText(stream, RegexEvaluator.MAX_OUTPUT);
                int count = stream.readInt();
                if (count < 0 || count > RegexEvaluator.MAX_MATCHES) throw new IOException("Invalid match count");
                var spans = new ArrayList<RegexEvaluator.Span>(count);
                for (int i = 0; i < count; i++) {
                    int start = stream.readInt(), end = stream.readInt();
                    if (start < 0 || start > end || end > text.length()) throw new IOException("Invalid match position");
                    spans.add(new RegexEvaluator.Span(start, end));
                }
                boolean truncated = stream.readBoolean();
                if (stream.read() != -1) throw new IOException("Unexpected worker output");
                return new RegexEvaluator.Result(result, java.util.List.copyOf(spans), truncated);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Regex request cancelled");
        } catch (IOException | java.net.URISyntaxException ex) {
            throw new IllegalArgumentException("无法运行正则隔离进程: " + ex.getMessage(), ex);
        } finally {
            if (process != null) reap(process);
            delete(input);
            delete(output);
        }
    }

    private static void reap(Process process) {
        boolean interrupted = Thread.interrupted();
        try {
            if (process.isAlive()) process.destroyForcibly();
            // This process never creates children. Wait for OS termination before releasing its files.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while (process.isAlive() && System.nanoTime() < deadline) {
                try { process.waitFor(50, TimeUnit.MILLISECONDS); }
                catch (InterruptedException ex) { interrupted = true; }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    private static void delete(Path file) {
        if (file == null) return;
        try { Files.deleteIfExists(file); }
        catch (IOException ex) { file.toFile().deleteOnExit(); }
    }

    private static void writeText(DataOutputStream stream, String text, int maximum) throws IOException {
        if (text.length() > maximum) throw new IOException("Text exceeds protocol limit");
        stream.writeInt(text.length());
        // UTF-16 code units preserve Java regex behavior even for isolated surrogates.
        for (int i = 0; i < text.length(); i++) stream.writeChar(text.charAt(i));
    }

    private static String readText(DataInputStream stream, int maximum) throws IOException {
        int length = stream.readInt();
        if (length < 0 || length > maximum) throw new IOException("Invalid text length");
        char[] chars = new char[length];
        for (int i = 0; i < length; i++) chars[i] = stream.readChar();
        return new String(chars);
    }

    /** Entry point launched using this class's code source, never the IDE's process classpath. */
    public static void main(String[] args) throws IOException {
        if (args.length != 2) throw new IOException("Expected input/output protocol files");
        RegexEvaluator.Result result = null;
        String error = null;
        try (var input = new DataInputStream(new java.io.BufferedInputStream(Files.newInputStream(Path.of(args[0]))))) {
            if (Files.size(Path.of(args[0])) > 500_000 || input.readInt() != MAGIC) throw new IOException("Invalid request");
            String regex = readText(input, 2048);
            String text = readText(input, RegexEvaluator.MAX_INPUT);
            int flags = input.readInt();
            String replacement = input.readBoolean() ? readText(input, 2048) : null;
            Duration budget = Duration.ofNanos(input.readLong());
            if (input.read() != -1) throw new IOException("Unexpected input");
            result = RegexEvaluator.evaluateInProcess(regex, text, flags, replacement, budget);
        } catch (Exception ex) {
            error = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            if (error.length() > MAX_ERROR) error = error.substring(0, MAX_ERROR);
        }
        try (var output = new DataOutputStream(new java.io.BufferedOutputStream(Files.newOutputStream(Path.of(args[1]))))) {
            output.writeInt(MAGIC);
            output.writeBoolean(error == null);
            if (error != null) writeText(output, error, MAX_ERROR);
            else {
                writeText(output, result.output(), RegexEvaluator.MAX_OUTPUT);
                output.writeInt(result.spans().size());
                for (var span : result.spans()) { output.writeInt(span.start()); output.writeInt(span.end()); }
                output.writeBoolean(result.truncated());
            }
        }
    }
}
