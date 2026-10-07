package com.cn.schrodinger.understatus.music;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;

/** HTTP streams own their connection; redirects and materialized responses are bounded. */
final class MusicHttp {
    private MusicHttp() {}

    static void validate(URI uri) {
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("需要有效的 HTTP(S) 地址");
        }
    }

    static InputStream open(URI initial) throws IOException {
        URI uri = initial;
        for (int redirect = 0; redirect <= 5; redirect++) {
            validate(uri);
            if (Thread.currentThread().isInterrupted()) throw new IOException("请求已取消");
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "UnderStatus/1.1");
            connection.setRequestProperty("Referer", "https://music.163.com/");
            try {
                int status = connection.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.isBlank()) throw new IOException("重定向缺少地址");
                    uri = uri.resolve(location);
                    connection.disconnect();
                    continue;
                }
                if (status < 200 || status >= 300) throw new IOException("HTTP " + status);
                return new FilterInputStream(connection.getInputStream()) {
                    @Override public void close() throws IOException {
                        try { super.close(); } finally { connection.disconnect(); }
                    }
                };
            } catch (IOException | RuntimeException ex) {
                connection.disconnect();
                throw ex;
            }
        }
        throw new IOException("重定向次数过多");
    }

    static byte[] get(URI uri, int limit) throws IOException {
        long deadline = System.nanoTime() + 20_000_000_000L;
        try (InputStream stream = open(uri); ByteArrayOutputStream result = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) throw new IOException("请求已取消");
                if (System.nanoTime() > deadline) throw new IOException("请求超时");
                if (result.size() + read > limit) throw new IOException("响应超过大小限制");
                result.write(buffer, 0, read);
            }
            return result.toByteArray();
        }
    }
}
