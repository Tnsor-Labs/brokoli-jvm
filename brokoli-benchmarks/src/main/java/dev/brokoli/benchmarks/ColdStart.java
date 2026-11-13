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

public final class ColdStart {

    static final int RUNS = 30;
    static final String JAVA_VERSION = "java_version";
    static final String FIRST_ATTEMPT_MS = "first_attempt_ms";
    static final String WALL_MS = "wall_ms";
    static final String RSS_MIB = "rss_mib";
    static final String NODE_MS = "node_ms";

    static final JsonMapper JSON = JsonMapper.builder().build();
    static final String BUILDER = "brokoli-jvm-benchmarks";
    static final String TASK_FILES = "META-INF/brokoli/tasks/";

    private static final String USAGE = "usage: ColdStart <classes directory> <brokoli-task jar>"
            + " <python no-op bundle> <records directory> <result file>";
    private static final int ARGUMENTS = 5;
    private static final String NOOP = "noop";
    private static final String NOOP_CLASS = "dev.brokoli.benchmarks.Noop";
    private static final Duration RUN_WAIT = Duration.ofSeconds(120);

    private ColdStart() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != ARGUMENTS) {
            throw new BenchmarkException(USAGE);
        }
        Path classes = Path.of(args[0]);
        Path taskJar = Path.of(args[1]);
        byte[] pythonNoop = Files.readAllBytes(Path.of(args[2]));
        Attempts attempts = new Attempts(Path.of(args[3]));
        Path result = Path.of(args[4]);
        CoreServer core = CoreServer.fromEnvironment();

        TaskBundle bundle = bundle(classes, taskJar, NOOP);
        String jvmPipeline = core.createPipeline(Pipelines.taskOnly("JVM SDK cold start",
                core.upload(archive(bundle)), taskInterface(classes, NOOP_CLASS, NOOP)));
        String pythonPipeline = core.createPipeline(Pipelines.taskOnly("Python cold start",
                core.upload(pythonNoop), null));

        Runtime jvm = new Runtime(Attempts.JVM, jvmPipeline);
        Runtime python = new Runtime(Attempts.PYTHON, pythonPipeline);
        for (int run = 0; run <= RUNS; run++) {
            jvm.attempt(core, attempts, run);
            python.attempt(core, attempts, run);
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put(JAVA_VERSION, System.getProperty("java.version"));
        report.put(Attempts.JVM, jvm.summary());
        report.put(Attempts.PYTHON, python.summary());
        Files.write(result, JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(report));
    }

    static TaskBundle bundle(Path classes, Path taskJar, String method) {
        return TaskBundles.of(classes, List.of(taskJar), BUILDER).stream()
                .filter(candidate -> candidate.method().equals(method))
                .findFirst()
                .orElseThrow(() -> new BenchmarkException("no task " + method + " in " + classes));
    }

    static byte[] archive(TaskBundle bundle) throws IOException {
        ByteArrayOutputStream archive = new ByteArrayOutputStream();
        bundle.writeTo(archive);
        return archive.toByteArray();
    }

    static JsonNode taskInterface(Path classes, String className, String method) throws IOException {
        Path taskFile = classes.resolve(TASK_FILES + className + ".json");
        for (JsonNode task : JSON.readTree(Files.readString(taskFile)).path("tasks").values()) {
            if (method.equals(task.path("method").asString())) {
                return task.path("interface");
            }
        }
        throw new BenchmarkException(taskFile + " has no task " + method);
    }

    private static final class Runtime {

        private final String name;
        private final String pipelineId;
        private final double[] wall = new double[RUNS];
        private final double[] rss = new double[RUNS];
        private final double[] node = new double[RUNS];
        private double firstAttempt;

        Runtime(String name, String pipelineId) {
            this.name = name;
            this.pipelineId = pipelineId;
        }

        void attempt(CoreServer core, Attempts attempts, int run) throws IOException, InterruptedException {
            int before = attempts.count(name);
            double duration = Pipelines.nodeDuration(core.awaitSuccess(core.run(pipelineId), RUN_WAIT));
            Attempts.Sample sample = attempts.since(name, before);
            if (run == 0) {
                firstAttempt = duration;
                return;
            }
            wall[run - 1] = sample.wallMillis();
            rss[run - 1] = sample.rssMebibytes();
            node[run - 1] = duration;
        }

        Map<String, Object> summary() {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put(FIRST_ATTEMPT_MS, firstAttempt);
            summary.put(WALL_MS, Summary.of(wall));
            summary.put(RSS_MIB, Summary.of(rss));
            summary.put(NODE_MS, Summary.of(node));
            return summary;
        }
    }
}
