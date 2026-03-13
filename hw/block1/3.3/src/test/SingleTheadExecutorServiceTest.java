package org.example.task33;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

class SingleTheadExecutorServiceTest {

    @Test
    void success() throws Exception {
        // проверяет, что успешная задача возвращает результат
        SingleTheadExecutorService executor = new SingleTheadExecutorService(new NamedThreadFactory());

        CondVarFuture<Integer> future = executor.submit(() -> 42);

        assertEquals(42, future.get());
        assertTrue(future.isDone());
    }

    @Test
    void getWaits() throws Exception {
        // проверяет, что get() блокируется до завершения задачи
        SingleTheadExecutorService executor = new SingleTheadExecutorService(new NamedThreadFactory());
        Gate gate = new Gate();

        CondVarFuture<Integer> future = executor.submit(() -> {
            gate.awaitOpen();
            return 7;
        });

        ResultHolder<Integer> holder = new ResultHolder<>();
        Thread waiter = new Thread(() -> {
            try {
                holder.setValue(future.get());
            } catch (ExecutionException e) {
                holder.setError(e);
            }
        });

        waiter.start();

        sleepMillis(150);
        assertFalse(future.isDone());
        assertFalse(holder.hasValue());

        gate.open();
        waiter.join(2000);

        assertEquals(7, holder.getValue());
        assertNull(holder.getError());
        assertTrue(future.isDone());
    }

    @Test
    void checkedException() throws Exception {
        // проверяет, что checked exception не убивает worker
        SingleTheadExecutorService executor = new SingleTheadExecutorService(new NamedThreadFactory());

        CondVarFuture<Integer> failed = executor.submit(() -> {
            throw new IOException("checked failure");
        });

        CondVarFuture<Integer> next = executor.submit(() -> 100);

        ExecutionException ex = assertThrows(ExecutionException.class, failed::get);
        assertInstanceOf(IOException.class, ex.getCause());

        assertEquals(100, next.get());
        assertTrue(next.isDone());
    }

    @Test
    void runtimeKillsWorker() throws Exception {
        // проверяет, что runtime exception завершает worker и создаётся новый
        RecordingThreadFactory factory = new RecordingThreadFactory();
        SingleTheadExecutorService executor = new SingleTheadExecutorService(factory);

        CondVarFuture<Integer> failed = executor.submit(() -> {
            throw new IllegalStateException("boom");
        });

        ExecutionException ex = assertThrows(ExecutionException.class, failed::get);
        assertInstanceOf(IllegalStateException.class, ex.getCause());

        CondVarFuture<Integer> next = executor.submit(() -> 55);
        assertEquals(55, next.get());

        assertTrue(factory.createdThreadCount() >= 2);
    }

    @Test
    void order() throws Exception {
        // проверяет, что задачи выполняются в порядке отправки
        SingleTheadExecutorService executor = new SingleTheadExecutorService(new NamedThreadFactory());
        List<Integer> order = Collections.synchronizedList(new ArrayList<>());

        CondVarFuture<Integer> f1 = executor.submit(recordingTask(order, 1));
        CondVarFuture<Integer> f2 = executor.submit(recordingTask(order, 2));
        CondVarFuture<Integer> f3 = executor.submit(recordingTask(order, 3));

        assertEquals(1, f1.get());
        assertEquals(2, f2.get());
        assertEquals(3, f3.get());

        assertEquals(List.of(1, 2, 3), order);
    }

    @Test
    void singleActiveTask() throws Exception {
        // проверяет, что одновременно выполняется не более одной задачи
        SingleTheadExecutorService executor = new SingleTheadExecutorService(new NamedThreadFactory());

        Lock lock = new ReentrantLock();
        int[] active = {0};
        int[] maxActive = {0};

        Callable<Integer> task = () -> {
            lock.lock();
            try {
                active[0]++;
                if (active[0] > maxActive[0]) {
                    maxActive[0] = active[0];
                }
            } finally {
                lock.unlock();
            }

            sleepMillis(100);

            lock.lock();
            try {
                active[0]--;
            } finally {
                lock.unlock();
            }

            return 1;
        };

        CondVarFuture<Integer> f1 = executor.submit(task);
        CondVarFuture<Integer> f2 = executor.submit(task);
        CondVarFuture<Integer> f3 = executor.submit(task);

        f1.get();
        f2.get();
        f3.get();

        assertEquals(1, maxActive[0]);
    }

    @Test
    void multipleWaiters() throws Exception {
        // проверяет, что все потоки, ожидающие get(), разблокируются
        SingleTheadExecutorService executor = new SingleTheadExecutorService(new NamedThreadFactory());
        Gate gate = new Gate();

        CondVarFuture<Integer> future = executor.submit(() -> {
            gate.awaitOpen();
            return 99;
        });

        ResultHolder<Integer> h1 = new ResultHolder<>();
        ResultHolder<Integer> h2 = new ResultHolder<>();
        ResultHolder<Integer> h3 = new ResultHolder<>();

        Thread t1 = waiterThread(future, h1);
        Thread t2 = waiterThread(future, h2);
        Thread t3 = waiterThread(future, h3);

        t1.start();
        t2.start();
        t3.start();

        sleepMillis(150);
        gate.open();

        t1.join(2000);
        t2.join(2000);
        t3.join(2000);

        assertEquals(99, h1.getValue());
        assertEquals(99, h2.getValue());
        assertEquals(99, h3.getValue());

        assertNull(h1.getError());
        assertNull(h2.getError());
        assertNull(h3.getError());
    }

    @Test
    void errorKillsWorker() throws Exception {
        // проверяет, что Error завершает worker и создаётся новый поток
        RecordingThreadFactory factory = new RecordingThreadFactory();
        SingleTheadExecutorService executor = new SingleTheadExecutorService(factory);

        CondVarFuture<Integer> failed = executor.submit(() -> {
            throw new AssertionError("fatal error");
        });

        ExecutionException ex = assertThrows(ExecutionException.class, failed::get);
        assertInstanceOf(AssertionError.class, ex.getCause());

        CondVarFuture<Integer> next = executor.submit(() -> 11);
        assertEquals(11, next.get());

        assertTrue(factory.createdThreadCount() >= 2);
    }

    private static Callable<Integer> recordingTask(List<Integer> order, int value) {
        return () -> {
            order.add(value);
            return value;
        };
    }

    private static Thread waiterThread(CondVarFuture<Integer> future, ResultHolder<Integer> holder) {
        return new Thread(() -> {
            try {
                holder.setValue(future.get());
            } catch (ExecutionException e) {
                holder.setError(e);
            }
        });
    }

    private static void sleepMillis(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class Gate {
        private final Lock lock = new ReentrantLock();
        private final Condition opened = lock.newCondition();
        private boolean isOpen;

        void awaitOpen() {
            lock.lock();
            try {
                while (!isOpen) {
                    try {
                        opened.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            } finally {
                lock.unlock();
            }
        }

        void open() {
            lock.lock();
            try {
                isOpen = true;
                opened.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

    private static final class ResultHolder<T> {
        private final Lock lock = new ReentrantLock();
        private T value;
        private Throwable error;
        private boolean hasValue;

        void setValue(T value) {
            lock.lock();
            try {
                this.value = value;
                this.hasValue = true;
            } finally {
                lock.unlock();
            }
        }

        void setError(Throwable error) {
            lock.lock();
            try {
                this.error = error;
            } finally {
                lock.unlock();
            }
        }

        T getValue() {
            lock.lock();
            try {
                return value;
            } finally {
                lock.unlock();
            }
        }

        Throwable getError() {
            lock.lock();
            try {
                return error;
            } finally {
                lock.unlock();
            }
        }

        boolean hasValue() {
            lock.lock();
            try {
                return hasValue;
            } finally {
                lock.unlock();
            }
        }
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private int counter;

        @Override
        public Thread newThread(Runnable r) {
            counter++;
            return new Thread(r, "single-thread-executor-" + counter);
        }
    }

    private static final class RecordingThreadFactory implements ThreadFactory {
        private final Lock lock = new ReentrantLock();
        private int counter;

        @Override
        public Thread newThread(Runnable r) {
            lock.lock();
            try {
                counter++;
                return new Thread(r, "recording-worker-" + counter);
            } finally {
                lock.unlock();
            }
        }

        int createdThreadCount() {
            lock.lock();
            try {
                return counter;
            } finally {
                lock.unlock();
            }
        }
    }
}