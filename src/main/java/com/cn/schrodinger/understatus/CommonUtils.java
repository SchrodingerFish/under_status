package com.cn.schrodinger.understatus;

import org.openide.awt.StatusDisplayer;

/**
 * Common helper utilities (clipboard operations, random string generators, etc.).
 *
 * @author peter/antigravity
 */
public class CommonUtils {

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

    public static String generateRandomString(int len) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            int idx = (int)(Math.random() * chars.length());
            sb.append(chars.charAt(idx));
        }
        return sb.toString();
    }
}
