package dev.brokoli.benchmarks;

import dev.brokoli.bundle.TaskBundle;
import dev.brokoli.bundle.TaskBundles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ColdStart {

    static final int RUNS = 30;
    static final String JAVA_VERSION = "java_version";
    static final String FIRST_ATTEMPT_MS = "first_attempt_ms";
    static final String COLD_START_MS = "cold_start_ms";
    static final String PEAK_RSS_MIB = "peak_rss_mib";
    static final String NODE_DURATION_MS = "node_duration_ms";
    static final String P50 = "p50";
    static final String P95 = "p95";
    static final String MAX = "max";
    static final String SAMPLES = "samples";

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SERVER = "BROKOLI_E2E_SERVER";
    private static final String API_KEY = "BROKOLI_E2E_API_KEY";
    private static final String USAGE = "usage: ColdStart <classes directory> <brokoli-task jar> <records file> <result file>";
    private static final int ARGUMENTS = 4;
    private static final String BUILDER = "brokoli-jvm-benchmarks";
    private static final String TASK_METHOD = "noop";
    private static final String TASK_FILE = "META-INF/brokoli/tasks/dev.brokoli.benchmarks.Noop.json";
    private static final String BUNDLE_FORMAT = "brokoli.task-bundle/v2";
    private static final String NODE = "noop";
    private static final Duration RUN_WAIT = Duration.ofSeconds(120);
    private static final double MICROS_PER_MILLI = 1000.0;
    private static final double KIB_PER_MIB = 1024.0;
    private static final int PERCENTILE_50 = 50;
    private static final int PERCENTILE_95 = 95;

    private ColdStart() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != ARGUMENTS) {
            throw new BenchmarkException(USAGE);
        }
        Path classes = Path.of(args[0]);
        Path taskJar = Path.of(args[1]);
        Path records = Path.of(args[2]);
        Path result = Path.of(args[3]);
        CoreServer core = new CoreServer(requiredEnv(SERVER), requiredEnv(API_KEY));

        TaskBundle bundle = TaskBundles.of(classes, List.of(taskJar), BUILDER).stream()
                .filter(candidate -> candidate.method().equals(TASK_METHOD))
                .findFirst()
                .orElseThrow(() -> new BenchmarkException("no task " + TASK_METHOD + " in " + classes));
        ByteArrayOutputStream archive = new ByteArrayOutputStream();
        String digest = bundle.writeTo(archive);
        core.upload(digest, archive.toByteArray());
        String pipelineId = core.createPipeline(pipeline(bundle, digest, taskInterface(classes)));

        Files.deleteIfExists(records);
        double firstAttempt = nodeDuration(core.awaitSuccess(core.run(pipelineId), RUN_WAIT));
        double[] durations = new double[RUNS];
        for (int run = 0; run < RUNS; run++) {
            durations[run] = nodeDuration(core.awaitSuccess(core.run(pipelineId), RUN_WAIT));
        }

        List<String> lines = Files.readAllLines(records);
        if (lines.size() != RUNS + 1) {
            throw new BenchmarkException(records + " has " + lines.size() + " records, expected one per attempt, "
                    + (RUNS + 1));
        }
        double[] wall = new double[RUNS];
        double[] rss = new double[RUNS];
        for (int run = 0; run < RUNS; run++) {
            String[] fields = lines.get(run + 1).trim().split(" ");
            wall[run] = Long.parseLong(fields[0]) / MICROS_PER_MILLI;
            rss[run] = Long.parseLong(fields[1]) / KIB_PER_MIB;
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put(JAVA_VERSION, System.getProperty("java.version"));
        report.put(FIRST_ATTEMPT_MS, firstAttempt);
        report.put(COLD_START_MS, Map.of(P50, Percentiles.nearestRank(wall, PERCENTILE_50),
                P95, Percentiles.nearestRank(wall, PERCENTILE_95), SAMPLES, wall));
        report.put(PEAK_RSS_MIB, Map.of(P50, Percentiles.nearestRank(rss, PERCENTILE_50),
                MAX, Percentiles.max(rss), SAMPLES, rss));
        report.put(NODE_DURATION_MS, Map.of(P50, Percentiles.nearestRank(durations, PERCENTILE_50),
                P95, Percentiles.nearestRank(durations, PERCENTILE_95), SAMPLES, durations));
        Files.write(result, JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(report));
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isEmpty()) {
            throw new BenchmarkException(name + " is not set: the cold start benchmark needs a running core server");
        }
        return value;
    }

    private static JsonNode taskInterface(Path classes) throws IOException {
        Path taskFile = classes.resolve(TASK_FILE);
        for (JsonNode task : JSON.readTree(Files.readString(taskFile)).path("tasks").values()) {
            if (TASK_METHOD.equals(task.path("method").asString())) {
                return task.path("interface");
            }
        }
        throw new BenchmarkException(taskFile + " has no task " + TASK_METHOD);
    }

    private static Map<String, Object> pipeline(TaskBundle bundle, String digest, JsonNode taskInterface) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", NODE);
        node.put("type", "task");
        node.put("name", bundle.name());
        node.put("config", Map.of("task_bundle", Map.of("digest", digest, "format", BUNDLE_FORMAT)));
        node.put("interface", taskInterface);
        Map<String, Object> pipeline = new LinkedHashMap<>();
        pipeline.put("name", "JVM SDK cold start " + UUID.randomUUID());
        pipeline.put("nodes", List.of(node));
        pipeline.put("edges", List.of());
        return pipeline;
    }

    private static double nodeDuration(JsonNode run) {
        for (JsonNode nodeRun : run.path("node_runs").values()) {
            if (NODE.equals(nodeRun.path("node_id").asString())) {
                return nodeRun.path("duration_ms").asDouble();
            }
        }
        throw new BenchmarkException("run " + run.path("id").asString() + " has no node run for " + NODE);
    }
}
