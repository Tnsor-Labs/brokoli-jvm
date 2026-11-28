package dev.brokoli.pipeline;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PythonReferenceTest {

    private static final Path REFERENCE = Path.of(System.getProperty("brokoli.python-reference", "../python-reference"));
    private static final Path EXPECTED = REFERENCE.resolve("expected");
    private static final Path LOCAL_EXPECTED = REFERENCE.resolve("local").resolve("expected");
    private static final ZoneId MAPUTO = ZoneId.of("Africa/Maputo");

    static Stream<Arguments> fixtures() {
        return Stream.of(
                Arguments.of("minimal", EXPECTED, (Supplier<Pipeline>) PythonReferenceTest::minimal),
                Arguments.of("options", EXPECTED, (Supplier<Pipeline>) PythonReferenceTest::options),
                Arguments.of("bash", EXPECTED, (Supplier<Pipeline>) PythonReferenceTest::bash),
                Arguments.of("data", EXPECTED, (Supplier<Pipeline>) PythonReferenceTest::data),
                Arguments.of("join_collision_alias", EXPECTED,
                        (Supplier<Pipeline>) PythonReferenceTest::joinCollisionAlias),
                Arguments.of("io_options", EXPECTED, (Supplier<Pipeline>) PythonReferenceTest::ioOptions),
                Arguments.of("dataset_schema", EXPECTED, (Supplier<Pipeline>) PythonReferenceTest::datasetSchema),
                Arguments.of("dataset_schema_join", EXPECTED,
                        (Supplier<Pipeline>) PythonReferenceTest::datasetSchemaJoin),
                Arguments.of("sla_utc", LOCAL_EXPECTED, (Supplier<Pipeline>) PythonReferenceTest::slaUtc),
                Arguments.of("retry_delay_only", LOCAL_EXPECTED,
                        (Supplier<Pipeline>) PythonReferenceTest::retryDelayOnly),
                Arguments.of("node_key_between_ids", LOCAL_EXPECTED,
                        (Supplier<Pipeline>) PythonReferenceTest::nodeKeyBetweenIds),
                Arguments.of("api_code_notify_options", LOCAL_EXPECTED,
                        (Supplier<Pipeline>) PythonReferenceTest::apiCodeNotifyOptions));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void rendersThePipelineByteForByteAsThePythonSdkDoes(String fixture, Path expected, Supplier<Pipeline> pipeline)
            throws IOException {
        assertThat(pipeline.get().render()).isEqualTo(read(expected, fixture + ".ir.json"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void digestsThePipelineAsThePythonSdkDoes(String fixture, Path expected, Supplier<Pipeline> pipeline)
            throws IOException {
        assertThat(pipeline.get().digest()).isEqualTo(read(expected, fixture + ".digest").strip());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void compilesIrThatCoresSchemaAccepts(String fixture, Path expected, Supplier<Pipeline> pipeline) {
        assertThat(CoreSchema.pipelineErrors(pipeline.get().toJson())).isEmpty();
    }

    private static Pipeline minimal() {
        Pipeline p = Pipeline.builder("Sales ETL")
                .pipelineId("sales-etl")
                .description("Clean the daily sales file")
                .tags("finance", "daily")
                .build();
        DatasetRef rows = p.sourceFile("Read", SourceFile.options().path("/data/sales.csv").format("csv"));
        DatasetRef clean = rows.then(p.transform("Clean", Transform.options().rules(List.of(
                Map.of("type", "rename", "mapping", Map.of("amt", "amount")),
                Map.of("type", "filter", "condition", "amount > 0")))));
        clean.then(p.sinkFile("Write", SinkFile.options().path("/data/out.csv").format("csv")));
        return p;
    }

    private static Pipeline options() {
        Pipeline p = Pipeline.builder("Hourly Slices")
                .pipelineId("hourly-slices")
                .schedule("0 * * * *")
                .scheduleTimezone(MAPUTO)
                .catchUp(true)
                .sla(LocalTime.of(7, 30), MAPUTO)
                .dependsOn("upstream-etl")
                .tags("intervals")
                .webhook(true)
                .onFailure("https://hooks.example/oncall")
                .build();
        DatasetRef rows = p.sourceFile("Read", SourceFile.options().path("/data/in.csv").format("csv"));
        rows.then(p.sinkFile("Slice", SinkFile.options().path("/data/slice-${interval.start}.csv").format("csv")));
        return p;
    }

    private static Pipeline bash() {
        Pipeline p = Pipeline.builder("Bash export").pipelineId("bash-export").build();
        DatasetRef source = p.sourceFile("Input", SourceFile.options().path("/data/in.csv"));
        p.bash("Refresh", source, Bash.options()
                .command("./refresh.sh --since \"$SINCE\"")
                .workingDir("/data/jobs")
                .env(Map.of("SINCE", "${param.since}"))
                .timeout(Duration.ofSeconds(600))
                .retries(2)
                .retryDelay(Duration.ofSeconds(30))
                .maxMemoryMb(512)
                .maxCpuTime(Duration.ofSeconds(300))
                .nodeKey("refresh-export"));
        return p;
    }

    private static Pipeline data() {
        Pipeline p = Pipeline.builder("Warehouse Load").pipelineId("warehouse-load").build();
        DatasetRef orders = p.sourceDb("Orders", SourceDb.options().connId("prod_pg").query("SELECT * FROM orders"));
        DatasetRef users = p.sourceDb("Users", SourceDb.options()
                .connId("prod_pg")
                .query("SELECT id, email FROM users"));
        DatasetRef joined = p.join("Enrich", orders, users, Join.options().on("user_id"));
        NodeRef gated = joined.then(p.qualityCheck("Gate", QualityCheck.options().rules(List.of(
                Map.of("rule", "not_null", "column", "email", "on_failure", "block")))));
        gated.then(p.sinkDb("Load", SinkDb.options().connId("warehouse").table("orders_enriched").upsert("id")));
        return p;
    }

    private static Pipeline joinCollisionAlias() {
        Pipeline p = Pipeline.builder("Join Collision Policy").pipelineId("join-collision-policy").build();
        DatasetRef left = p.sourceDb("Left", SourceDb.options()
                .connId("warehouse")
                .query("SELECT id, name FROM left_table"));
        DatasetRef right = p.sourceDb("Right", SourceDb.options()
                .connId("warehouse")
                .query("SELECT id, name FROM right_table"));
        p.join("Merge", left, right, Join.options().on("id").rightAlias("right_row"));
        return p;
    }

    private static Pipeline ioOptions() {
        Pipeline p = Pipeline.builder("IO options").pipelineId("io-options").build();
        DatasetRef file = p.sourceFile("Read file", SourceFile.options()
                .path("/data/in.csv")
                .retries(3)
                .timeout(Duration.ofSeconds(60))
                .pool("files"));
        DatasetRef api = p.sourceApi("Fetch", SourceApi.options()
                .url("https://api.example.com/graphql")
                .sourceType("graphql")
                .pool("api"));
        DatasetRef db = p.sourceDb("Query", SourceDb.options().query("SELECT 1").connId("warehouse").pool("warehouse"));
        NodeRef shaped = p.code("Shape", file, Code.typescript("export default (rows) => rows")
                .nodePath("/usr/bin/node")
                .maxMemoryMb(256)
                .maxCpuTime(Duration.ofSeconds(30))
                .retries(1)
                .retryDelay(Duration.ofMillis(2000))
                .pool("compute"));
        p.sinkDb("Load", shaped, SinkDb.options()
                .table("events")
                .connId("clickhouse")
                .createTable(true)
                .tableEngine("ReplacingMergeTree")
                .retries(2)
                .timeout(Duration.ofSeconds(300))
                .pool("clickhouse"));
        p.sinkFile("Save", api, SinkFile.options().path("/data/out.csv").pool("files"));
        p.sinkApi("Post", db, SinkApi.options().url("https://hooks.example.com/x").retries(1).pool("api"));
        p.notify("Tell", db, Notify.webhook("https://hooks.example.com/notify")
                .retries(2)
                .retryDelay(Duration.ofMillis(1000)));
        return p;
    }

    private static Pipeline datasetSchema() {
        Pipeline p = Pipeline.builder("Dataset Schema").pipelineId("dataset-schema").build();
        p.sourceApi("Fetch", SourceApi.options()
                .url("https://example.test/data")
                .schema(DatasetSchema.of(Column.of("id", DataType.int64()),
                        Column.of("name", DataType.string().nullable())).closed()));
        return p;
    }

    private static Pipeline datasetSchemaJoin() {
        Pipeline p = Pipeline.builder("Dataset Schema Join").pipelineId("dataset-schema-join").build();
        DatasetSchema rows = DatasetSchema.of(Column.of("id", DataType.int64()), Column.of("name", DataType.string()))
                .closed();
        DatasetRef left = p.sourceApi("Left", SourceApi.options().url("https://example.test/left").schema(rows));
        DatasetRef right = p.sourceApi("Right", SourceApi.options().url("https://example.test/right").schema(rows));
        p.join("Merge", left, right, Join.options().on("id").rightAlias("right_row"));
        return p;
    }

    private static Pipeline apiCodeNotifyOptions() {
        Pipeline p = Pipeline.builder("Item Push").pipelineId("item-push").build();
        DatasetRef items = p.sourceApi("Search", SourceApi.options()
                .url("/items/search")
                .method("POST")
                .headers(Map.of("Accept", "application/json", "X-Api-Key", "${secret.items_key}"))
                .body(Map.of("query", "{ items { id } }", "limit", 100,
                        "nested", Map.of("active", true, "tags", Arrays.asList("a", null))))
                .connId("items_api")
                .params(Map.of("since", "${param.since}", "status", "active"))
                .records("data.items")
                .retries(2)
                .retryDelay(Duration.ofMillis(1500))
                .timeout(Duration.ofSeconds(45)));
        NodeRef shaped = p.code("Shape", items, Code.python("output_data = {'columns': columns, 'rows': rows}")
                .pythonPath("/opt/venv/bin/python")
                .timeout(Duration.ofSeconds(120))
                .maxMemoryMb(512)
                .maxCpuTime(Duration.ofSeconds(60)));
        p.sinkApi("Push", shaped, SinkApi.options()
                .url("https://sink.example.com/ingest")
                .method("PUT")
                .headers(Map.of("X-Source", "brokoli"))
                .connId("sink_api")
                .batchSize(250)
                .basicAuth("loader", "${secret.sink_password}")
                .timeout(Duration.ofSeconds(30))
                .pool("api"));
        p.notify("Alert", shaped, Notify.slack("https://hooks.slack.com/services/T0/B0/x")
                .message("Pipeline {{pipeline}} pushed {{rows}} rows")
                .channel("#data-alerts")
                .timeout(Duration.ofSeconds(10)));
        return p;
    }

    private static Pipeline slaUtc() {
        Pipeline p = Pipeline.builder("Morning Report")
                .pipelineId("morning-report")
                .schedule("0 6 * * *")
                .scheduleTimezone(ZoneOffset.UTC)
                .sla(LocalTime.of(6, 5))
                .build();
        DatasetRef rows = p.sourceFile("Read", SourceFile.options().path("/data/in.csv").format("csv"));
        rows.then(p.sinkFile("Write", SinkFile.options().path("/data/out.csv").format("csv")));
        return p;
    }

    private static Pipeline retryDelayOnly() {
        Pipeline p = Pipeline.builder("Slow Upstream").pipelineId("slow-upstream").build();
        DatasetRef rows = p.sourceFile("Read", SourceFile.options()
                .path("/data/in.csv")
                .format("csv")
                .retryDelay(Duration.ofMillis(1500)));
        rows.then(p.sinkFile("Write", SinkFile.options()
                .path("/data/out.csv")
                .format("csv")
                .timeout(Duration.ofSeconds(30))));
        return p;
    }

    private static Pipeline nodeKeyBetweenIds() {
        Pipeline p = Pipeline.builder("Three Reads").pipelineId("three-reads").build();
        DatasetRef first = p.sourceFile("Read", SourceFile.options().path("/data/a.csv").format("csv"));
        DatasetRef keyed = p.sourceFile("Read", SourceFile.options()
                .path("/data/b.csv")
                .format("csv")
                .nodeKey("read_2"));
        DatasetRef last = p.sourceFile("Read", SourceFile.options().path("/data/c.csv").format("csv"));
        first.then(p.sinkFile("Write", SinkFile.options().path("/data/a-out.csv").format("csv")));
        keyed.then(p.sinkFile("Write", SinkFile.options().path("/data/b-out.csv").format("csv")));
        last.then(p.sinkFile("Write", SinkFile.options().path("/data/c-out.csv").format("csv")));
        return p;
    }

    private static String read(Path directory, String name) throws IOException {
        return Files.readString(directory.resolve(name), StandardCharsets.UTF_8);
    }
}
