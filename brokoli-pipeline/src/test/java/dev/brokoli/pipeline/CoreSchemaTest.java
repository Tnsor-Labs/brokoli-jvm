package dev.brokoli.pipeline;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CoreSchemaTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Path DATASET_SCHEMA_CASES = Path.of(System.getProperty("brokoli.core-contract", "../core-contract"))
            .resolve("docs/schema/fixtures/dataset-schema-v1");
    private static final String VIOLATION = "_violation";

    @Test
    void acceptsEveryPositiveDatasetSchemaCaseOfCore() throws IOException {
        List<Path> cases = cases("positive");

        assertThat(cases).isNotEmpty();
        for (Path positive : cases) {
            assertThat(CoreSchema.datasetSchemaErrors(Files.readString(positive))).as(positive.toString()).isEmpty();
        }
    }

    @Test
    void refusesEveryNegativeDatasetSchemaCaseOfCoreWithoutItsViolationNote() throws IOException {
        List<Path> cases = cases("negative");

        assertThat(cases).isNotEmpty();
        for (Path negative : cases) {
            ObjectNode document = (ObjectNode) JSON.readTree(Files.readString(negative));
            assertThat(document.has(VIOLATION)).as(negative.toString()).isTrue();
            document.remove(VIOLATION);
            assertThat(CoreSchema.datasetSchemaErrors(JSON.writeValueAsString(document))).as(negative.toString()).isNotEmpty();
        }
    }

    private static List<Path> cases(String kind) throws IOException {
        try (Stream<Path> files = Files.list(DATASET_SCHEMA_CASES.resolve(kind))) {
            return files.filter(file -> file.toString().endsWith(".json")).sorted().toList();
        }
    }
}
