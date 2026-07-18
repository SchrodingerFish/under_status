package com.cn.schrodinger.understatus.statusbar;

import com.cn.schrodinger.understatus.DocumentUtils;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorRegistry;

/**
 * Tracks the last-focused editor document and pushes line/char/encoding metrics
 * to a consumer. Listens to {@link EditorRegistry} focus changes and document edits.
 */
public final class EditorMetricsTracker implements AutoCloseable {

    private final Consumer<EditorMetrics> consumer;
    private final Timer debounceTimer;
    private final PropertyChangeListener editorListener = this::editorChanged;
    private final DocumentListener documentListener = new DocumentListener() {
        @Override public void insertUpdate(DocumentEvent event) { scheduleRefresh(); }
        @Override public void removeUpdate(DocumentEvent event) { scheduleRefresh(); }
        @Override public void changedUpdate(DocumentEvent event) { scheduleRefresh(); }
    };
    private Document currentDocument;
    private boolean started;

    public EditorMetricsTracker(Consumer<EditorMetrics> consumer) {
        this.consumer = Objects.requireNonNull(consumer);
        this.debounceTimer = new Timer(250, event -> refreshNow());
        this.debounceTimer.setRepeats(false);
    }

    public void start() {
        if (started) {
            return;
        }
        started = true;
        EditorRegistry.addPropertyChangeListener(editorListener);
        switchDocument(EditorRegistry.lastFocusedComponent());
    }

    public void refreshNow() {
        JTextComponent editor = EditorRegistry.lastFocusedComponent();
        if (editor == null || editor.getDocument() != currentDocument) {
            switchDocument(editor);
            return;
        }
        publish(currentDocument);
    }

    public static EditorMetrics calculate(Document document, String encoding) {
        if (document == null) {
            return EditorMetrics.unavailable();
        }
        javax.swing.text.Element root = document.getDefaultRootElement();
        int lines = root == null ? 0 : root.getElementCount();
        return new EditorMetrics(lines, document.getLength(), encoding, true);
    }

    private void editorChanged(PropertyChangeEvent event) {
        String name = event.getPropertyName();
        if (EditorRegistry.FOCUS_GAINED_PROPERTY.equals(name)
                || EditorRegistry.FOCUS_LOST_PROPERTY.equals(name)
                || EditorRegistry.FOCUSED_DOCUMENT_PROPERTY.equals(name)
                || EditorRegistry.COMPONENT_REMOVED_PROPERTY.equals(name)
                || EditorRegistry.LAST_FOCUSED_REMOVED_PROPERTY.equals(name)) {
            SwingUtilities.invokeLater(() -> switchDocument(EditorRegistry.lastFocusedComponent()));
        }
    }

    private void switchDocument(JTextComponent editor) {
        Document next = editor == null ? null : editor.getDocument();
        if (currentDocument != null && currentDocument != next) {
            currentDocument.removeDocumentListener(documentListener);
        }
        if (next != null && next != currentDocument) {
            next.addDocumentListener(documentListener);
        }
        currentDocument = next;
        publish(currentDocument);
    }

    private void publish(Document document) {
        EditorMetrics metrics = document == null
                ? EditorMetrics.unavailable()
                : calculate(document, DocumentUtils.getFileEncoding(document));
        deliver(metrics);
    }

    private void deliver(EditorMetrics metrics) {
        if (SwingUtilities.isEventDispatchThread()) {
            consumer.accept(metrics);
        } else {
            SwingUtilities.invokeLater(() -> consumer.accept(metrics));
        }
    }

    private void scheduleRefresh() {
        if (started) {
            debounceTimer.restart();
        }
    }

    @Override
    public void close() {
        if (!started) {
            return;
        }
        started = false;
        debounceTimer.stop();
        EditorRegistry.removePropertyChangeListener(editorListener);
        if (currentDocument != null) {
            currentDocument.removeDocumentListener(documentListener);
        }
        currentDocument = null;
    }
}
