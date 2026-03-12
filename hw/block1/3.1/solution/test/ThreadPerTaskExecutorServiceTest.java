import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadFactory;

import static org.junit.jupiter.api.Assertions.*;

class ThreadPerTaskExecutorServiceTest {

    private static final ThreadFactory FACTORY = Thread::new;

    @Test
    void largeResult() throws Exception {
        ThreadPerTaskExecutorService executor =
                new ThreadPerTaskExecutorService(FACTORY);

        JoinFuture<Long> future = executor.submit(() -> 987_654_321L * 123_456_789L);

        assertEquals(987_654_321L * 123_456_789L, future.get());
    }

    @Test
    void waits() throws Exception {
        ThreadPerTaskExecutorService executor =
                new ThreadPerTaskExecutorService(FACTORY);

        JoinFuture<Integer> future = executor.submit(() -> {
            Thread.sleep(5_000);
            return 42;
        });

        long start = System.currentTimeMillis();
        long result = future.get();
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(42, result);
        assertTrue(elapsed >= 5_000, "get() не ждет завершения");
    }

    @Test
    void wrapsException() {
        ThreadPerTaskExecutorService executor =
                new ThreadPerTaskExecutorService(FACTORY);

        JoinFuture<Integer> future = executor.submit(() -> {
            throw new IllegalStateException("something went wrong");
        });

        ExecutionException ex = assertThrows(ExecutionException.class, future::get);
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertEquals("something went wrong", ex.getCause().getMessage());
    }

    @Test
    void isDone() throws Exception {
        ThreadPerTaskExecutorService executor =
                new ThreadPerTaskExecutorService(FACTORY);

        JoinFuture<Integer> future = executor.submit(() -> {
            Thread.sleep(5_000);
            return 42;
        });

        assertFalse(future.isDone());
        assertEquals(42, future.get());
        assertTrue(future.isDone());
    }

    @Test
    void repeatedGet() throws Exception {
        ThreadPerTaskExecutorService executor =
                new ThreadPerTaskExecutorService(FACTORY);

        JoinFuture<Integer> future = executor.submit(() -> {
            Thread.sleep(5_000);
            return 67;
        });

        long first = future.get();
        long second = future.get();

        assertEquals(67, first);
        assertEquals(first, second);
    }

    @Test
    void nullResult() throws Exception {
        ThreadPerTaskExecutorService executor =
                new ThreadPerTaskExecutorService(FACTORY);

        JoinFuture<String> future = executor.submit(() -> {
            Thread.sleep(5_000);
            return null;
        });

        assertNull(future.get());
        assertTrue(future.isDone());
    }

    @Test
    void manyTasks() throws Exception {
        ThreadPerTaskExecutorService executor =
                new ThreadPerTaskExecutorService(FACTORY);

        int n = 40;
        List<JoinFuture<Long>> futures = new ArrayList<>();

        for (int i = 1; i <= n; i++) {
            final int x = i;
            futures.add(executor.submit(() -> {
                Thread.sleep(15L * (n - x));
                return (long) x * x * x * x;
            }));
        }

        for (int i = 1; i <= n; i++) {
            assertEquals((long) i * i * i * i, futures.get(i - 1).get());
        }
    }
}