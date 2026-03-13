package task.latch;


public interface SimpleCountDownLatch {
    void await() throws InterruptedException;
    void countDown();
    long getCount();
}