package com.cn.schrodinger.understatus.toolbox.notes;

import com.cn.schrodinger.understatus.settings.SettingsRepository;
import com.cn.schrodinger.understatus.settings.ContentStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;

/**
 * Process-wide notebook model. Documents and public methods belong to the EDT;
 * only immutable snapshots cross to storage. This session outlives its panels,
 * preserving failed writes and avoiding stale snapshots from concurrent editors.
 */
public final class NotesSession {

    public interface Storage {
        String load();
        default ContentStore.ReadResult loadResult() {
            synchronized (this) {
                return new ContentStore.ReadResult(load(), warning());
            }
        }
        void save(String data);
        /** @deprecated Override loadResult to bind recovery metadata to the read. */
        @Deprecated
        default String warning() { return ""; }
    }

    private static final class DefaultHolder {
        private static final NotesSession INSTANCE = new NotesSession(new Storage() {
            @Override public String load() { return SettingsRepository.getDefault().loadNotes(); }
            @Override public ContentStore.ReadResult loadResult() { return SettingsRepository.getDefault().loadNotesResult(); }
            @Override public void save(String data) { SettingsRepository.getDefault().saveNotes(data); }
        }, Executors.newSingleThreadExecutor(task -> {
            Thread worker = new Thread(task, "understatus-notes-storage");
            worker.setDaemon(true);
            return worker;
        }), 500);
    }

    public final class Entry {
        private String title;
        private final PlainDocument document = new PlainDocument();
        private boolean active = true;

        private Entry(Note note) {
            title = note.title();
            try {
                document.insertString(0, note.content(), null);
            } catch (BadLocationException ex) {
                throw new IllegalStateException(ex);
            }
            document.addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent e) { changed(); }
                @Override public void removeUpdate(DocumentEvent e) { changed(); }
                @Override public void changedUpdate(DocumentEvent e) { changed(); }
                private void changed() {
                    if (active) markChanged();
                }
            });
        }

        public String title() { return title; }
        public Document document() { return document; }
        @Override public String toString() { return title; }
    }

    private record Snapshot(long revision, List<Note> notes) { }

    private final Storage storage;
    private final Executor worker;
    private final Timer debounce;
    private final NoteCodec codec = new NoteCodec();
    private final List<Entry> entries = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private boolean loadAttempted;
    private boolean loading;
    private boolean loaded;
    private long revision;
    private long savedRevision;
    private Snapshot inFlight;
    private Snapshot pending;
    private String error = "";
    private String warning = "";

    public NotesSession(Storage storage, Executor worker, int debounceMillis) {
        this.storage = Objects.requireNonNull(storage);
        this.worker = Objects.requireNonNull(worker);
        debounce = new Timer(debounceMillis, e -> flush());
        debounce.setRepeats(false);
    }

    public static NotesSession getDefault() { return DefaultHolder.INSTANCE; }

    public Runnable subscribe(Runnable listener) {
        requireEdt();
        listeners.add(Objects.requireNonNull(listener));
        return () -> {
            requireEdt();
            listeners.remove(listener);
        };
    }

    public List<Entry> entries() { return List.copyOf(entries); }
    public boolean isLoaded() { return loaded; }
    public boolean hasPendingChanges() { return loaded && revision != savedRevision; }
    public boolean canRetry() { return !error.isEmpty() && !loading && inFlight == null; }
    public String detail() { return error.isEmpty() ? warning : error; }

    public String statusText() {
        if (loading) return "正在加载便签…";
        if (!error.isEmpty()) return loaded ? "保存失败；未保存内容仍保留，可重试" : "加载失败；请重试";
        if (inFlight != null) return "正在保存…";
        if (hasPendingChanges()) return "有未保存的更改";
        if (!warning.isEmpty()) return "已从备份恢复便签";
        return loaded ? "已保存" : "正在加载便签…";
    }

    public void ensureLoaded() {
        requireEdt();
        if (!loaded && !loadAttempted) startLoad();
    }

    public void retry() {
        requireEdt();
        if (!loaded) {
            if (!loading) startLoad();
        } else {
            flush();
        }
    }

    private void startLoad() {
        loadAttempted = true;
        loading = true;
        error = "";
        publish();
        try {
            worker.execute(() -> {
                try {
                    ContentStore.ReadResult result = storage.loadResult();
                    String data = result.value();
                    List<Note> notes = codec.decode(data);
                    // Do not silently salvage a subset then overwrite damaged data.
                    if (data != null && !data.isBlank() && data.split("\\R").length != notes.size() * 2) {
                        throw new IllegalStateException("便签数据格式损坏，原文件已保留");
                    }
                    SwingUtilities.invokeLater(() -> finishLoad(notes, result.warning(), null));
                } catch (RuntimeException ex) {
                    SwingUtilities.invokeLater(() -> finishLoad(List.of(), "", ex));
                }
            });
        } catch (RuntimeException ex) {
            finishLoad(List.of(), "", ex);
        }
    }

    private void finishLoad(List<Note> notes, String recovered, RuntimeException failure) {
        requireEdt();
        loading = false;
        if (failure != null) {
            error = failureDetail(failure);
        } else {
            for (Note note : notes) entries.add(new Entry(note));
            if (entries.isEmpty()) entries.add(new Entry(new Note("便签 1", "")));
            loaded = true;
            warning = recovered == null ? "" : recovered;
            error = "";
        }
        publish();
    }

    public Entry add(String title) {
        requireLoaded();
        Entry entry = new Entry(new Note(title, ""));
        entries.add(entry);
        markChanged();
        publish();
        return entry;
    }

    public void delete(Entry entry) {
        requireLoaded();
        if (!entries.remove(entry)) return;
        entry.active = false;
        if (entries.isEmpty()) entries.add(new Entry(new Note("便签 1", "")));
        markChanged();
        publish();
    }

    public void rename(Entry entry, String title) {
        requireLoaded();
        if (!entries.contains(entry) || title == null || title.isBlank()) return;
        if (entry.title.equals(title.trim())) return;
        entry.title = title.trim();
        markChanged();
        publish();
    }

    private void markChanged() {
        requireLoaded();
        boolean wasPending = hasPendingChanges();
        revision++;
        debounce.restart();
        // A keystroke only changes a revision and resets the timer. Never read
        // or copy the full Document from its mutation callback.
        if (!wasPending) publish();
    }

    /** Capture pending edits now; disk IO and encoding always run on the worker. */
    public void flush() {
        requireEdt();
        debounce.stop();
        if (!loaded || !hasPendingChanges()) return;
        long requestedRevision = pending != null ? pending.revision()
                : inFlight != null ? inFlight.revision() : savedRevision;
        if (revision != requestedRevision) pending = snapshot();
        startSave();
    }

    private Snapshot snapshot() {
        List<Note> notes = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            try {
                Document document = entry.document;
                notes.add(new Note(entry.title, document.getText(0, document.getLength())));
            } catch (BadLocationException ex) {
                throw new IllegalStateException(ex);
            }
        }
        return new Snapshot(revision, List.copyOf(notes));
    }

    private void startSave() {
        if (inFlight != null || pending == null) return;
        Snapshot saving = pending;
        pending = null;
        inFlight = saving;
        error = "";
        publish();
        try {
            worker.execute(() -> {
                try {
                    storage.save(codec.encode(saving.notes()));
                    SwingUtilities.invokeLater(() -> finishSave(saving, null));
                } catch (RuntimeException ex) {
                    SwingUtilities.invokeLater(() -> finishSave(saving, ex));
                }
            });
        } catch (RuntimeException ex) {
            finishSave(saving, ex);
        }
    }

    private void finishSave(Snapshot saving, RuntimeException failure) {
        requireEdt();
        inFlight = null;
        if (failure == null) {
            savedRevision = saving.revision();
            error = "";
            warning = "";
            startSave();
        } else {
            // Avoid retry loops. Documents and the latest queued snapshot survive
            // here even when every view has been disposed.
            debounce.stop();
            error = failureDetail(failure);
        }
        publish();
    }

    private void publish() {
        for (Runnable listener : List.copyOf(listeners)) listener.run();
    }

    private void requireLoaded() {
        requireEdt();
        if (!loaded) throw new IllegalStateException("Notes have not loaded");
    }

    private static void requireEdt() {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Notes require the EDT");
    }

    private static String failureDetail(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
