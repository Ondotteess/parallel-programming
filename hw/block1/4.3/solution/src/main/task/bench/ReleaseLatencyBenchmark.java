package task.bench;

import task.latch.SimpleCountDownLatch;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public final class ReleaseLatencyBenchmark {

    public List<LatencySample> runSeries(
            String latchName,
            LatchFactory factory,
            int[] threadCounts,
            int warmupRuns,
            int measuredRuns
    ) throws InterruptedException {

        List<LatencySample> samples = new ArrayList<>();

        for (int threads : threadCounts) {
            for (int i = 0; i < warmupRuns; i++) {
                runOnce(factory, threads);
            }

            for (int run = 0; run < measuredRuns; run++) {
                long latency = runOnce(factory, threads);
                samples.add(new LatencySample(latchName, threads, run, latency));
            }
        }

        return samples;
    }

    public long runOnce(LatchFactory factory, int threads) throws InterruptedException {
        if (threads <= 0) {
            throw new IllegalArgumentException("threads must be > 0");
        }

        SimpleCountDownLatch latch = factory.create(threads + 1);

        CountDownLatch spawned = new CountDownLatch(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch arrivedGate = new CountDownLatch(threads);

        long[] wakeTimes = new long[threads];
        Thread[] workers = new Thread[threads];

        for (int i = 0; i < threads; i++) {
            final int index = i;

            workers[i] = new Thread(() -> {
                try {
                    spawned.countDown();
                    startGate.await();

                    latch.countDown();
                    arrivedGate.countDown();
                    latch.await();

                    wakeTimes[index] = System.nanoTime();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    wakeTimes[index] = Long.MIN_VALUE;
                }
            }, "worker-" + i);

            workers[i].start();
        }

        spawned.await();
        startGate.countDown();
        arrivedGate.await();

        long t0 = System.nanoTime();
        latch.countDown();

        for (Thread worker : workers) {
            worker.join();
        }

        long maxWake = Long.MIN_VALUE;
        for (long wakeTime : wakeTimes) {
            if (wakeTime > maxWake) {
                maxWake = wakeTime;
            }
        }

        if (maxWake == Long.MIN_VALUE) {
            throw new IllegalStateException("all worker threads were interrupted");
        }

        return maxWake - t0;
    }

    public List<LatencyStats> summarize(List<LatencySample> samples) {
        List<LatencyStats> stats = new ArrayList<>();

        if (samples.isEmpty()) {
            return stats;
        }

        String currentLatch = samples.get(0).latchName();
        int currentThreads = samples.get(0).threads();
        List<Long> currentValues = new ArrayList<>();

        for (LatencySample sample : samples) {
            if (!sample.latchName().equals(currentLatch) || sample.threads() != currentThreads) {
                stats.add(buildStats(currentLatch, currentThreads, currentValues));
                currentLatch = sample.latchName();
                currentThreads = sample.threads();
                currentValues = new ArrayList<>();
            }
            currentValues.add(sample.latencyNanos());
        }

        stats.add(buildStats(currentLatch, currentThreads, currentValues));
        return stats;
    }

    private LatencyStats buildStats(String latchName, int threads, List<Long> values) {
        int n = values.size();

        double sum = 0.0;
        for (Long value : values) {
            sum += value;
        }
        double mean = sum / n;

        double sq = 0.0;
        for (Long value : values) {
            double diff = value - mean;
            sq += diff * diff;
        }

        double stddev = n > 1 ? Math.sqrt(sq / (n - 1)) : 0.0;

        return new LatencyStats(latchName, threads, n, mean, stddev);
    }
}