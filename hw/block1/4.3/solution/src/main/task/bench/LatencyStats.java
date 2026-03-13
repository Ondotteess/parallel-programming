package task.bench;

public final class LatencyStats {
    private final String latchName;
    private final int threads;
    private final int runs;
    private final double meanNanos;
    private final double stddevNanos;

    public LatencyStats(String latchName, int threads, int runs, double meanNanos, double stddevNanos) {
        this.latchName = latchName;
        this.threads = threads;
        this.runs = runs;
        this.meanNanos = meanNanos;
        this.stddevNanos = stddevNanos;
    }

    public String latchName() {
        return latchName;
    }

    public int threads() {
        return threads;
    }

    public int runs() {
        return runs;
    }

    public double meanNanos() {
        return meanNanos;
    }

    public double stddevNanos() {
        return stddevNanos;
    }
}