package com.cn.schrodinger.understatus.statusbar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.text.PlainDocument;
import org.junit.jupiter.api.Test;

class EditorMetricsTrackerTest {

    @Test
    void calculatesMetricsWithoutReadingDocumentText() throws Exception {
        PlainDocument document = new PlainDocument();
        document.insertString(0, "one\ntwo", null);

        assertEquals(new EditorMetrics(2, 7, "UTF-8", true),
                EditorMetricsTracker.calculate(document, "UTF-8"));
    }

    @Test
    void unavailableMetricsWhenDocumentMissing() {
        EditorMetrics metrics = EditorMetricsTracker.calculate(null, "UTF-8");
        assertFalse(metrics.available());
        assertEquals(0, metrics.lines());
        assertEquals(0, metrics.characters());
        assertEquals(EditorMetrics.unavailable(), metrics);
    }

    @Test
    void emptyDocumentStillAvailableWithOneLine() {
        PlainDocument document = new PlainDocument();
        EditorMetrics metrics = EditorMetricsTracker.calculate(document, "GBK");
        assertTrue(metrics.available());
        assertEquals(1, metrics.lines());
        assertEquals(0, metrics.characters());
        assertEquals("GBK", metrics.encoding());
    }
}
