package task.bench;

import task.latch.SimpleCountDownLatch;

@FunctionalInterface
public interface LatchFactory {
    SimpleCountDownLatch create(int count);
}