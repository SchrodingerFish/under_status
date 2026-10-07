package com.cn.schrodinger.understatus;

import com.cn.schrodinger.understatus.toolbox.notes.Note;
import com.cn.schrodinger.understatus.toolbox.notes.NoteCodec;
import com.cn.schrodinger.understatus.toolbox.notes.NotesSession;
import com.cn.schrodinger.understatus.settings.ContentStore;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotesTabPanelTest {

    @Test
    void recoveryWarningUsesTheReadResultInsteadOfSeparateMutableMetadata() throws Exception {
        Queue<Runnable> worker = new ArrayDeque<>();
        NotesSession.Storage storage = new NotesSession.Storage() {
            @Override public String load() { return new NoteCodec().encode(List.of(new Note("backup", "text"))); }
            @Override public String warning() { return "Unrelated favorites warning"; }
            @Override public ContentStore.ReadResult loadResult() {
                return new ContentStore.ReadResult(load(), "Notes recovered from backup");
            }
            @Override public void save(String value) { }
        };
        AtomicReference<NotesSession> session = new AtomicReference<>();
        edt(() -> {
            session.set(new NotesSession(storage, worker::add, 60_000));
            session.get().ensureLoaded();
        });
        worker.remove().run();
        edt(() -> assertEquals("Notes recovered from backup", session.get().detail()));
    }

    @Test
    void successfulSaveRetryClearsTheWarningFromRecoveredContent() throws Exception {
        Fixture f = new Fixture();
        f.storage.warning = "Recovered fixture backup";
        NotesTabPanel panel = f.open();
        f.complete();
        edt(() -> {
            assertEquals("Recovered fixture backup", f.session.detail());
            find(panel, "notes-editor", JTextArea.class).append(" repaired");
            panel.flushSaveToPreferences();
        });
        f.storage.failSave = true;
        f.complete();
        f.storage.failSave = false;
        edt(f.session::retry);
        f.complete();
        edt(() -> {
            assertEquals("", f.session.detail(), "Successful persistence resolves the recovery warning");
            assertEquals("已保存", f.session.statusText());
            panel.removeNotify();
        });
    }

    @Test
    void disposalDetachesTheEditorAndReattachmentUsesTheLiveDocument() throws Exception {
        Fixture f = new Fixture();
        NotesTabPanel panel = f.open();
        f.complete();
        edt(() -> {
            JTextArea editor = find(panel, "notes-editor", JTextArea.class);
            var liveDocument = editor.getDocument();
            panel.removeNotify();
            assertNotSame(liveDocument, editor.getDocument(), "Closed editors must release shared document listeners");
            panel.addNotify();
            assertSame(liveDocument, editor.getDocument());
            panel.removeNotify();
        });
    }

    @Test
    void completionOfOlderSaveCannotMarkNewerEditsSaved() throws Exception {
        Fixture f = new Fixture();
        NotesTabPanel panel = f.open();
        f.complete();
        edt(() -> {
            JTextArea editor = find(panel, "notes-editor", JTextArea.class);
            editor.append(" saved");
            panel.flushSaveToPreferences();
            editor.append(" newer");
        });
        f.complete();
        edt(() -> {
            assertTrue(f.session.hasPendingChanges());
            assertEquals("first saved newer", find(panel, "notes-editor", JTextArea.class).getText());
            panel.removeNotify();
        });
        f.complete();
        assertEquals("first saved newer", f.storage.savedNotes().get(0).content());
        edt(() -> assertFalse(f.session.hasPendingChanges()));
    }

    @Test
    void failedInflightSaveRetainsTheLatestCoalescedSnapshotForRetry() throws Exception {
        Fixture f = new Fixture();
        NotesTabPanel panel = f.open();
        f.complete();
        f.storage.failSave = true;
        edt(() -> {
            JTextArea editor = find(panel, "notes-editor", JTextArea.class);
            editor.append(" initial");
            panel.flushSaveToPreferences();
            editor.append(" latest");
            panel.removeNotify();
        });
        f.complete();
        assertEquals(0, f.worker.size(), "Save failures must not spin retry loops");
        NotesTabPanel reopened = f.open();
        f.storage.failSave = false;
        edt(() -> find(reopened, "notes-retry", JButton.class).doClick());
        f.complete();
        assertEquals("first initial latest", f.storage.savedNotes().get(0).content());
        edt(reopened::removeNotify);
    }

    @Test
    void deletingTheLastNoteCreatesOneEditableReplacement() throws Exception {
        Fixture f = new Fixture();
        NotesTabPanel panel = f.open();
        f.complete();
        edt(() -> {
            f.session.delete(f.session.entries().get(0));
            f.session.delete(f.session.entries().get(0));
            assertEquals(1, f.session.entries().size());
            JTextArea editor = find(panel, "notes-editor", JTextArea.class);
            assertEquals("", editor.getText());
            assertTrue(editor.isEnabled());
            editor.append("replacement");
            panel.removeNotify();
        });
        f.drain();
        assertEquals(List.of(new Note("便签 1", "replacement")), f.storage.savedNotes());
    }

    @Test
    void switchingRenamingAndDeletingKeepTextWithItsNote() throws Exception {
        Fixture f = new Fixture();
        NotesTabPanel panel = f.open();
        assertEquals(1, f.worker.size());
        f.complete();
        edt(() -> {
            JList<?> list = find(panel, "notes-list", JList.class);
            JTextArea editor = find(panel, "notes-editor", JTextArea.class);
            editor.append(" edited");
            list.setSelectedIndex(1);
            assertEquals("second", editor.getText());
            editor.append(" changed");
            f.session.rename(f.session.entries().get(1), "renamed");
            list.setSelectedIndex(0);
            assertEquals("first edited", editor.getText());
            f.session.delete(f.session.entries().get(0));
            assertEquals("second changed", editor.getText());
            assertEquals("renamed", f.session.entries().get(0).title());
            panel.flushSaveToPreferences();
            panel.removeNotify();
        });
        f.drain();
        assertEquals(List.of(new Note("renamed", "second changed")), f.storage.savedNotes());
    }

    @Test
    void multiplePanelsShareEditsAndOnlyQueueOneSave() throws Exception {
        Fixture f = new Fixture();
        NotesTabPanel first = f.open();
        NotesTabPanel second = f.open();
        assertEquals(1, f.worker.size());
        f.complete();
        edt(() -> {
            JTextArea left = find(first, "notes-editor", JTextArea.class);
            JTextArea right = find(second, "notes-editor", JTextArea.class);
            assertSame(left.getDocument(), right.getDocument());
            left.append(" A");
            assertEquals(0, f.worker.size(), "Typing waits for a debounce snapshot");
            first.flushSaveToPreferences();
            for (int i = 0; i < 100; i++) {
                right.append(" B");
                second.flushSaveToPreferences();
            }
            assertEquals(left.getText(), right.getText());
            assertEquals(1, f.worker.size(), "Only one in-flight save is retained");
            first.removeNotify();
            second.removeNotify();
        });
        f.drain();
        assertEquals(2, f.storage.writes.size());
        assertEquals("first A" + " B".repeat(100), f.storage.savedNotes().get(0).content());
    }

    @Test
    void failedSaveSurvivesDisposalAndReopeningUntilRetrySucceeds() throws Exception {
        Fixture f = new Fixture();
        NotesTabPanel panel = f.open();
        f.complete();
        f.storage.failSave = true;
        edt(() -> {
            find(panel, "notes-editor", JTextArea.class).append(" unsaved");
            panel.removeNotify();
        });
        f.complete();
        NotesTabPanel reopened = f.open();
        edt(() -> {
            assertEquals("first unsaved", find(reopened, "notes-editor", JTextArea.class).getText());
            assertTrue(f.session.hasPendingChanges());
            assertTrue(find(reopened, "notes-retry", JButton.class).isVisible());
        });
        assertEquals(1, f.storage.loads);
        f.storage.failSave = false;
        edt(() -> find(reopened, "notes-retry", JButton.class).doClick());
        f.complete();
        edt(() -> {
            assertFalse(f.session.hasPendingChanges());
            assertFalse(find(reopened, "notes-retry", JButton.class).isVisible());
            reopened.removeNotify();
        });
        assertEquals("first unsaved", f.storage.savedNotes().get(0).content());
    }

    @Test
    void failedLoadNeverSavesBlankDefaultsAndCanBeRetried() throws Exception {
        Fixture f = new Fixture();
        f.storage.failLoad = true;
        NotesTabPanel panel = f.open();
        f.complete();
        edt(() -> {
            assertFalse(find(panel, "notes-editor", JTextArea.class).isEnabled());
            assertTrue(find(panel, "notes-retry", JButton.class).isVisible());
            panel.flushSaveToPreferences();
            panel.removeNotify();
        });
        assertTrue(f.storage.writes.isEmpty());
        assertEquals(0, f.worker.size());
        NotesTabPanel reopened = f.open();
        f.storage.failLoad = false;
        edt(() -> find(reopened, "notes-retry", JButton.class).doClick());
        f.complete();
        edt(() -> {
            assertEquals("first", find(reopened, "notes-editor", JTextArea.class).getText());
            reopened.removeNotify();
        });
        assertTrue(f.storage.writes.isEmpty());
    }

    @Test
    void malformedPayloadCannotBecomeAnEmptyEditableNotebook() throws Exception {
        Fixture f = new Fixture();
        f.storage.data = "v1\n%%%";
        NotesTabPanel panel = f.open();
        f.complete();
        edt(() -> {
            assertFalse(f.session.isLoaded());
            assertTrue(find(panel, "notes-retry", JButton.class).isVisible());
            panel.removeNotify();
        });
        assertTrue(f.storage.writes.isEmpty());
    }

    private static void edt(Runnable action) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try { action.run(); } catch (Throwable ex) { failure.set(ex); }
        });
        if (failure.get() instanceof Error error) throw error;
        if (failure.get() instanceof Exception exception) throw exception;
    }

    private static <T extends Component> T find(Container parent, String name, Class<T> type) {
        for (Component child : parent.getComponents()) {
            if (name.equals(child.getName())) return type.cast(child);
            if (child instanceof Container nested) {
                T found = find(nested, name, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static final class Fixture {
        final Queue<Runnable> worker = new ArrayDeque<>();
        final MemoryStorage storage = new MemoryStorage();
        final NotesSession session;

        Fixture() throws Exception {
            AtomicReference<NotesSession> result = new AtomicReference<>();
            edt(() -> result.set(new NotesSession(storage, (Executor) worker::add, 60_000)));
            session = result.get();
        }

        NotesTabPanel open() throws Exception {
            AtomicReference<NotesTabPanel> result = new AtomicReference<>();
            edt(() -> result.set(new NotesTabPanel(session)));
            return result.get();
        }

        void complete() throws Exception {
            worker.remove().run();
            edt(() -> {});
        }

        void drain() throws Exception {
            while (!worker.isEmpty()) complete();
        }
    }

    private static final class MemoryStorage implements NotesSession.Storage {
        String data = new NoteCodec().encode(List.of(new Note("one", "first"), new Note("two", "second")));
        final List<String> writes = new ArrayList<>();
        boolean failLoad;
        boolean failSave;
        String warning = "";
        int loads;

        @Override public String load() {
            assertFalse(SwingUtilities.isEventDispatchThread(), "Load must be off the EDT");
            loads++;
            if (failLoad) throw new IllegalStateException("read failed");
            return data;
        }

        @Override public String warning() { return warning; }

        @Override public void save(String value) {
            assertFalse(SwingUtilities.isEventDispatchThread(), "Save must be off the EDT");
            if (failSave) throw new IllegalStateException("write failed");
            data = value;
            writes.add(value);
        }

        List<Note> savedNotes() { return new NoteCodec().decode(data); }
    }
}
