package org.nsu.syspro.parprog.counters.impls;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.markers.SeriesMarkers;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Convert JMH result JSON into PNG charts with error bars.
 *
 * Usage:
 *   java ... JmhJsonToPng path/to/results.json [outDir]
 *
 * Output:
 *   outDir/throughput_get.png
 *   outDir/throughput_inc.png
 */
public class JmhJsonToPng {

    private static final String OP_GET = "get";
    private static final String OP_INC = "inc";

    private static class Point {
        final int threads;
        final double score;
        final double err;

        Point(int threads, double score, double err) {
            this.threads = threads;
            this.score = score;
            this.err = err;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: java ... JmhJsonToPng <path-to-results.json> [outDir]");
            System.exit(1);
        }

        Path in = Paths.get(args[0]);
        Path outDir = Paths.get(args.length >= 2 ? args[1] : "charts");
        Files.createDirectories(outDir);

        ObjectMapper om = new ObjectMapper();
        String json = new String(Files.readAllBytes(in), StandardCharsets.UTF_8);
        JsonNode root = om.readTree(json);
        if (!root.isArray()) {
            throw new IllegalStateException("Expected top-level JSON array (JMH result format).");
        }

        // op -> counterType -> points
        Map<String, Map<String, List<Point>>> data = new HashMap<String, Map<String, List<Point>>>();
        data.put(OP_GET, new HashMap<String, List<Point>>());
        data.put(OP_INC, new HashMap<String, List<Point>>());

        for (JsonNode bench : root) {
            String benchmarkFqn = bench.path("benchmark").asText("");
            String method = lastSegment(benchmarkFqn);

            String op = opFromMethod(method);
            if (op == null) continue;

            int threads = bench.path("threads").asInt(-1);
            String counterType = bench.path("params").path("counterType").asText("");

            JsonNode pm = bench.path("primaryMetric");
            double score = pm.path("score").asDouble(Double.NaN);
            double err = pm.path("scoreError").asDouble(0.0);

            Point p = new Point(threads, score, err);

            Map<String, List<Point>> byCounter = data.get(op);
            List<Point> pts = byCounter.get(counterType);
            if (pts == null) {
                pts = new ArrayList<Point>();
                byCounter.put(counterType, pts);
            }
            pts.add(p);
        }

        saveChart(OP_GET, data.get(OP_GET), outDir.resolve("throughput_get.png"));
        saveChart(OP_INC, data.get(OP_INC), outDir.resolve("throughput_inc.png"));

        System.out.println("Wrote: " + outDir.toAbsolutePath());
    }

    private static void saveChart(String op, Map<String, List<Point>> byCounter, Path outFile) throws Exception {
        XYChart chart = new XYChartBuilder()
                .title("JMH Throughput: " + op)
                .xAxisTitle("Threads")
                .yAxisTitle("Throughput (ops/ms)")
                .width(1100)
                .height(700)
                .build();

        // sort series names for stable output
        List<String> seriesNames = new ArrayList<String>(byCounter.keySet());
        Collections.sort(seriesNames);

        for (String counterType : seriesNames) {
            if ("Unsafe".equals(counterType)) continue; // comment to include unsafe

            List<Point> pts = byCounter.get(counterType);
            if (pts == null || pts.isEmpty()) continue;

            // sort points by threads
            pts.sort(new Comparator<Point>() {
                @Override public int compare(Point a, Point b) {
                    return Integer.compare(a.threads, b.threads);
                }
            });

            double[] x = new double[pts.size()];
            double[] y = new double[pts.size()];
            double[] yErr = new double[pts.size()];

            for (int i = 0; i < pts.size(); i++) {
                Point p = pts.get(i);
                x[i] = p.threads;
                y[i] = p.score;
                yErr[i] = p.err;
            }

            // Error bars: pass yErr as 3rd array
            XYSeries s = chart.addSeries(counterType, x, y, yErr);
            s.setMarker(SeriesMarkers.CIRCLE);
        }

        BitmapEncoder.saveBitmap(chart, outFile.toString(), BitmapEncoder.BitmapFormat.PNG);
    }

    private static String lastSegment(String fqn) {
        int idx = fqn.lastIndexOf('.');
        return idx >= 0 ? fqn.substring(idx + 1) : fqn;
    }

    private static String opFromMethod(String method) {
        if (method.startsWith("get")) return OP_GET;
        if (method.startsWith("inc")) return OP_INC;
        return null;
    }
}
