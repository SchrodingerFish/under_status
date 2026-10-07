package com.cn.schrodinger.understatus;

import org.openide.awt.StatusDisplayer;

/**
 * Common helper utilities (clipboard operations, random string generators, etc.).
 *
 * @author peter/antigravity
 */
public class CommonUtils {

    private static final java.security.SecureRandom PASSWORD_RANDOM = new java.security.SecureRandom();

    public static void copyToClipboard(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        try {
            java.awt.datatransfer.Clipboard clipboard = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
            java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(text);
            clipboard.setContents(selection, null);
            StatusDisplayer.getDefault().setStatusText("已成功复制到剪贴板 (Copied to Clipboard)");
        } catch (Exception ex) {
            java.util.logging.Logger.getLogger(CommonUtils.class.getName()).log(java.util.logging.Level.FINE, "Clipboard copy failed", ex);
        }
    }

    public static String getClipboardText() {
        try {
            java.awt.datatransfer.Clipboard clipboard = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard();
            java.awt.datatransfer.Transferable contents = clipboard.getContents(null);
            if (contents != null && contents.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor)) {
                return (String) contents.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor);
            }
        } catch (Exception ex) {
            java.util.logging.Logger.getLogger(CommonUtils.class.getName()).log(java.util.logging.Level.FINE, "Clipboard read failed", ex);
        }
        return "";
    }

    public static String generateRandomString(int len) {
        if (len < 0 || len > 4096) throw new IllegalArgumentException("密码长度必须在 0 到 4096 之间");
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            int idx = PASSWORD_RANDOM.nextInt(chars.length());
            sb.append(chars.charAt(idx));
        }
        return sb.toString();
    }
}
