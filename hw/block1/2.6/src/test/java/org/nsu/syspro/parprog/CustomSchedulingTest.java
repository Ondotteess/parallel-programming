package org.nsu.syspro.parprog;

import org.junit.jupiter.api.Test;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.nsu.syspro.parprog.base.DefaultFork;
import org.nsu.syspro.parprog.base.DiningTable;
import org.nsu.syspro.parprog.examples.DefaultPhilosopher;
import org.nsu.syspro.parprog.helpers.TestLevels;
import org.nsu.syspro.parprog.interfaces.Fork;

public class CustomSchedulingTest extends TestLevels {

    static final class CustomizedPhilosopher extends DefaultPhilosopher {
        @Override
        public void onHungry(Fork left, Fork right) {
            sleepMillis(this.id * 20);
            // System.out.println(Thread.currentThread() + " " + this + ": onHungry");
            super.onHungry(left, right);
        }
    }

    static final class CustomizedFork extends DefaultFork {
        @Override
        public void acquire() {
            //System.out.println(Thread.currentThread() + " trying to acquire " + this);
            super.acquire();
            //System.out.println(Thread.currentThread() + " acquired " + this);
            sleepMillis(100);
        }
    }

    static final class CustomizedTable extends DiningTable<CustomizedPhilosopher, CustomizedFork> {
        public CustomizedTable(int N) {
            super(N);
        }

        @Override
        public CustomizedFork createFork() {
            return new CustomizedFork();
        }

        @Override
        public CustomizedPhilosopher createPhilosopher() {
            return new CustomizedPhilosopher();
        }
    }

    @EnabledIf("easyEnabled")
    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5})
    @Timeout(2)
    void testDeadlockFreedom(int N) {
        final CustomizedTable table = dine(new CustomizedTable(N), 1);
    }


    @EnabledIf("easyEnabled")
    @Test
    @Timeout(6)
    void testSingleSlow() {
        final class SingleSlowTable extends DiningTable<DefaultPhilosopher, DefaultFork> {
            private int created = 0;

            SingleSlowTable(int N) { super(N); }

            @Override
            public DefaultFork createFork() {
                return new DefaultFork();
            }

            @Override
            public DefaultPhilosopher createPhilosopher() {
                final int index = created++;

                if (index == 1) {
                    return new DefaultPhilosopher() {
                        @Override
                        public void onHungry(Fork left, Fork right) {
                            Fork first = left;
                            Fork second = right;

                            if (first.id() > second.id()) {
                                Fork tmp = first;
                                first = second;
                                second = tmp;
                            }

                            first.acquire();
                            try {
                                second.acquire();
                                try {
                                    try {
                                        Thread.sleep(1000);
                                    } catch (InterruptedException e) {
                                        throw new RuntimeException(e);
                                    }
                                    countMeal();
                                } finally {
                                    second.release();
                                }
                            } finally {
                                first.release();
                            }
                        }
                    };
                }

                return new DefaultPhilosopher();
            }
        }

        final SingleSlowTable table = dine(new SingleSlowTable(5), 2);

        assertTrue(table.maxMeals() >= 1000,
                "Expected at least one philosopher to eat >= 1000 times, but maxMeals=" + table.maxMeals());
    }

    @EnabledIf("mediumEnabled")
    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5})
    @Timeout(3)
    void testWeakFairness(int N) {
        final class SkewedPhilosopher extends DefaultPhilosopher {
            private final int index;

            SkewedPhilosopher(int index) {
                this.index = index;
            }

            @Override
            public void onHungry(Fork left, Fork right) {
                if ((index & 1) == 1) {
                    sleepMillis(9);
                }
                super.onHungry(left, right);
            }
        }

        final class SkewedTable extends DiningTable<SkewedPhilosopher, DefaultFork> {
            private int created = 0;

            SkewedTable(int N) { super(N); }

            @Override
            public DefaultFork createFork() {
                return new DefaultFork();
            }

            @Override
            public SkewedPhilosopher createPhilosopher() {
                return new SkewedPhilosopher(created++);
            }
        }

        final SkewedTable table = dine(new SkewedTable(N), 1);

        assertTrue(table.minMeals() > 0,
                "Expected every philosopher to eat at least once, but minMeals=" + table.minMeals()
                        + ", maxMeals=" + table.maxMeals());
    }
}
