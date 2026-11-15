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
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PythonReferenceTest {

    private static final Path EXPECTED = Path.of(System.getProperty("brokoli.python-reference", "../python-reference"))
            .resolve("expected");
    private static final ZoneId MAPUTO = ZoneId.of("Africa/Maputo");

    static Stream<Arguments> fixtures() {
        return Stream.of(
                Arguments.of("minimal", (Supplier<Pipeline>) PythonReferenceTest::minimal),
                Arguments.of("options", (Supplier<Pipeline>) PythonReferenceTest::options),
                Arguments.of("bash", (Supplier<Pipeline>) PythonReferenceTest::bash));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void rendersThePipelineByteForByteAsThePythonSdkDoes(String fixture, Supplier<Pipeline> pipeline)
            throws IOException {
        assertThat(pipeline.get().render()).isEqualTo(expected(fixture + ".ir.json"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void digestsThePipelineAsThePythonSdkDoes(String fixture, Supplier<Pipeline> pipeline) throws IOException {
        assertThat(pipeline.get().digest()).isEqualTo(expected(fixture + ".digest").strip());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    void compilesIrThatCoresSchemaAccepts(String fixture, Supplier<Pipeline> pipeline) {
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

    private static String expected(String name) throws IOException {
        return Files.readString(EXPECTED.resolve(name), StandardCharsets.UTF_8);
    }
}
