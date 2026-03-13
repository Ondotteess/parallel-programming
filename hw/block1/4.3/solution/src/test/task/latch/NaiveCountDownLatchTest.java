package task.latch;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NaiveCountDownLatchTest {

    @Test
    void constructorRejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> new NaiveCountDownLatch(-1));
    }

    @Test
    void awaitReturnsWhenZero() throws Exception {
        NaiveCountDownLatch latch = new NaiveCountDownLatch(0);
        latch.await();
        assertEquals(0, latch.getCount());
    }

    @Test
    void waiterReleasedAfterOpen() throws Exception {
        NaiveCountDownLatch latch = new NaiveCountDownLatch(1);
        AtomicBoolean passed = new AtomicBoolean(false);
        CountDownLatch enteredAwait = new CountDownLatch(1);

        Thread t = new Thread(() -> {
            try {
                enteredAwait.countDown();
                latch.await();
                passed.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        t.start();
        enteredAwait.await();

        assertFalse(passed.get());

        latch.countDown();
        t.join(1000);

        assertFalse(t.isAlive(), "waiter thread must finish");
        assertTrue(passed.get());
        assertEquals(0, latch.getCount());
    }

    @Test
    void allWaitersReleased() throws Exception {
        int waiters = 6;
        NaiveCountDownLatch latch = new NaiveCountDownLatch(1);

        CountDownLatch ready = new CountDownLatch(waiters);
        AtomicInteger passed = new AtomicInteger(0);
        Thread[] threads = new Thread[waiters];

        for (int i = 0; i < waiters; i++) {
            threads[i] = new Thread(() -> {
                try {
                    ready.countDown();
                    latch.await();
                    passed.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "naive-waiter-" + i);
            threads[i].start();
        }

        ready.await();
        latch.countDown();

        for (Thread thread : threads) {
            thread.join(1000);
            assertFalse(thread.isAlive(), "all waiter threads must finish");
        }

        assertEquals(waiters, passed.get());
        assertEquals(0, latch.getCount());
    }

    @Test
    void awaitAfterOpenIsImmediate() throws Exception {
        NaiveCountDownLatch latch = new NaiveCountDownLatch(1);
        latch.countDown();

        long before = System.nanoTime();
        latch.await();
        long after = System.nanoTime();

        assertEquals(0, latch.getCount());
        assertTrue(after >= before);
    }

    @Test
    void awaitCanBeInterrupted() throws Exception {
        NaiveCountDownLatch latch = new NaiveCountDownLatch(1);
        CountDownLatch ready = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean(false);

        Thread t = new Thread(() -> {
            try {
                ready.countDown();
                latch.await();
            } catch (InterruptedException e) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
            }
        });

        t.start();
        ready.await();

        t.interrupt();
        t.join(1000);

        assertFalse(t.isAlive(), "interrupted waiter thread must finish");
        assertTrue(interrupted.get(), "await must throw InterruptedException");
        assertEquals(1, latch.getCount());
    }

    @Test
    void extraCountDownKeepsZero() {
        NaiveCountDownLatch latch = new NaiveCountDownLatch(1);

        latch.countDown();
        latch.countDown();
        latch.countDown();

        assertEquals(0, latch.getCount());
    }
}