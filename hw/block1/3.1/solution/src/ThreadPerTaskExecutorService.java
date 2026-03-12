import java.util.concurrent.Callable;
import java.util.concurrent.ThreadFactory;

/**
 * Сервис, создающий отдельный поток для каждой отправленной задачи.
 * Создаёт объект JoinFuture
 * создаёт новый поток
 * task.call()
 *  - успешно/исключение — результат сохраняется в future,
 * сохраняет поток во future
 * возвращает JoinFuture через который можно получить результат выполнения
 *
 */


public class ThreadPerTaskExecutorService {
    private final ThreadFactory factory;

    /**
     * Создаёт сервис с фабрикой потоков.
     *
     * @param f фабрика
     * @throws NullPointerException если фабрика null
     */
    public ThreadPerTaskExecutorService(ThreadFactory f) {
        if (f == null) {
            throw new NullPointerException("threadFactory is null");
        }
        this.factory = f;
    }

    /**
     * Отправляет задачу на выполнение и возвращает future c результатом
     *
     * @param task задача
     * @param <T> тип результата
     * @return future результата
     * @throws NullPointerException если задача null
     */
    public <T> JoinFuture<T> submit(Callable<T> task) {
        if (task == null) {
            throw new NullPointerException("task is null");
        }

        JoinFuture<T> future = new JoinFuture<>();

        Thread worker = factory.newThread(() -> {
            try {
                future.setResult(task.call());
            } catch (Throwable t) {
                future.setFailure(t);
            }
        });

        if (worker == null) {
            throw new NullPointerException("threadFactory returned null");
        }

        future.setThread(worker);
        worker.start();
        return future;
    }
}