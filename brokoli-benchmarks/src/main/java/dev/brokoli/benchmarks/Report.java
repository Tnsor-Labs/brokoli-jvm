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
    private static final String USAGE = "usage: Report <cold start result> <JMH result>";
    private static final int ARGUMENTS = 2;
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
        System.out.print(markdown(JSON.readTree(Files.readString(Path.of(args[0]))),
                JSON.readTree(Files.readString(Path.of(args[1])))));
    }

    static String markdown(JsonNode coldStart, JsonNode jmh) {
        StringBuilder table = new StringBuilder()
                .append("### Benchmarks\n\n")
                .append("Reporting only, nothing here fails the build. The task JVM ran on Java ")
                .append(coldStart.path(ColdStart.JAVA_VERSION).asString()).append(".\n\n")
                .append("| Measure | Result | Target |\n")
                .append("|---|---|---|\n");
        JsonNode wall = coldStart.path(ColdStart.COLD_START_MS);
        JsonNode rss = coldStart.path(ColdStart.PEAK_RSS_MIB);
        JsonNode node = coldStart.path(ColdStart.NODE_DURATION_MS);
        row(table, "Cold start of a no-op task, p50 of " + ColdStart.RUNS, millis(wall.path(ColdStart.P50)),
                "under 100 ms");
        row(table, "Cold start of a no-op task, p95 of " + ColdStart.RUNS, millis(wall.path(ColdStart.P95)),
                "under 150 ms");
        row(table, "First attempt, with the harness compiled", millis(coldStart.path(ColdStart.FIRST_ATTEMPT_MS)),
                "under 2500 ms");
        row(table, "Peak RSS of a no-op task, highest of " + ColdStart.RUNS,
                mebibytes(rss.path(ColdStart.MAX)) + " (p50 " + mebibytes(rss.path(ColdStart.P50)) + ")",
                "under 96 MiB");
        row(table, "Task node duration in core, p50 and p95", millis(node.path(ColdStart.P50)) + " and "
                + millis(node.path(ColdStart.P95)), "");

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
        return table.toString();
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

    private static String mebibytes(JsonNode value) {
        return format("%.1f", value.asDouble()) + " MiB";
    }

    private static String format(String pattern, double value) {
        return String.format(Locale.ROOT, pattern, value);
    }
}
