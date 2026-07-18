package com.cn.schrodinger.understatus.toolbox.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * High performance, thread-safe hash digest calculator.
 * Supports MD5, SHA-1, SHA-256, and SHA-512.
 *
 * @author peter/antigravity
 */
public class HashCalculator {

    public static String calculateHash(String input, String algorithm) {
        if (input == null) {
            return "";
        }
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) {
            return "Hash calculation failed: " + ex.getMessage();
        }
    }
}
