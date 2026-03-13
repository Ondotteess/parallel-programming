package task.bench;

public final class LatencySample {
    private final String latchName;
    private final int threads;
    private final int run;
    private final long latencyNanos;

    public LatencySample(String latchName, int threads, int run, long latencyNanos) {
        this.latchName = latchName;
        this.threads = threads;
        this.run = run;
        this.latencyNanos = latencyNanos;
    }

    public String latchName() {
        return latchName;
    }

    public int threads() {
        return threads;
    }

    public int run() {
        return run;
    }

    public long latencyNanos() {
        return latencyNanos;
    }
}