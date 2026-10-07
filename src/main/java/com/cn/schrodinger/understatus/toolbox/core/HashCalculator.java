package com.cn.schrodinger.understatus.toolbox.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Ultra high performance, thread-safe hash digest calculator.
 * Supports MD5, SHA-1, SHA-256, and SHA-512 with zero intermediate hex String allocations.
 *
 * @author peter/antigravity
 */
public class HashCalculator {

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    public static String calculateHash(String input, String algorithm) {
        if (input == null) {
            return "";
        }
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            
            char[] out = new char[digest.length * 2];
            for (int i = 0; i < digest.length; i++) {
                int v = digest[i] & 0xFF;
                out[i * 2] = HEX_CHARS[v >>> 4];
                out[i * 2 + 1] = HEX_CHARS[v & 0x0F];
            }
            return new String(out);
        } catch (Exception ex) {
            return "Hash calculation failed: " + ex.getMessage();
        }
    }
}
