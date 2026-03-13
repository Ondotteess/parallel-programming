package task.util;

import task.bench.LatencySample;
import task.bench.LatencyStats;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class CsvWriter {

    private CsvWriter() {
    }

    public static void writeSamples(Path path, List<LatencySample> samples) throws IOException {
        ensureParentDirectoryExists(path);

        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write("latch,threads,run,latency_nanos");
            writer.newLine();

            for (LatencySample sample : samples) {
                writer.write(sample.latchName() + ","
                        + sample.threads() + ","
                        + sample.run() + ","
                        + sample.latencyNanos());
                writer.newLine();
            }
        }
    }

    public static void writeStats(Path path, List<LatencyStats> stats) throws IOException {
        ensureParentDirectoryExists(path);

        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write("latch,threads,runs,mean_nanos,stddev_nanos");
            writer.newLine();

            for (LatencyStats stat : stats) {
                writer.write(stat.latchName() + ","
                        + stat.threads() + ","
                        + stat.runs() + ","
                        + stat.meanNanos() + ","
                        + stat.stddevNanos());
                writer.newLine();
            }
        }
    }

    private static void ensureParentDirectoryExists(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}