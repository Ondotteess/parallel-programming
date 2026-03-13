package org.example.task33;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Исполнитель задач, в котором всегда работает не более одного потока.
 * Все задачи выполняются последовательно в порядке их отправки.
 * Если поток завершается из-за unchecked Throwable,
 * задача завершается с ошибкой, и поток прекращает работу.
 */
public class SingleTheadExecutorService {
    private final ThreadFactory threadFactory;
    private final LinkedBlockingQueue<Task<?>> queue = new LinkedBlockingQueue<>();
    private final Lock stateLock = new ReentrantLock();

    private boolean workerRunning;

    /**
     * Создаёт исполнитель задач с указанной фабрикой потоков.
     *
     * @param f фабрика потоков
     * @throws NullPointerException если фабрика потоков null
     */
    public SingleTheadExecutorService(ThreadFactory f) {
        this.threadFactory = Objects.requireNonNull(f, "threadFactory is null");
    }

    /**
     * Отправляет задачу на выполнение.
     * Для задачи создаётся CondVarFuture, через который
     * можно получить результат вычисления или исключение.
     *
     * @param task задача для выполнения
     * @param <T> тип результата
     * @return future-объект, представляющий результат выполнения задачи
     * @throws NullPointerException если задача равна null
     */
    public <T> CondVarFuture<T> submit(Callable<T> task) {
        Objects.requireNonNull(task, "task is null");

        CondVarFuture<T> future = new CondVarFuture<>();
        Task<T> queuedTask = new Task<>(task, future);

        queue.add(queuedTask);

        stateLock.lock();
        try {
            ensureWorkerStartedLocked();
        } finally {
            stateLock.unlock();
        }

        return future;
    }

    /**
     * Запускает рабочий поток, если он ещё не запущен.
     * Метод должен вызываться только при удержании stateLock.
     */
    private void ensureWorkerStartedLocked() {
        if (workerRunning) {
            return;
        }

        Thread worker = threadFactory.newThread(this::workerLoop);
        if (worker == null) {
            throw new NullPointerException("threadFactory returned null thread");
        }

        workerRunning = true;
        try {
            worker.start();
        } catch (RuntimeException e) {
            workerRunning = false;
            throw e;
        }
    }

    /**
     * Поток извлекает задачи из очереди и выполняет их по одной.
     * Если задача завершилась checked-исключением, поток продолжает
     * обработку следующих задач.
     * Если задача завершилась unchecked Throwable, поток завершает работу.
     */
    private void workerLoop() {
        try {
            while (true) {
                Task<?> task = queue.take();
                boolean continueRunning = executeTask(task);
                if (!continueRunning) {
                    return;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            stateLock.lock();
            try {
                workerRunning = false;
                if (!queue.isEmpty()) {
                    ensureWorkerStartedLocked();
                }
            } finally {
                stateLock.unlock();
            }
        }
    }

    /**
     * Выполняет одну задачу из очереди.
     *
     * @param rawTask задача
     * @return true, если рабочий поток должен продолжить выполнение
     * следующих задач, и false, если поток должен завершиться
     */
    private boolean executeTask(Task<?> rawTask) {
        return executeTypedTask(rawTask);
    }

    /**
     * Выполняет задачу.
     * При успешном завершении результат записывается в  future.
     * Если возникает checked-исключение, future завершается с ошибкой,
     * но рабочий поток продолжает выполнять следующие задачи.
     * Если возникает unchecked Throwable, future завершается с ошибкой,
     * а рабочий поток завершает работу.
     *
     * @param rawTask задача
     * @param <T> тип результата
     * @return true, если поток должен продолжить работу,
     * иначе false
     */
    private <T> boolean executeTypedTask(Task<?> rawTask) {
        @SuppressWarnings("unchecked")
        Task<T> task = (Task<T>) rawTask;

        try {
            T value = task.callable.call();
            task.future.complete(value);
            return true;
        } catch (RuntimeException | Error fatal) {
            task.future.fail(fatal);
            return false;
        } catch (Exception checked) {
            task.future.fail(checked);
            return true;
        } catch (Throwable fatal) {
            task.future.fail(fatal);
            return false;
        }
    }

    /**
     * Структура данных, связывающая задачу и future.
     * @param <T> тип результата задачи
     */
    private static final class Task<T> {
        private final Callable<T> callable;
        private final CondVarFuture<T> future;

        private Task(Callable<T> callable, CondVarFuture<T> future) {
            this.callable = callable;
            this.future = future;
        }
    }
}