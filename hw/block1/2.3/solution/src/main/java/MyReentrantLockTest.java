import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

public class MyReentrantLockTest {

    private static final class NonReentrantLockMock implements NonReentrantLock {
        private final ReentrantLock lock = new ReentrantLock();
        private Thread owner = null;

        @Override
        public void lock() {
            Thread cur = Thread.currentThread();
            lock.lock();
            if (owner == cur) {
                lock.unlock();
                throw new IllegalMonitorStateException("NonReentrantLock re-lock by same thread");
            }
            owner = cur;
        }

        @Override
        public void unlock() {
            Thread cur = Thread.currentThread();
            if (owner != cur) {
                throw new IllegalMonitorStateException("unlock by non-owner");
            }
            owner = null;
            lock.unlock();
        }
    }

    private static final class FactoryMock implements NonReentrantLockFactory {
        @Override
        public NonReentrantLock create() {
            return new NonReentrantLockMock();
        }
    }

    @Test
    void reentrancy_singleThread() {
        MyReentrantLock r = new MyReentrantLock(new FactoryMock());
        r.lock();
        r.lock();
        r.lock();
        r.unlock();
        r.unlock();
        r.unlock();
    }

    @Test
    void tryLock_works() {
        MyReentrantLock r = new MyReentrantLock(new FactoryMock());
        assertTrue(r.tryLock());
        assertTrue(r.tryLock());
        r.unlock();
        r.unlock();
    }

    @Test
    void unlockByNonOwner_throws() throws Exception {
        MyReentrantLock r = new MyReentrantLock(new FactoryMock());
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);

        Thread t1 = new Thread(() -> {
            r.lock();
            locked.countDown();
            try {
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            } finally {
                r.unlock();
                done.countDown();
            }
        });

        t1.start();
        assertTrue(locked.await(1, TimeUnit.SECONDS));

        assertThrows(IllegalMonitorStateException.class, r::unlock);

        assertTrue(done.await(1, TimeUnit.SECONDS));
        t1.join();
    }

    @Test
    void mutualExclusion_manyThreads_counterCorrect() throws Exception {
        MyReentrantLock r = new MyReentrantLock(new FactoryMock());
        int threads = 8;
        int iters = 50_000;

        int[] counter = {0};
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        List<Thread> ts = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Thread t = new Thread(() -> {
                try {
                    start.await();
                    for (int k = 0; k < iters; k++) {
                        r.lock();
                        try {
                            counter[0]++;
                        } finally {
                            r.unlock();
                        }
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    done.countDown();
                }
            });
            ts.add(t);
            t.start();
        }

        start.countDown();
        assertTrue(done.await(15, TimeUnit.SECONDS));
        for (Thread t : ts) t.join();

        assertEquals(threads * iters, counter[0]);
    }

    @Test
    void blocksOtherThread_untilFullyUnlocked() throws Exception {
        MyReentrantLock r = new MyReentrantLock(new FactoryMock());

        CountDownLatch t1Locked = new CountDownLatch(1);
        CountDownLatch t2Entered = new CountDownLatch(1);

        Thread t1 = new Thread(() -> {
            r.lock();
            r.lock(); // holdCount=2
            t1Locked.countDown();
            try {
                try { Thread.sleep(300); } catch (InterruptedException ignored) {}
            } finally {
                r.unlock(); // holdCount=1
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                r.unlock(); // holdCount=0
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                assertTrue(t1Locked.await(1, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            r.lock();
            try {
                t2Entered.countDown();
            } finally {
                r.unlock();
            }
        });

        t1.start();
        t2.start();

        assertFalse(t2Entered.await(350, TimeUnit.MILLISECONDS));
        assertTrue(t2Entered.await(2, TimeUnit.SECONDS));

        t1.join();
        t2.join();
    }
}