package task.bench;

import task.latch.MonitorCountDownLatch;
import task.latch.NaiveCountDownLatch;
import task.util.CsvWriter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class BenchmarkMain {

    public static void main(String[] args) throws InterruptedException, IOException {
        ReleaseLatencyBenchmark benchmark = new ReleaseLatencyBenchmark();

        int[] threadCounts = {1, 2, 4, 8, 16, 32, 64, 128};
        int warmupRuns = 10;
        int measuredRuns = 30;

        List<LatencySample> allSamples = new ArrayList<>();

        System.out.println("Running benchmark for NaiveCountDownLatch...");
        allSamples.addAll(benchmark.runSeries(
                "naive",
                NaiveCountDownLatch::new,
                threadCounts,
                warmupRuns,
                measuredRuns
        ));

        System.out.println("Running benchmark for MonitorCountDownLatch...");
        allSamples.addAll(benchmark.runSeries(
                "monitor",
                MonitorCountDownLatch::new,
                threadCounts,
                warmupRuns,
                measuredRuns
        ));

        allSamples.sort(Comparator
                .comparing(LatencySample::latchName)
                .thenComparingInt(LatencySample::threads)
                .thenComparingInt(LatencySample::run));

        List<LatencyStats> stats = benchmark.summarize(allSamples);

        Path rawPath = Path.of("solution/results", "raw", "latency_samples.csv");
        Path statsPath = Path.of("solution/results", "raw", "latency_stats.csv");

        CsvWriter.writeSamples(rawPath, allSamples);
        CsvWriter.writeStats(statsPath, stats);

        System.out.println("Done.");
        System.out.println("Raw samples written to: " + rawPath);
        System.out.println("Stats written to: " + statsPath);

        for (LatencyStats stat : stats) {
            System.out.printf(
                    "%s, N=%d, runs=%d, mean=%.2f ns, stddev=%.2f ns%n",
                    stat.latchName(),
                    stat.threads(),
                    stat.runs(),
                    stat.meanNanos(),
                    stat.stddevNanos()
            );
        }
    }
}