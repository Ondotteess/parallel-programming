package task.latch;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class MonitorCountDownLatch implements SimpleCountDownLatch {
    private static final class Token {
        private boolean released = false;
    }

    private final Object guard = new Object();
    private final Deque<Token> waiters = new ArrayDeque<>();
    private int count;

    public MonitorCountDownLatch(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count < 0");
        }
        this.count = count;
    }

    @Override
    public void await() throws InterruptedException {
        Token token;

        synchronized (guard) {
            if (count == 0) {
                return;
            }

            token = new Token();
            waiters.addLast(token);
        }

        synchronized (token) {
            while (!token.released) {
                token.wait();
            }
        }
    }

    @Override
    public void countDown() {
        List<Token> toRelease = null;

        synchronized (guard) {
            if (count == 0) {
                return;
            }

            count--;

            if (count == 0) {
                toRelease = new ArrayList<>(waiters);
                waiters.clear();
            }
        }

        if (toRelease != null) {
            for (Token token : toRelease) {
                synchronized (token) {
                    token.released = true;
                    token.notify();
                }
            }
        }
    }

    @Override
    public long getCount() {
        synchronized (guard) {
            return count;
        }
    }
}