package com.cn.schrodinger.understatus.weather;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public final class JdkHttpTransport implements HttpTransport {

    static final int MAX_BODY_BYTES = 2 * 1024 * 1024;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Override
    public String get(URI uri) throws WeatherException {
        return get(uri, Map.of());
    }

    @Override
    public String get(URI uri, Map<String, String> headers) throws WeatherException {
        HttpRequest request = buildRequest(uri, headers);
        try {
            HttpResponse<byte[]> response = client.send(
                    request, responseInfo -> new LimitedBodySubscriber());
            String encoding = response.headers().firstValue("Content-Encoding").orElse("");
            String body;
            try {
                body = decodeBody(response.body(), encoding);
            } catch (IOException ex) {
                throw new WeatherException(WeatherException.Kind.RESPONSE, "天气响应压缩数据无效或过大", ex);
            }
            requireSuccess(response.statusCode(), body);
            return body;
        } catch (HttpTimeoutException ex) {
            throw new WeatherException(WeatherException.Kind.TIMEOUT, "天气请求超时", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new WeatherException(WeatherException.Kind.CANCELLED, "天气请求已中断", ex);
        } catch (IOException ex) {
            for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
                if (cause instanceof BodyTooLargeException) {
                    throw new WeatherException(WeatherException.Kind.RESPONSE, "天气响应过大", ex);
                }
            }
            throw new WeatherException(WeatherException.Kind.NETWORK, "天气服务暂时不可用", ex);
        }
    }

    static HttpRequest buildRequest(URI uri, Map<String, String> headers)
            throws WeatherException {
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new WeatherException(WeatherException.Kind.CONFIGURATION,
                    "天气服务仅允许使用 HTTPS");
        }
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/json")
                .header("Accept-Encoding", "gzip")
                .GET();
        if (headers != null) {
            headers.forEach((name, value) -> {
                if (name != null && value != null && !name.isBlank()) {
                    builder.header(name, value);
                }
            });
        }
        return builder.build();
    }

    static String decodeBody(byte[] body, String contentEncoding) throws IOException {
        if (body.length > MAX_BODY_BYTES) throw new BodyTooLargeException();
        byte[] decoded = body;
        if (contentEncoding != null
                && contentEncoding.toLowerCase(java.util.Locale.ROOT).contains("gzip")) {
            try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(body))) {
                decoded = input.readNBytes(MAX_BODY_BYTES + 1);
                if (decoded.length > MAX_BODY_BYTES) throw new BodyTooLargeException();
            }
        }
        return new String(decoded, StandardCharsets.UTF_8);
    }

    static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        @Override public CompletionStage<byte[]> getBody() { return body; }
        @Override public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            value.request(1);
        }
        @Override public void onNext(List<ByteBuffer> buffers) {
            if (body.isDone()) return;
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > MAX_BODY_BYTES - bytes.size()) {
                    subscription.cancel();
                    body.completeExceptionally(new BodyTooLargeException());
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { body.completeExceptionally(error); }
        @Override public void onComplete() { body.complete(bytes.toByteArray()); }
    }

    private static final class BodyTooLargeException extends IOException {
        BodyTooLargeException() { super("Weather response exceeds byte limit"); }
    }

    static void requireSuccess(int statusCode) throws WeatherException {
        requireSuccess(statusCode, "");
    }

    static void requireSuccess(int statusCode, String body) throws WeatherException {
        if (statusCode == 204 || isDataUnavailable(body)) {
            throw new WeatherException(WeatherException.Kind.UNSUPPORTED,
                    "当前地区暂无该天气数据", statusCode);
        }
        if (statusCode >= 200 && statusCode < 300) {
            return;
        }
        WeatherException.Kind kind = switch (statusCode) {
            case 400 -> WeatherException.Kind.INVALID_REQUEST;
            case 401 -> WeatherException.Kind.AUTHENTICATION;
            case 403 -> WeatherException.Kind.FORBIDDEN;
            case 404 -> WeatherException.Kind.NOT_FOUND;
            case 429 -> WeatherException.Kind.RATE_LIMIT;
            default -> statusCode >= 500
                    ? WeatherException.Kind.UNAVAILABLE : WeatherException.Kind.RESPONSE;
        };
        throw new WeatherException(kind, messageFor(statusCode), statusCode);
    }

    private static boolean isDataUnavailable(String body) {
        if (body == null) return false;
        String normalized = body.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("data-not-available")
                || normalized.contains("data not available");
    }

    private static String messageFor(int statusCode) {
        return switch (statusCode) {
            case 400 -> "天气请求参数或地点无效";
            case 401 -> "天气服务认证失败，请检查 API Key";
            case 403 -> "天气服务拒绝请求，请检查 API Host、权限或额度";
            case 404 -> "天气服务接口不存在";
            case 429 -> "天气请求过于频繁，请稍后再试";
            default -> statusCode >= 500
                    ? "天气服务暂时不可用" : "天气请求失败 (HTTP " + statusCode + ")";
        };
    }
}
