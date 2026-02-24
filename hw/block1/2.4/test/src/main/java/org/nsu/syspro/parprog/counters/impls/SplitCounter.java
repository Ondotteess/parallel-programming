package org.nsu.syspro.parprog.counters.impls;

import java.util.concurrent.locks.ReentrantLock;

public class SplitCounter implements Counter {

    private final int granularity;
    private final long[] portions;
    private final ReentrantLock[] locks;

    public SplitCounter(int GRANULARITY) {
        if (GRANULARITY <= 0) {
            throw new IllegalArgumentException("GRANULARITY must be > 0");
        }
        this.granularity = GRANULARITY;
        this.portions = new long[GRANULARITY];
        this.locks = new ReentrantLock[GRANULARITY];
        for (int i = 0; i < GRANULARITY; i++) {
            locks[i] = new ReentrantLock(false); // unfair быстрее
        }
    }

    private int idx() {
        return Math.floorMod(Thread.currentThread().getId(), granularity);
    }

    @Override
    public void increment() {
        int i = idx();
        ReentrantLock l = locks[i];
        l.lock();
        try {
            portions[i]++;
        } finally {
            l.unlock();
        }
    }

    @Override
    public long get() {
        // lock-all для согласованного снимка
        for (int i = 0; i < granularity; i++) locks[i].lock();
        try {
            long sum = 0L;
            for (int i = 0; i < granularity; i++) sum += portions[i];
            return sum;
        } finally {
            for (int i = granularity - 1; i >= 0; i--) locks[i].unlock();
        }
    }
}