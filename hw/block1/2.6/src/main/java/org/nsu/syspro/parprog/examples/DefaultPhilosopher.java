package org.nsu.syspro.parprog.examples;

import org.nsu.syspro.parprog.base.DefaultFork;
import org.nsu.syspro.parprog.interfaces.Fork;
import org.nsu.syspro.parprog.interfaces.Philosopher;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultPhilosopher implements Philosopher {

    private static final AtomicLong idProvider = new AtomicLong(0);

    /**
     * Ограничиваем одновременный вход к медленным вилкам
     */
    private static final ReentrantLock SLOW_FORK_SERIALIZER = new ReentrantLock(true);

    public final long id;
    private long successfulMeals;

    public DefaultPhilosopher() {
        this.id = idProvider.getAndAdd(1);
        this.successfulMeals = 0;
    }

    @Override
    public long meals() {
        return successfulMeals;
    }

    @Override
    public void countMeal() {
        successfulMeals++;
    }

    private static boolean isSlowFork(Fork f) {
        return (f instanceof DefaultFork) && (f.getClass() != DefaultFork.class);
    }

    /**
     *  1. Все берут вилки по возрастанию id -> цикла ожиданий не возникает
     *  2.1 Если вилка медленная, пускаем одного за раз
     *  2.2 Если быстрая - берем в заданном порядке
     * */

    @Override
    public void onHungry(Fork left, Fork right) {
        Fork first = left;
        Fork second = right;
        if (first.id() > second.id()) {
            Fork tmp = first;
            first = second;
            second = tmp;
        }

        if (isSlowFork(first) || isSlowFork(second)) {
            if (!SLOW_FORK_SERIALIZER.tryLock()) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException ignored) {
                    return;
                }
                return;
            }
            try {
                eat(first, second);
            } finally {
                SLOW_FORK_SERIALIZER.unlock();
            }
            return;
        }

        eat(first, second);
    }

    @Override
    public String toString() {
        return "DefaultPhilosopher{" +
                "id=" + id +
                '}';
    }
}