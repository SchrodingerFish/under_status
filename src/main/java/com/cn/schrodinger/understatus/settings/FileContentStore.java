package com.cn.schrodinger.understatus.settings;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Supplier;

/** Versioned UTF-8 documents with integrity checking, atomic replacement and backup. */
public final class FileContentStore implements ContentStore {
    private static final int MAX_BYTES = 16 * 1024 * 1024;
    private final Path directory;

    public FileContentStore(Path directory) { this.directory = directory.toAbsolutePath().normalize(); }

    @Override public synchronized String load(String key, Supplier<String> legacy) {
        Path file = path(key), backup = path(key + ".bak");
        try {
            if (Files.exists(file)) {
                try { return decode(file); }
                catch (IOException failure) {
                    if (Files.exists(backup)) return decode(backup);
                    throw failure;
                }
            }
            if (Files.exists(backup)) return decode(backup);
            String value = legacy.get();
            if (!value.isEmpty()) save(key, value);
            return value;
        } catch (IOException ex) { throw new UncheckedIOException("无法读取 " + key + "；原文件已保留", ex); }
    }

    @Override public synchronized void save(String key, String value) {
        Path file = path(key), backup = path(key + ".bak");
        try {
            byte[] data = value.getBytes(StandardCharsets.UTF_8);
            if (data.length > MAX_BYTES) throw new IOException("内容超过 16 MiB");
            byte[] encoded = ("UnderStatus-v1\n" + digest(data) + "\n" + value).getBytes(StandardCharsets.UTF_8);
            Files.createDirectories(directory);
            if (Files.exists(file)) {
                boolean valid = true;
                try { decode(file); }
                catch (IOException corrupt) {
                    valid = false;
                    if (!Files.exists(backup)) throw corrupt;
                    decode(backup);
                }
                if (valid) replace(backup, Files.readAllBytes(file));
            }
            replace(file, encoded);
        } catch (IOException ex) { throw new UncheckedIOException("保存失败，旧数据已保留: " + key, ex); }
    }

    private Path path(String key) {
        if (!key.matches("[a-zA-Z0-9._-]+")) throw new IllegalArgumentException("Invalid content key");
        Path result = directory.resolve(key + ".data").normalize();
        if (!result.getParent().equals(directory)) throw new IllegalArgumentException("Invalid content path");
        return result;
    }

    private static String decode(Path file) throws IOException {
        if (Files.size(file) > MAX_BYTES + 128) throw new IOException("文件过大");
        String[] parts = Files.readString(file, StandardCharsets.UTF_8).split("\n", 3);
        if (parts.length != 3 || !parts[0].equals("UnderStatus-v1")
                || !parts[1].equals(digest(parts[2].getBytes(StandardCharsets.UTF_8)))) {
            throw new IOException("文件格式或校验和无效");
        }
        return parts[2];
    }

    private void replace(Path target, byte[] bytes) throws IOException {
        Path temp = Files.createTempFile(directory, "content-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }

    private static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
