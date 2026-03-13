package task.bench;

import org.junit.jupiter.api.Test;
import task.latch.MonitorCountDownLatch;
import task.latch.NaiveCountDownLatch;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SmokeBenchmarkTest {

    @Test
    void runOnceRejectsBadThreadCount() {
        ReleaseLatencyBenchmark benchmark = new ReleaseLatencyBenchmark();

        assertThrows(IllegalArgumentException.class, () -> benchmark.runOnce(NaiveCountDownLatch::new, 0));
        assertThrows(IllegalArgumentException.class, () -> benchmark.runOnce(MonitorCountDownLatch::new, -1));
    }

    @Test
    void runOnceWorksForNaive() throws InterruptedException {
        ReleaseLatencyBenchmark benchmark = new ReleaseLatencyBenchmark();

        long latency = benchmark.runOnce(NaiveCountDownLatch::new, 4);

        assertTrue(latency >= 0, "latency must be non-negative");
    }

    @Test
    void runOnceWorksForMonitor() throws InterruptedException {
        ReleaseLatencyBenchmark benchmark = new ReleaseLatencyBenchmark();

        long latency = benchmark.runOnce(MonitorCountDownLatch::new, 4);

        assertTrue(latency >= 0, "latency must be non-negative");
    }

    @Test
    void runSeriesNaiveSampleCount() throws InterruptedException {
        ReleaseLatencyBenchmark benchmark = new ReleaseLatencyBenchmark();

        int[] threadCounts = {1, 2, 4};
        int warmupRuns = 2;
        int measuredRuns = 3;

        List<LatencySample> samples = benchmark.runSeries(
                "naive",
                NaiveCountDownLatch::new,
                threadCounts,
                warmupRuns,
                measuredRuns
        );

        assertEquals(threadCounts.length * measuredRuns, samples.size());

        for (LatencySample sample : samples) {
            assertEquals("naive", sample.latchName());
            assertTrue(sample.threads() == 1 || sample.threads() == 2 || sample.threads() == 4);
            assertTrue(sample.run() >= 0 && sample.run() < measuredRuns);
            assertTrue(sample.latencyNanos() >= 0);
        }
    }

    @Test
    void runSeriesMonitorSampleCount() throws InterruptedException {
        ReleaseLatencyBenchmark benchmark = new ReleaseLatencyBenchmark();

        int[] threadCounts = {1, 2, 4};
        int warmupRuns = 1;
        int measuredRuns = 2;

        List<LatencySample> samples = benchmark.runSeries(
                "monitor",
                MonitorCountDownLatch::new,
                threadCounts,
                warmupRuns,
                measuredRuns
        );

        assertEquals(threadCounts.length * measuredRuns, samples.size());

        for (LatencySample sample : samples) {
            assertEquals("monitor", sample.latchName());
            assertTrue(sample.threads() == 1 || sample.threads() == 2 || sample.threads() == 4);
            assertTrue(sample.run() >= 0 && sample.run() < measuredRuns);
            assertTrue(sample.latencyNanos() >= 0);
        }
    }

    @Test
    void runSeriesSingleThread() throws InterruptedException {
        ReleaseLatencyBenchmark benchmark = new ReleaseLatencyBenchmark();

        List<LatencySample> samples = benchmark.runSeries(
                "naive",
                NaiveCountDownLatch::new,
                new int[]{1},
                0,
                2
        );

        assertEquals(2, samples.size());
        for (LatencySample sample : samples) {
            assertEquals(1, sample.threads());
            assertTrue(sample.latencyNanos() >= 0);
        }
    }

}