package com.cn.schrodinger.understatus.music;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Loopback-only fixtures; no public music service or network dependency. */
class MusicHttpTest {
    @Test void resolvesRelativeRedirectsAndBoundsRedirectLoops() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger loops = new AtomicInteger();
        server.createContext("/relative", exchange -> {
            exchange.getResponseHeaders().add("Location", "result");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/result", exchange -> {
            byte[] response = "fixture".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.createContext("/loop", exchange -> {
            loops.incrementAndGet();
            exchange.getResponseHeaders().add("Location", "/loop");
            exchange.sendResponseHeaders(307, -1);
            exchange.close();
        });
        server.start();
        try {
            URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            assertEquals("fixture", new String(MusicHttp.get(base.resolve("/relative"), 100), StandardCharsets.UTF_8));
            assertThrows(IOException.class, () -> MusicHttp.get(base.resolve("/loop"), 100));
            assertEquals(6, loops.get());
        } finally { server.stop(0); }
    }

    @Test void rejectsOversizedErrorAndInvalidSchemeResponses() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/large", exchange -> {
            byte[] body = new byte[1024];
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/error", exchange -> {
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.createContext("/invalid", exchange -> {
            exchange.getResponseHeaders().add("Location", "file:///tmp/fixture");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        try {
            URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            assertThrows(IOException.class, () -> MusicHttp.get(base.resolve("/large"), 100));
            IOException error = assertThrows(IOException.class, () -> MusicHttp.get(base.resolve("/error"), 100));
            assertTrue(error.getMessage().contains("503"));
            assertThrows(IllegalArgumentException.class, () -> MusicHttp.get(base.resolve("/invalid"), 100));
        } finally { server.stop(0); }
    }
}
