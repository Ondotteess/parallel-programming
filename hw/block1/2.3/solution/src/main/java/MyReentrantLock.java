/**
 * Реентерабельный мьютекс, через spinning и backoff  на нереентерабельном мьютексе.
 *  - owner и holdCount всегда читаются/пишутся только под stateLock.
 *  - владелец может заходить повторно, увеличивая holdCount.
 *  - deadlock-free
 *  - не starvation-free
 */
public final class MyReentrantLock {
    private final NonReentrantLock stateLock;
    private Thread owner = null;
    private int holdCount = 0;

    private static final int MAX_WAIT_TIME = 1_000_000;
    private static final int MIN_WAIT_TIME = 1_000;

    public MyReentrantLock(NonReentrantLockFactory factory) {
        if (factory == null) throw new NullPointerException("factory");
        this.stateLock = factory.create();
        if (this.stateLock == null) {
            throw new IllegalStateException("Factory returned null lock");
        }
    }

    /**
     * Либо захватывает мьютекс, либо выходит не ожидая освобождения.
     * @return true если захватили , иначе false
     */
    public boolean tryLock() {
        final Thread current = Thread.currentThread();
        stateLock.lock();
        try {
            if (owner == null) {
                owner = current;
                holdCount = 1;
                return true;
            }
            if (owner == current) {
                holdCount++;
                return true;
            }
            return false;
        } finally {
            stateLock.unlock();
        }
    }

    /**
     * Крутимся в цикле пока не захватим мьютекс, делая экспоненциально растущие паузы
     * spinning + backoff.
     */
    public void lock() {
        int backoff = MIN_WAIT_TIME;

        while (!tryLock()) {
            onSpinWaitCompat();
            sleepNanos(backoff);
            backoff = Math.min(MAX_WAIT_TIME, backoff << 1);
        }
    }

    public void unlock() {
        final Thread current = Thread.currentThread();

        stateLock.lock();
        try {
            if (owner != current) {
                throw new IllegalMonitorStateException("Current thread does not own this lock");
            }
            if (holdCount <= 0) {
                throw new IllegalMonitorStateException("Unlock without matching lock");
            }

            holdCount--;
            if (holdCount == 0) {
                owner = null;
            }
        } finally {
            stateLock.unlock();
        }
    }

    // ---- helpers  ----

    private static void onSpinWaitCompat() {
        Thread.onSpinWait();
    }

    private static void sleepNanos(int nanos) {
        int ns = Math.min(999_999, Math.max(0, nanos));
        try {
            Thread.sleep(0, ns);
        } catch (InterruptedException ignored) {
        }
    }
}

/**
 * owner и holdCount читаются и меняются только под stateLock -> нет гонок
 * реентерабельность достигается засчет holdCount
 * другие потоки не смогут захватить owner пока он не станет null
 *
 * */