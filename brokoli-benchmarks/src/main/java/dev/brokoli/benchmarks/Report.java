package dev.brokoli.benchmarks;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class Report {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String USAGE = "usage: Report <cold start result> <large input result> <JMH result>";
    private static final int ARGUMENTS = 3;
    private static final double MILLIS_PER_SECOND = 1000.0;
    private static final String RECORDS = "records";
    private static final String ROWS = "rows";
    private static final String RAW_MAP = "rawMap";
    private static final String DECODED_MAP = "decodedMap";
    private static final Map<String, String> ROW_LABELS = Map.of(
            RECORDS, "SDK records, `List<Order>`",
            ROWS, "SDK rows, `Rows`",
            RAW_MAP, "a raw `Map`, reading `paid` only",
            DECODED_MAP, "a raw `Map`, decoding all six columns by hand");
    private static final Map<String, String> BASELINES = Map.of(RECORDS, DECODED_MAP, ROWS, RAW_MAP);
    private static final Map<String, String> BASELINE_NAMES = Map.of(
            DECODED_MAP, "the hand-decoded `Map`",
            RAW_MAP, "the raw `Map`");
    private static final double PERCENT = 100.0;

    private Report() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != ARGUMENTS) {
            throw new BenchmarkException(USAGE);
        }
        System.out.print(markdown(read(args[0]), read(args[1]), read(args[2])));
    }

    static String markdown(JsonNode coldStart, JsonNode largeInput, JsonNode jmh) {
        StringBuilder table = new StringBuilder()
                .append("### Benchmarks\n\n")
                .append("Reporting only, nothing here fails the build. The task JVM ran on Java ")
                .append(coldStart.path(ColdStart.JAVA_VERSION).asString()).append(".\n\n")
                .append("| Measure | Result | Target |\n")
                .append("|---|---|---|\n");
        coldStart(table, coldStart);
        largeInput(table, largeInput);
        jmh(table, jmh);
        return table.toString();
    }

    private static void coldStart(StringBuilder table, JsonNode coldStart) {
        JsonNode jvm = coldStart.path(Attempts.JVM);
        JsonNode python = coldStart.path(Attempts.PYTHON);
        String runs = " of " + ColdStart.RUNS;
        row(table, "JVM cold start of a no-op task, p50" + runs, millis(p50(jvm, ColdStart.WALL_MS)), "under 100 ms");
        row(table, "JVM cold start of a no-op task, p95" + runs, millis(jvm.path(ColdStart.WALL_MS).path(Summary.P95)),
                "under 150 ms");
        row(table, "Python cold start of a no-op task, p50" + runs, millis(p50(python, ColdStart.WALL_MS)), "");
        row(table, "JVM cold start less Python's, p50", format("%+.1f",
                p50(jvm, ColdStart.WALL_MS).asDouble() - p50(python, ColdStart.WALL_MS).asDouble()) + " ms",
                "under 50 ms");
        row(table, "JVM first attempt, with the harness compiled", millis(jvm.path(ColdStart.FIRST_ATTEMPT_MS)),
                "under 2500 ms");
        row(table, "Python first attempt", millis(python.path(ColdStart.FIRST_ATTEMPT_MS)), "");
        row(table, "JVM peak RSS of a no-op task, highest" + runs, peak(jvm.path(ColdStart.RSS_MIB)), "under 96 MiB");
        row(table, "Python peak RSS of a no-op task, highest" + runs, peak(python.path(ColdStart.RSS_MIB)), "");
        row(table, "Task node duration in core, p50, JVM and Python", millis(p50(jvm, ColdStart.NODE_MS)) + " and "
                + millis(p50(python, ColdStart.NODE_MS)), "");
    }

    private static void largeInput(StringBuilder table, JsonNode largeInput) {
        long rows = largeInput.path(LargeInput.ROWS_KEY).asLong();
        JsonNode sdk = largeInput.path(LargeInput.SDK);
        JsonNode rawMap = largeInput.path(LargeInput.RAW_MAP);
        JsonNode python = largeInput.path(LargeInput.PYTHON);
        String of = rows + " rows, ";
        String runs = " of " + LargeInput.RUNS;
        row(table, of + "JVM SDK task, p50" + runs, throughput(rows, sdk) + ", Python " + throughput(rows, python),
                "at least the Python task");
        row(table, of + "raw `Map` JVM task, p50" + runs, throughput(rows, rawMap), "");
        row(table, of + "Python task, p50" + runs, throughput(rows, python), "");
        double sdkPeak = sdk.path(LargeInput.RSS_MIB).path(Summary.MAX).asDouble();
        double rawPeak = rawMap.path(LargeInput.RSS_MIB).path(Summary.MAX).asDouble();
        row(table, of + "peak RSS of the JVM SDK task, highest" + runs, mebibytes(sdkPeak) + ", "
                + format("%+.1f", (sdkPeak / rawPeak - 1) * PERCENT) + "% over the raw `Map` task",
                "at most 10% over the raw `Map` task");
        row(table, of + "peak RSS of the raw `Map` JVM task, highest" + runs, mebibytes(rawPeak), "");
        row(table, of + "peak RSS of the Python task, highest" + runs,
                mebibytes(python.path(LargeInput.RSS_MIB).path(Summary.MAX).asDouble()), "");
    }

    private static void jmh(StringBuilder table, JsonNode jmh) {

        Map<String, Double> scores = new HashMap<>();
        for (JsonNode result : jmh.values()) {
            scores.put(method(result), score(result));
        }
        for (JsonNode result : jmh.values()) {
            String method = method(result);
            String measure = "JMH, " + result.path("params").path("rows").asString() + " rows through "
                    + ROW_LABELS.getOrDefault(method, method);
            JsonNode metric = result.path("primaryMetric");
            JsonNode error = metric.path("scoreError");
            String score = format("%.2f", score(result))
                    + (error.isNumber() ? " ± " + format("%.2f", error.asDouble()) : "")
                    + " " + metric.path("scoreUnit").asString();
            String baseline = BASELINES.get(method);
            if (baseline == null || !scores.containsKey(baseline)) {
                row(table, measure, score, "");
            } else {
                String name = BASELINE_NAMES.get(baseline);
                row(table, measure, score + ", " + format("%+.1f", (score(result) / scores.get(baseline) - 1)
                        * PERCENT) + "% over " + name, "at most 5% over " + name);
            }
        }
    }

    private static JsonNode read(String path) throws IOException {
        return JSON.readTree(Files.readString(Path.of(path)));
    }

    private static JsonNode p50(JsonNode runtime, String measure) {
        return runtime.path(measure).path(Summary.P50);
    }

    private static String peak(JsonNode rss) {
        return mebibytes(rss.path(Summary.MAX).asDouble()) + " (p50 " + mebibytes(rss.path(Summary.P50).asDouble())
                + ")";
    }

    private static String throughput(long rows, JsonNode task) {
        double millis = task.path(LargeInput.NODE_MS).path(Summary.P50).asDouble();
        return format("%.0f", millis) + " ms, " + format("%.0f", rows / (millis / MILLIS_PER_SECOND)) + " rows/s";
    }

    private static void row(StringBuilder table, String measure, String result, String target) {
        table.append("| ").append(measure).append(" | ").append(result).append(" | ").append(target).append(" |\n");
    }

    private static String method(JsonNode result) {
        String benchmark = result.path("benchmark").asString();
        return benchmark.substring(benchmark.lastIndexOf('.') + 1);
    }

    private static double score(JsonNode result) {
        return result.path("primaryMetric").path("score").asDouble();
    }

    private static String millis(JsonNode value) {
        return format("%.1f", value.asDouble()) + " ms";
    }

    private static String mebibytes(double value) {
        return format("%.1f", value) + " MiB";
    }

    private static String format(String pattern, double value) {
        return String.format(Locale.ROOT, pattern, value);
    }
}
