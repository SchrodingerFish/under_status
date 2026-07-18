package com.cn.schrodinger.understatus;

import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.EditorRegistry;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;

/**
 * File operations related to obtaining active documents, files, and toggling permissions.
 *
 * @author peter/antigravity
 */
public class FileUtils {

    public static DataObject getActiveDataObject() {
        JTextComponent editor = EditorRegistry.lastFocusedComponent();
        if (editor != null) {
            Document doc = editor.getDocument();
            Object desc = doc.getProperty(Document.StreamDescriptionProperty);
            if (desc instanceof DataObject) {
                return (DataObject) desc;
            }
        }
        return null;
    }

    public static boolean isFileWritable(DataObject dobj) {
        if (dobj == null) return true;
        FileObject fo = dobj.getPrimaryFile();
        if (fo == null) return true;
        java.io.File file = FileUtil.toFile(fo);
        if (file != null) {
            return file.canWrite();
        }
        return true;
    }

    public static boolean toggleFileReadOnly(DataObject dobj) {
        if (dobj == null) return false;
        FileObject fo = dobj.getPrimaryFile();
        if (fo == null) return false;
        java.io.File file = FileUtil.toFile(fo);
        if (file != null) {
            boolean currentWritable = file.canWrite();
            boolean success = file.setWritable(!currentWritable);
            if (success) {
                fo.refresh();
                return true;
            }
        }
        return false;
    }
}
