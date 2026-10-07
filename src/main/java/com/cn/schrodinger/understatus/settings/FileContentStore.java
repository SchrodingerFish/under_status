package com.cn.schrodinger.understatus.settings;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/** Checksummed UTF-8 files with atomic replacement and one verified backup. */
public final class FileContentStore implements ContentStore {
    private static final String VERSION = "understatus-content-v1\n";
    public static final int MAX_BYTES = 16 * 1024 * 1024;
    private final Path directory;
    // Compatibility only. Production callers consume ReadResult, not mutable metadata.
    private final ThreadLocal<String> warning = ThreadLocal.withInitial(() -> "");

    public FileContentStore(Path directory) { this.directory = directory.toAbsolutePath().normalize(); }

    @Override public synchronized Optional<String> read(String key) {
        return readResult(key).map(ReadResult::value);
    }

    @Override public synchronized Optional<ReadResult> readResult(String key) {
        Path file = path(key);
        Path backup = path(key + ".bak");
        warning.remove();
        if (!Files.exists(file) && !Files.exists(backup)) return Optional.empty();
        try {
            return Optional.of(new ReadResult(decode(file), ""));
        } catch (IOException primary) {
            try {
                String recovered = decode(backup);
                ReadResult result = new ReadResult(recovered, "主文件不可用，已恢复上一份备份；请核对内容后保存。");
                warning.set(result.warning());
                return Optional.of(result);
            } catch (IOException secondary) {
                primary.addSuppressed(secondary);
                throw new UncheckedIOException("无法读取已保存内容，原文件已保留", primary);
            }
        }
    }

    @Override public synchronized void write(String key, String value) {
        byte[] payload = value.getBytes(StandardCharsets.UTF_8);
        if (payload.length > MAX_BYTES) throw new IllegalArgumentException("内容超过 16 MiB 保存限制");
        Path file = path(key);
        Path temporary = null;
        try {
            Files.createDirectories(directory);
            temporary = Files.createTempFile(directory, key + "-", ".tmp");
            byte[] encoded = (VERSION + hash(payload) + "\n" + value).getBytes(StandardCharsets.UTF_8);
            Files.write(temporary, encoded);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
            if (Files.exists(file)) {
                // Never overwrite a usable backup with a corrupt primary.
                boolean valid;
                try { decode(file); valid = true; } catch (IOException corrupt) { valid = false; }
                if (valid) {
                    Path backupTemp = Files.createTempFile(directory, key + "-backup-", ".tmp");
                    try {
                        Files.copy(file, backupTemp, StandardCopyOption.REPLACE_EXISTING);
                        Files.move(backupTemp, path(key + ".bak"), StandardCopyOption.ATOMIC_MOVE,
                                StandardCopyOption.REPLACE_EXISTING);
                    } finally { Files.deleteIfExists(backupTemp); }
                }
            }
            // Fail closed if the filesystem cannot provide atomic replacement.
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            warning.remove();
        } catch (IOException ex) {
            throw new UncheckedIOException("保存失败，已有内容已保留", ex);
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { /* Next save can still proceed. */ }
            }
        }
    }

    /** @deprecated Last operation on this thread only; prefer readResult. */
    @Deprecated
    @Override public String warning() { return warning.get(); }

    private Path path(String key) {
        if (!key.matches("[a-zA-Z0-9.-]+") || key.contains("..")) throw new IllegalArgumentException("无效内容标识");
        return directory.resolve(key + ".data");
    }

    private static String decode(Path file) throws IOException {
        if (Files.size(file) > MAX_BYTES + 128L) throw new IOException("内容文件过大");
        String encoded = Files.readString(file, StandardCharsets.UTF_8);
        if (!encoded.startsWith(VERSION)) throw new IOException("未知内容文件版本");
        int start = VERSION.length();
        int end = encoded.indexOf('\n', start);
        if (end < 0) throw new IOException("内容文件头损坏");
        String payload = encoded.substring(end + 1);
        if (!hash(payload.getBytes(StandardCharsets.UTF_8)).equals(encoded.substring(start, end))) {
            throw new IOException("内容文件校验失败");
        }
        return payload;
    }

    private static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
