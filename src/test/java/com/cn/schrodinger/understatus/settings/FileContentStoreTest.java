package com.cn.schrodinger.understatus.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileContentStoreTest {
    @TempDir Path directory;

    @Test void migratesWithoutDeletingLegacyAndPersistsLargeUnicodeNotesAcrossRestart() {
        var legacy = new HashMap<String, String>();
        legacy.put("notesListSerialized", "legacy");
        var repository = new SettingsRepository(SettingsStore.inMemory(legacy), SecretStore.inMemory(new HashMap<>()), new FileContentStore(directory));
        assertEquals("legacy", repository.loadNotes());
        String large = "中文便签\n".repeat(20_000);
        repository.saveNotes(large);
        assertEquals("legacy", legacy.get("notesListSerialized"));
        assertEquals(large, new FileContentStore(directory).load("notesListSerialized", () -> "old"));
    }

    @Test void restoresBackupAndRefusesToSilentlyOverwriteUnrecoverableCorruption() throws Exception {
        var store = new FileContentStore(directory);
        store.save("notes", "first");
        store.save("notes", "second");
        Files.writeString(directory.resolve("notes.data"), "broken");
        assertEquals("first", store.load("notes", () -> "legacy"));
        store.save("notes", "recovered");
        assertEquals("recovered", store.load("notes", () -> "legacy"));
        Files.writeString(directory.resolve("fresh.data"), "broken");
        assertThrows(UncheckedIOException.class, () -> store.load("fresh", () -> "legacy"));
        assertThrows(UncheckedIOException.class, () -> store.save("fresh", "replacement"));
        assertEquals("broken", Files.readString(directory.resolve("fresh.data")));
    }

    @Test void migrationWriteFailureRetainsLegacyAndNoTemporaryFilesRemain() throws Exception {
        Path blocked = directory.resolve("blocked");
        Files.writeString(blocked, "not a directory");
        var store = new FileContentStore(blocked);
        assertThrows(UncheckedIOException.class, () -> store.load("notes", () -> "legacy"));
        try (var files = Files.list(directory)) {
            assertTrue(files.noneMatch(path -> path.toString().endsWith(".tmp")));
        }
    }
}
