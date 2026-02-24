package org.nsu.syspro.parprog.counters.benches;

import org.junit.jupiter.api.Test;
import org.nsu.syspro.parprog.counters.impls.SplitCounter;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;


public class SplitCounterTest {

    // ---- basic test #1
    @Test
    void singleThreadIncrementWorks() {
        SplitCounter c = new SplitCounter(8);
        assertEquals(0L, c.get());
        for (int i = 0; i < 1000; i++) c.increment();
        assertEquals(1000L, c.get());
    }

    // ---- basic test #2
    @Test
    void getIsStableWithoutIncrements() {
        SplitCounter c = new SplitCounter(4);
        assertEquals(0L, c.get());
        assertEquals(0L, c.get());
        assertEquals(0L, c.get());
    }

    // ---- stress test #1: exact sum with many threads
    @Test
    void manyThreadsExactSum() throws Exception {
        int threads = Math.max(4, Runtime.getRuntime().availableProcessors() * 2);
        int perThread = 200_000;

        SplitCounter c = new SplitCounter(32);

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < perThread; i++) c.increment();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        fail(e);
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            done.await();
        } finally {
            pool.shutdownNow();
        }

        assertEquals((long) threads * perThread, c.get());
    }

    // ---- stress test #2: concurrent gets during increments
    @Test
    void concurrentGetsDuringIncrements() throws Exception {
        int incThreads = Math.max(2, Runtime.getRuntime().availableProcessors());
        int getThreads = Math.max(2, incThreads / 2);
        int perThread = 150_000;

        SplitCounter c = new SplitCounter(16);
        AtomicBoolean stop = new AtomicBoolean(false);

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch incDone = new CountDownLatch(incThreads);

        ExecutorService pool = Executors.newFixedThreadPool(incThreads + getThreads);
        try {
            // getters
            for (int i = 0; i < getThreads; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        while (!stop.get()) {
                            long v = c.get();
                            assertTrue(v >= 0L);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        fail(e);
                    }
                });
            }

            // incrementers
            for (int t = 0; t < incThreads; t++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < perThread; i++) c.increment();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        fail(e);
                    } finally {
                        incDone.countDown();
                    }
                });
            }

            start.countDown();
            incDone.await();
            stop.set(true);

            assertEquals((long) incThreads * perThread, c.get());
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- stress test #3: oversubscription, plus hang protection
    @Test
    void oversubscriptionDoesNotHangAndIsCorrect() {
        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            int cpu = Math.max(1, Runtime.getRuntime().availableProcessors());
            int threads = cpu * 4;
            int perThread = 50_000;

            SplitCounter c = new SplitCounter(64);

            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threads);

            ExecutorService pool = Executors.newFixedThreadPool(threads);
            try {
                for (int t = 0; t < threads; t++) {
                    pool.submit(() -> {
                        try {
                            start.await();
                            for (int i = 0; i < perThread; i++) c.increment();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            fail(e);
                        } finally {
                            done.countDown();
                        }
                    });
                }

                start.countDown();
                done.await();
            } finally {
                pool.shutdownNow();
            }

            assertEquals((long) threads * perThread, c.get());
        });
    }
}