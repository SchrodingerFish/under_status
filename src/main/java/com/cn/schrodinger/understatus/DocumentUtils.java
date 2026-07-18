package com.cn.schrodinger.understatus;

import java.nio.charset.Charset;
import javax.swing.text.Document;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;

/**
 * Utility operations related to active editor document text metrics and properties.
 *
 * @author peter/antigravity
 */
public final class DocumentUtils {

    private DocumentUtils() {
    }

    public static int getCharCount(Document doc) {
        if (doc == null) {
            return 0;
        }
        return doc.getLength();
    }

    public static int getLineCount(Document doc) {
        if (doc == null) {
            return 0;
        }
        javax.swing.text.Element root = doc.getDefaultRootElement();
        if (root == null) {
            return 0;
        }
        return root.getElementCount();
    }

    public static String getFileEncoding(Document doc) {
        if (doc == null) {
            return defaultEncoding();
        }

        // Prefer NetBeans stream description (DataObject → FileObject → FileEncodingQuery).
        Object desc = doc.getProperty(Document.StreamDescriptionProperty);
        if (desc instanceof DataObject) {
            FileObject fo = ((DataObject) desc).getPrimaryFile();
            if (fo != null) {
                String encoding = getFileEncoding(fo);
                if (encoding != null && !encoding.isBlank()) {
                    return encoding;
                }
            }
        } else if (desc instanceof FileObject) {
            String encoding = getFileEncoding((FileObject) desc);
            if (encoding != null && !encoding.isBlank()) {
                return encoding;
            }
        }

        // Fallback: document-level properties used by some editors.
        for (String key : new String[] {"encoding", "textEncoding", "Charset"}) {
            Object value = doc.getProperty(key);
            if (value instanceof Charset) {
                return ((Charset) value).name();
            }
            if (value != null) {
                String name = value.toString().trim();
                if (!name.isEmpty()) {
                    return name;
                }
            }
        }

        return defaultEncoding();
    }

    public static String getFileEncoding(FileObject fileObject) {
        if (fileObject == null) {
            return defaultEncoding();
        }
        try {
            Class<?> feqClass = Class.forName("org.netbeans.api.queries.FileEncodingQuery");
            java.lang.reflect.Method getEncodingMethod =
                    feqClass.getMethod("getEncoding", FileObject.class);
            Object charset = getEncodingMethod.invoke(null, fileObject);
            if (charset instanceof Charset) {
                return ((Charset) charset).name();
            }
            if (charset != null) {
                java.lang.reflect.Method nameMethod = charset.getClass().getMethod("name");
                Object name = nameMethod.invoke(charset);
                if (name != null && !name.toString().isBlank()) {
                    return name.toString();
                }
            }
        } catch (Exception ex) {
            // FileEncodingQuery may be unavailable outside a full IDE runtime.
        }

        Object attr = fileObject.getAttribute("encoding");
        if (attr != null) {
            String name = attr.toString().trim();
            if (!name.isEmpty()) {
                return name;
            }
        }
        return defaultEncoding();
    }

    private static String defaultEncoding() {
        return System.getProperty("file.encoding", "UTF-8");
    }
}
