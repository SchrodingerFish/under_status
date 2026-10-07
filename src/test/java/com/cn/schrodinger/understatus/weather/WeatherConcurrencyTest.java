package com.cn.schrodinger.understatus.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class WeatherConcurrencyTest {
    private static final QWeatherConfig CONFIG = new QWeatherConfig("abc.qweatherapi.com", "key", "zh", "m");
    private static final LocationContext LOCATION = new LocationContext("101010100", "北京", 116.41,
            39.92, ZoneId.of("Asia/Shanghai"));

    @Test void overlappingRequestsShareOneHttpCall() throws Exception {
        BlockingTransport transport = new BlockingTransport();
        WeatherDataService service = service(transport);
        FutureTask<WeatherDataService.Result<WeatherNow>> first = request(service);
        Thread owner = start(first);
        try {
            assertTrue(transport.entered.await(5, TimeUnit.SECONDS));
            FutureTask<WeatherDataService.Result<WeatherNow>> second = request(service);
            Thread follower = start(second);
            awaitWaiting(follower);
            assertEquals(1, transport.calls.get());
            transport.release.countDown();
            assertEquals(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS));
            assertEquals(1, transport.calls.get());
            follower.join(5000);
        } finally {
            transport.release.countDown();
            owner.join(5000);
        }
    }

    @Test void invalidationRejectsOldCompletionWithoutOverwritingNewCache() throws Exception {
        BlockingTransport transport = new BlockingTransport();
        WeatherDataService service = service(transport);
        FutureTask<WeatherDataService.Result<WeatherNow>> old = request(service);
        Thread owner = start(old);
        try {
            assertTrue(transport.entered.await(5, TimeUnit.SECONDS));
            service.clearAll();
            WeatherNow current = service.now(CONFIG, LOCATION, false).value();
            assertEquals(22, current.temperatureCelsius());
            transport.release.countDown();
            ExecutionException error = assertThrows(ExecutionException.class, () -> old.get(5, TimeUnit.SECONDS));
            assertEquals(WeatherException.Kind.CANCELLED, ((WeatherException) error.getCause()).kind());
            assertEquals(current, service.now(CONFIG, LOCATION, false).value());
            assertEquals(2, transport.calls.get());
        } finally {
            transport.release.countDown();
            owner.join(5000);
        }
    }

    @Test void interruptedFollowerDoesNotCancelSharedOwner() throws Exception {
        BlockingTransport transport = new BlockingTransport();
        WeatherDataService service = service(transport);
        FutureTask<WeatherDataService.Result<WeatherNow>> first = request(service);
        Thread owner = start(first);
        try {
            assertTrue(transport.entered.await(5, TimeUnit.SECONDS));
            FutureTask<Boolean> followerResult = new FutureTask<>(() -> {
                WeatherException error = assertThrows(WeatherException.class,
                        () -> service.now(CONFIG, LOCATION, false));
                return error.kind() == WeatherException.Kind.CANCELLED && Thread.currentThread().isInterrupted();
            });
            Thread follower = start(followerResult);
            awaitWaiting(follower);
            follower.interrupt();
            assertTrue(followerResult.get(5, TimeUnit.SECONDS));
            transport.release.countDown();
            assertFalse(first.get(5, TimeUnit.SECONDS).stale());
            assertFalse(service.now(CONFIG, LOCATION, false).stale());
            assertEquals(1, transport.calls.get());
            follower.join(5000);
        } finally {
            transport.release.countDown();
            owner.join(5000);
        }
    }

    @Test void interruptedOwnerLetsLiveFollowersShareOneTakeover() throws Exception {
        BlockingTransport transport = new BlockingTransport();
        WeatherDataService service = service(transport);
        FutureTask<WeatherDataService.Result<WeatherNow>> first = request(service, true);
        Thread owner = start(first);
        FutureTask<WeatherDataService.Result<WeatherNow>> second = request(service, true);
        FutureTask<WeatherDataService.Result<WeatherNow>> third = request(service, true);
        Thread follower = null;
        Thread anotherFollower = null;
        try {
            assertTrue(transport.entered.await(5, TimeUnit.SECONDS));
            follower = start(second);
            anotherFollower = start(third);
            awaitWaiting(follower);
            awaitWaiting(anotherFollower);
            assertEquals(1, transport.calls.get());
            owner.interrupt();
            assertCancelled(first);
            WeatherDataService.Result<WeatherNow> result = second.get(5, TimeUnit.SECONDS);
            assertEquals(22, result.value().temperatureCelsius());
            assertFalse(result.stale());
            assertEquals(result, third.get(5, TimeUnit.SECONDS));
            assertFalse(follower.isInterrupted());
            assertFalse(anotherFollower.isInterrupted());
            assertEquals(2, transport.calls.get());
            assertEquals(result, service.now(CONFIG, LOCATION, false));
        } finally {
            transport.release.countDown();
            owner.join(5000);
            if (follower != null) follower.join(5000);
            if (anotherFollower != null) anotherFollower.join(5000);
        }
    }

    @Test void invalidatedFollowersDoNotTakeOverCancelledOwner() throws Exception {
        BlockingTransport transport = new BlockingTransport();
        WeatherDataService service = service(transport);
        FutureTask<WeatherDataService.Result<WeatherNow>> first = request(service);
        Thread owner = start(first);
        FutureTask<WeatherDataService.Result<WeatherNow>> second = request(service);
        Thread follower = null;
        try {
            assertTrue(transport.entered.await(5, TimeUnit.SECONDS));
            follower = start(second);
            awaitWaiting(follower);
            service.clearAll();
            owner.interrupt();
            assertCancelled(first);
            assertCancelled(second);
            assertEquals(1, transport.calls.get());
            assertEquals(22, service.now(CONFIG, LOCATION, false).value().temperatureCelsius());
            assertEquals(2, transport.calls.get());
        } finally {
            transport.release.countDown();
            owner.join(5000);
            if (follower != null) follower.join(5000);
        }
    }

    @Test void invalidationDuringTakeoverPreventsCacheResurrection() throws Exception {
        BlockingTransport transport = new BlockingTransport();
        transport.blockSecond = true;
        WeatherDataService service = service(transport);
        FutureTask<WeatherDataService.Result<WeatherNow>> first = request(service);
        Thread owner = start(first);
        FutureTask<WeatherDataService.Result<WeatherNow>> second = request(service);
        Thread follower = null;
        try {
            assertTrue(transport.entered.await(5, TimeUnit.SECONDS));
            follower = start(second);
            awaitWaiting(follower);
            owner.interrupt();
            assertCancelled(first);
            assertTrue(transport.secondEntered.await(5, TimeUnit.SECONDS));
            service.clearAll();
            transport.releaseSecond.countDown();
            assertCancelled(second);
            assertEquals(2, transport.calls.get());
            service.now(CONFIG, LOCATION, false);
            assertEquals(3, transport.calls.get());
        } finally {
            transport.release.countDown();
            transport.releaseSecond.countDown();
            owner.join(5000);
            if (follower != null) follower.join(5000);
        }
    }

    @Test void cancelledReplacementDoesNotStartAnUnboundedTakeoverLoop() throws Exception {
        BlockingTransport transport = new BlockingTransport();
        WeatherDataService service = service(uri -> {
            transport.get(uri);
            throw new WeatherException(WeatherException.Kind.CANCELLED, "replacement cancelled");
        });
        FutureTask<WeatherDataService.Result<WeatherNow>> first = request(service);
        Thread owner = start(first);
        FutureTask<WeatherDataService.Result<WeatherNow>> second = request(service);
        Thread follower = null;
        try {
            assertTrue(transport.entered.await(5, TimeUnit.SECONDS));
            follower = start(second);
            awaitWaiting(follower);
            owner.interrupt();
            assertCancelled(first);
            assertCancelled(second);
            assertEquals(2, transport.calls.get());
        } finally {
            transport.release.countDown();
            owner.join(5000);
            if (follower != null) follower.join(5000);
        }
    }

    private static void assertCancelled(FutureTask<?> task) {
        ExecutionException error = assertThrows(ExecutionException.class, () -> task.get(5, TimeUnit.SECONDS));
        assertEquals(WeatherException.Kind.CANCELLED, ((WeatherException) error.getCause()).kind());
    }

    @Test void clearingLocationInvalidatesPendingRequest() throws Exception {
        BlockingTransport transport = new BlockingTransport();
        WeatherDataService service = service(transport);
        FutureTask<WeatherDataService.Result<WeatherNow>> old = request(service);
        Thread owner = start(old);
        try {
            assertTrue(transport.entered.await(5, TimeUnit.SECONDS));
            service.clearLocation(LOCATION);
            transport.release.countDown();
            ExecutionException error = assertThrows(ExecutionException.class, () -> old.get(5, TimeUnit.SECONDS));
            assertEquals(WeatherException.Kind.CANCELLED, ((WeatherException) error.getCause()).kind());
            assertEquals(22, service.now(CONFIG, LOCATION, false).value().temperatureCelsius());
            assertEquals(2, transport.calls.get());
        } finally {
            transport.release.countDown();
            owner.join(5000);
        }
    }

    private static WeatherDataService service(HttpTransport transport) {
        return new WeatherDataService(new QWeatherClient(transport),
                new LocationResolver(transport), new WeatherCache());
    }

    private static FutureTask<WeatherDataService.Result<WeatherNow>> request(WeatherDataService service) {
        return request(service, false);
    }

    private static FutureTask<WeatherDataService.Result<WeatherNow>> request(WeatherDataService service,
            boolean force) {
        return new FutureTask<>(() -> service.now(CONFIG, LOCATION, force));
    }

    private static Thread start(FutureTask<?> task) {
        Thread thread = new Thread(task, "weather-test");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static void awaitWaiting(Thread thread) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.isAlive() && System.nanoTime() < deadline) {
            if (thread.getState() == Thread.State.WAITING || thread.getState() == Thread.State.TIMED_WAITING) return;
            Thread.onSpinWait();
        }
        throw new AssertionError("Request did not reach its wait point");
    }

    private static final class BlockingTransport implements HttpTransport {
        private final AtomicInteger calls = new AtomicInteger();
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final CountDownLatch secondEntered = new CountDownLatch(1);
        private final CountDownLatch releaseSecond = new CountDownLatch(1);
        private boolean blockSecond;

        @Override public String get(URI uri) throws WeatherException { return get(uri, Map.of()); }
        @Override public String get(URI uri, Map<String, String> headers) throws WeatherException {
            int call = calls.incrementAndGet();
            if (call == 1) {
                entered.countDown();
                waitForRelease(release);
            } else if (call == 2 && blockSecond) {
                secondEntered.countDown();
                waitForRelease(releaseSecond);
            }
            return "{\"code\":\"200\",\"now\":{\"temp\":\"" + (call == 1 ? 11 : 22)
                    + "\",\"feelsLike\":\"32\",\"icon\":\"100\",\"text\":\"晴\","
                    + "\"wind360\":\"180\",\"windDir\":\"南风\",\"windScale\":\"2\","
                    + "\"windSpeed\":\"8\",\"humidity\":\"48\",\"precip\":\"0.0\","
                    + "\"pressure\":\"1001\",\"vis\":\"18\"}}";
        }

        private static void waitForRelease(CountDownLatch gate) throws WeatherException {
            try {
                if (!gate.await(5, TimeUnit.SECONDS)) throw new AssertionError("Unreleased request");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new WeatherException(WeatherException.Kind.CANCELLED, "interrupted", ex);
            }
        }
    }
}
