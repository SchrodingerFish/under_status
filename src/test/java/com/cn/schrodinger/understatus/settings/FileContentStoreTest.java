package com.cn.schrodinger.understatus.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void recoveryMetadataStaysWithItsPayloadAcrossOtherReadsAndWrites() throws Exception {
        FileContentStore store = new FileContentStore(directory);
        store.write("notes", "notes backup");
        store.write("notes", "notes latest");
        store.write("favorites", "favorites backup");
        store.write("favorites", "favorites latest");
        Files.writeString(directory.resolve("notes.data"), "corrupt");

        ContentStore.ReadResult notes = store.readResult("notes").orElseThrow();
        ContentStore.ReadResult favorites = store.readResult("favorites").orElseThrow();
        store.write("favorites", "favorites saved");
        assertEquals("notes backup", notes.value());
        assertFalse(notes.warning().isBlank(), "Another key cannot erase this recovery warning");
        assertEquals("favorites latest", favorites.value());
        assertTrue(favorites.warning().isEmpty(), "Notes recovery cannot mark clean favorites as recovered");

        Files.writeString(directory.resolve("favorites.data"), "corrupt");
        ContentStore.ReadResult recoveredFavorites = store.readResult("favorites").orElseThrow();
        store.write("notes", "notes repaired");
        ContentStore.ReadResult repairedNotes = store.readResult("notes").orElseThrow();
        assertFalse(recoveredFavorites.warning().isBlank());
        assertTrue(repairedNotes.warning().isEmpty());
        assertFalse(notes.warning().isBlank(), "Results describe their original read forever");
    }

    @Test
    void repositoryReturnsRecoveryMetadataWithContentAndKeepsLegacyLoadApis() throws Exception {
        FileContentStore files = new FileContentStore(directory);
        SettingsRepository repository = new SettingsRepository(SettingsStore.inMemory(new HashMap<>()),
                SecretStore.inMemory(new HashMap<>()), files);
        repository.saveNotes("backup");
        repository.saveNotes("latest");
        repository.saveFavorites("clean favorites");
        Files.writeString(directory.resolve("notes.data"), "corrupt");

        ContentStore.ReadResult notes = repository.loadNotesResult();
        ContentStore.ReadResult favorites = repository.loadFavoritesResult();
        assertEquals("backup", notes.value());
        assertFalse(notes.warning().isBlank());
        assertEquals("clean favorites", favorites.value());
        assertTrue(favorites.warning().isEmpty());
        assertEquals(notes.value(), repository.loadNotes());
        assertEquals(favorites.value(), repository.loadFavorites());
    }

    @Test
    void savesContentLargerThanPreferencesLimitAndPreservesBackup() throws Exception {
        FileContentStore store = new FileContentStore(directory);
        String large = "中文 notes \n".repeat(2000);
        store.write("notes", large);
        store.write("notes", "second");
        assertEquals("second", store.read("notes").orElseThrow());
        Files.writeString(directory.resolve("notes.data"), "corrupt");
        assertEquals(large, store.read("notes").orElseThrow());
        assertFalse(store.warning().isBlank());
        store.write("notes", "repaired");
        assertEquals("repaired", store.read("notes").orElseThrow());
        Files.writeString(directory.resolve("notes.data"), "corrupt again");
        assertEquals(large, store.read("notes").orElseThrow());
    }

    @Test
    void corruptContentFailsInsteadOfReturningEmptyAndPathsStayLocal() throws Exception {
        FileContentStore store = new FileContentStore(directory);
        Files.writeString(directory.resolve("notes.data"), "corrupt");
        assertThrows(UncheckedIOException.class, () -> store.read("notes"));
        assertEquals("corrupt", Files.readString(directory.resolve("notes.data")));
        assertThrows(IllegalArgumentException.class, () -> store.write("../escape", "x"));
    }

    @Test
    void legacyDataMigratesOnceAndEmptySaveDoesNotResurrectIt() {
        HashMap<String, String> preferences = new HashMap<>();
        preferences.put("notesListSerialized", "legacy");
        FileContentStore files = new FileContentStore(directory);
        SettingsRepository repository = new SettingsRepository(SettingsStore.inMemory(preferences),
                SecretStore.inMemory(new HashMap<>()), files);
        assertEquals("legacy", repository.loadNotes());
        assertEquals("legacy", files.read("notes").orElseThrow());
        assertEquals("legacy", preferences.get("notesListSerialized"));
        repository.saveNotes("");
        assertEquals("", repository.loadNotes());
    }

    @Test
    void unavailableDirectoryReportsSaveFailure() throws Exception {
        Path file = directory.resolve("not-a-directory");
        Files.writeString(file, "keep");
        FileContentStore store = new FileContentStore(file);
        assertThrows(UncheckedIOException.class, () -> store.write("notes", "value"));
        assertEquals("keep", Files.readString(file));
        assertTrue(Files.isRegularFile(file));
    }
}
