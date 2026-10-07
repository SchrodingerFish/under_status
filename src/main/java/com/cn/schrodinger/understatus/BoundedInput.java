package com.cn.schrodinger.understatus;

import java.io.IOException;
import java.io.InputStream;

public final class BoundedInput {
    private BoundedInput() {}
    public static byte[] read(InputStream input, int maxBytes) throws IOException {
        byte[] bytes = input.readNBytes(maxBytes + 1);
        if (bytes.length > maxBytes) throw new IOException("响应超过大小限制");
        return bytes;
    }
}
