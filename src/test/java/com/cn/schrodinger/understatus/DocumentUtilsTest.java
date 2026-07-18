package com.cn.schrodinger.understatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.swing.text.PlainDocument;
import org.junit.jupiter.api.Test;

class DocumentUtilsTest {

    @Test
    void countsCharactersAndLines() throws Exception {
        PlainDocument document = new PlainDocument();
        document.insertString(0, "alpha\nbeta\ngamma", null);

        assertEquals(16, DocumentUtils.getCharCount(document));
        assertEquals(3, DocumentUtils.getLineCount(document));
    }

    @Test
    void fallsBackToDocumentEncodingProperty() {
        PlainDocument document = new PlainDocument();
        document.putProperty("encoding", "GB18030");

        assertEquals("GB18030", DocumentUtils.getFileEncoding(document));
    }

    @Test
    void returnsDefaultEncodingWhenDocumentMissing() {
        String encoding = DocumentUtils.getFileEncoding((javax.swing.text.Document) null);
        assertNotNull(encoding);
        assertEquals(false, encoding.isBlank());
    }
}
