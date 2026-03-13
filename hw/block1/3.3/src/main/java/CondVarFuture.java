package org.example.task33;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Future, сделанный на Lock и Condition.
 *
 * @param <V> тип результата
 */
public class CondVarFuture<V> {
    private final Lock lock = new ReentrantLock();
    private final Condition doneCondition = lock.newCondition();

    private boolean done;
    private V result;
    private ExecutionException executionException;

    /**
     * Ожидает завершения, и возвращает полученный результат.
     * Если вычисление завершилось с ошибкой, выбрасывает ExecutionException.
     * Прерывания во время ожидания запоминаются, а флаг прерывания восстанавливается перед выходом
     *
     * @return  результат
     */
    public V get() throws ExecutionException {
        lock.lock();
        boolean interrupted = false;
        try {
            while (!done) {
                try {
                    doneCondition.await();
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }

            if (executionException != null) {
                throw executionException;
            }
            return result;
        } finally {
            lock.unlock();
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Возвращает true, если вычисление завершено.
     * @return true
     */
    public boolean isDone() {
        lock.lock();
        try {
            return done;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Завершает вычисление успешно и сохраняет результат.
     * Если объект уже был завершён ранее, повторное завершение игнорируется.
     * @param value вычисленное значение
     */
    void complete(V value) {
        lock.lock();
        try {
            if (done) {
                return;
            }
            result = value;
            done = true;
            doneCondition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Завершает вычисление с ошибкой.
     * @param cause причина ошибки
     */
    void fail(Throwable cause) {
        lock.lock();
        try {
            if (done) {
                return;
            }
            executionException = new ExecutionException(cause);
            done = true;
            doneCondition.signalAll();
        } finally {
            lock.unlock();
        }
    }
}