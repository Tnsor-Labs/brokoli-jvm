package dev.brokoli.pipeline;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PythonReferenceTest {

    private static final Path EXPECTED = Path.of(System.getProperty("brokoli.python-reference", "../python-reference"))
            .resolve("expected");

    @Test
    void rendersTheMinimalPipelineByteForByteAsThePythonSdkDoes() throws IOException {
        Pipeline p = minimal();

        assertThat(p.render()).isEqualTo(expected("minimal.ir.json"));
    }

    @Test
    void digestsTheMinimalPipelineAsThePythonSdkDoes() throws IOException {
        Pipeline p = minimal();

        assertThat(p.digest()).isEqualTo(expected("minimal.digest").strip());
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

    private static String expected(String name) throws IOException {
        return Files.readString(EXPECTED.resolve(name), StandardCharsets.UTF_8);
    }
}
