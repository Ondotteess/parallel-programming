package task.latch;


public final class NaiveCountDownLatch implements SimpleCountDownLatch {
    private int count;

    public NaiveCountDownLatch(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count < 0");
        }
        this.count = count;
    }

    @Override
    public synchronized void await() throws InterruptedException {
        while (count != 0) {
            wait();
        }
    }

    @Override
    public synchronized void countDown() {
        if (count == 0) {
            return;
        }

        count--;

        if (count == 0) {
            notifyAll();
        }
    }

    @Override
    public synchronized long getCount() {
        return count;
    }
}