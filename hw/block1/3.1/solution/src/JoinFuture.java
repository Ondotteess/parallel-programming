import java.util.concurrent.ExecutionException;

/**
 * Представляет результат вычисления, выполненного в отдельном потоке.
 * Хранит
 * - поток, в котором выполняется задача
 * - результат вычисления
 * - возможное исключение
 */

public class JoinFuture<V> {
    private Thread thread;
    private V result;
    private Throwable failure;

    /**
     * ожидает завершения вычисления, возвращает результат
     *
     * @return результат вычисления
     * @throws ExecutionException если задача выкинула исключение
     */
    public V get() throws ExecutionException {
        if (thread == null) {
            throw new IllegalStateException("worker thread is not set");
        }

        boolean interrupted = false;
        try {
            while (true) {
                try {
                    thread.join();
                    break;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        if (failure != null) {
            throw new ExecutionException(failure);
        }
        return result;
    }

    /**
     * true, если задача завершилась успешно или с исключением.
     */
    public boolean isDone() {
        if (thread == null) {
            return false;
        }
        return !thread.isAlive();
    }

    void setThread(Thread thread) {
        this.thread = thread;
    }

    void setResult(V result) {
        this.result = result;
    }

    void setFailure(Throwable failure) {
        this.failure = failure;
    }
}
