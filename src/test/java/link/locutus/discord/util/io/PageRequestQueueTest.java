package link.locutus.discord.util.io;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageRequestQueueTest {

    @Test
    void sameDomainRequestsExecuteSequentially() throws Exception {
        URI url = URI.create("https://example.com/api/one");
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxConcurrent = new AtomicInteger();
        java.util.concurrent.CountDownLatch firstStarted = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch allowFirstFinish = new java.util.concurrent.CountDownLatch(1);

        try (PageRequestQueue queue = new PageRequestQueue(2)) {
            PageRequestQueue.PageRequestTask<String> first = queue.submit(() -> {
                int concurrent = active.incrementAndGet();
                maxConcurrent.accumulateAndGet(concurrent, Math::max);
                firstStarted.countDown();
                try {
                    assertTrue(allowFirstFinish.await(2, TimeUnit.SECONDS));
                    return "first";
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                } finally {
                    active.decrementAndGet();
                }
            }, PagePriority.ACTIVE_PAGE, 1, 0, 0, url);

            PageRequestQueue.PageRequestTask<String> second = queue.submit(() -> {
                int concurrent = active.incrementAndGet();
                maxConcurrent.accumulateAndGet(concurrent, Math::max);
                try {
                    return "second";
                } finally {
                    active.decrementAndGet();
                }
            }, PagePriority.ACTIVE_PAGE, 2, 0, 0, url);

            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
            Thread.sleep(150);
            assertFalse(second.isDone(), "same-domain work should wait until the current request finishes");

            allowFirstFinish.countDown();
            assertEquals("first", first.get(2, TimeUnit.SECONDS));
            assertEquals("second", second.get(2, TimeUnit.SECONDS));
            assertEquals(1, maxConcurrent.get(), "same-domain requests must never overlap");
        }
    }

    @Test
    void differentDomainsCanStillRunConcurrently() throws Exception {
        URI firstUrl = URI.create("https://first.example.com/api");
        URI secondUrl = URI.create("https://second.example.com/api");
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxConcurrent = new AtomicInteger();
        java.util.concurrent.CountDownLatch bothStarted = new java.util.concurrent.CountDownLatch(2);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);

        try (PageRequestQueue queue = new PageRequestQueue(2)) {
            PageRequestQueue.PageRequestTask<String> first = queue.submit(() -> {
                int concurrent = active.incrementAndGet();
                maxConcurrent.accumulateAndGet(concurrent, Math::max);
                bothStarted.countDown();
                try {
                    assertTrue(release.await(2, TimeUnit.SECONDS));
                    return "first";
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                } finally {
                    active.decrementAndGet();
                }
            }, PagePriority.ACTIVE_PAGE, 1, 0, 0, firstUrl);

            PageRequestQueue.PageRequestTask<String> second = queue.submit(() -> {
                int concurrent = active.incrementAndGet();
                maxConcurrent.accumulateAndGet(concurrent, Math::max);
                bothStarted.countDown();
                try {
                    assertTrue(release.await(2, TimeUnit.SECONDS));
                    return "second";
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                } finally {
                    active.decrementAndGet();
                }
            }, PagePriority.ACTIVE_PAGE, 2, 0, 0, secondUrl);

            assertTrue(bothStarted.await(2, TimeUnit.SECONDS));
            release.countDown();

            assertEquals("first", first.get(2, TimeUnit.SECONDS));
            assertEquals("second", second.get(2, TimeUnit.SECONDS));
            assertEquals(2, maxConcurrent.get(), "independent domains should still use available workers");
        }
    }

    @Test
    void failingRequestDoesNotCompleteNextRequestExceptionally() throws Exception {
        URI url = URI.create("https://example.com/api/poison");

        try (PageRequestQueue queue = new PageRequestQueue(2)) {
            PageRequestQueue.PageRequestTask<String> failed = queue.submit(() -> {
                throw new IllegalArgumentException("first failure");
            }, PagePriority.ACTIVE_PAGE, 1, 0, 0, url);

            PageRequestQueue.PageRequestTask<String> successful = queue.submit(() -> "ok", PagePriority.ACTIVE_PAGE, 2, 0, 0, url);

            ExecutionException executionException = org.junit.jupiter.api.Assertions.assertThrows(
                    ExecutionException.class,
                    () -> failed.get(2, TimeUnit.SECONDS)
            );
            Throwable cause = executionException.getCause();
            assertInstanceOf(RuntimeException.class, cause);
            assertInstanceOf(IllegalArgumentException.class, cause.getCause());
            assertEquals("first failure", cause.getCause().getMessage());
            assertEquals("ok", successful.get(2, TimeUnit.SECONDS));
        }
    }
}

