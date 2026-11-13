package dev.brokoli.benchmarks;

import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LargeInput {

    static final int ROWS = 1_000_000;
    static final int RUNS = 3;
    static final String ROWS_KEY = "rows";
    static final String SDK = "sdk";
    static final String RAW_MAP = "raw_map";
    static final String PYTHON = "python";
    static final String NODE_MS = "node_ms";
    static final String RSS_MIB = "rss_mib";

    private static final String USAGE = "usage: LargeInput <classes directory> <brokoli-task jar>"
            + " <raw Map bundle> <python paid bundle> <records directory> <result file>";
    private static final int ARGUMENTS = 6;
    private static final String METHOD = "paidLines";
    private static final String CLASS = "dev.brokoli.benchmarks.Lines";
    private static final int TIMEOUT_SECONDS = 600;
    private static final Duration RUN_WAIT = Duration.ofSeconds(TIMEOUT_SECONDS);

    private LargeInput() {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != ARGUMENTS) {
            throw new BenchmarkException(USAGE);
        }
        Path classes = Path.of(args[0]);
        Path taskJar = Path.of(args[1]);
        byte[] rawMap = Files.readAllBytes(Path.of(args[2]));
        byte[] python = Files.readAllBytes(Path.of(args[3]));
        Attempts attempts = new Attempts(Path.of(args[4]));
        Path result = Path.of(args[5]);
        CoreServer core = CoreServer.fromEnvironment();

        Path input = Files.createTempFile("lines", ".json");
        try {
            try (OutputStream out = Files.newOutputStream(input)) {
                ColdStart.JSON.writeValue(out, Lines.generate(ROWS));
            }
            JsonNode taskInterface = ColdStart.taskInterface(classes, CLASS, METHOD);
            String path = input.toAbsolutePath().toString();
            Task[] tasks = {
                new Task(SDK, Attempts.JVM, core.createPipeline(Pipelines.fromFile("JVM SDK 1M rows", path,
                        core.upload(ColdStart.archive(ColdStart.bundle(classes, taskJar, METHOD))), taskInterface,
                        TIMEOUT_SECONDS))),
                new Task(RAW_MAP, Attempts.JVM, core.createPipeline(Pipelines.fromFile("JVM raw Map 1M rows",
                        path, core.upload(rawMap), taskInterface, TIMEOUT_SECONDS))),
                new Task(PYTHON, Attempts.PYTHON, core.createPipeline(Pipelines.fromFile("Python 1M rows", path,
                        core.upload(python), taskInterface, TIMEOUT_SECONDS))),
            };
            for (int run = 0; run < RUNS; run++) {
                for (Task task : tasks) {
                    task.attempt(core, attempts, run);
                }
            }

            Map<String, Object> report = new LinkedHashMap<>();
            report.put(ROWS_KEY, ROWS);
            for (Task task : tasks) {
                report.put(task.name, task.summary());
            }
            Files.write(result, ColdStart.JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(report));
        } finally {
            Files.deleteIfExists(input);
        }
    }

    private static final class Task {

        private final String name;
        private final String runtime;
        private final String pipelineId;
        private final double[] node = new double[RUNS];
        private final double[] rss = new double[RUNS];

        Task(String name, String runtime, String pipelineId) {
            this.name = name;
            this.runtime = runtime;
            this.pipelineId = pipelineId;
        }

        void attempt(CoreServer core, Attempts attempts, int run) throws IOException, InterruptedException {
            int before = attempts.count(runtime);
            node[run] = Pipelines.nodeDuration(core.awaitSuccess(core.run(pipelineId), RUN_WAIT));
            rss[run] = attempts.since(runtime, before).rssMebibytes();
        }

        Map<String, Object> summary() {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put(NODE_MS, Summary.of(node));
            summary.put(RSS_MIB, Summary.of(rss));
            return summary;
        }
    }
}
