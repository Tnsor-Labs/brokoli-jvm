package dev.brokoli.bundle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

class TaskInterfacesTest {

    private static final String VECTORS = "/schema/fixtures/task-interface/differential/";

    @ParameterizedTest
    @ValueSource(strings = {"dataset-record", "defaulted-parameter", "enum-field", "nested-record",
            "nullable-required-field", "required-parameter"})
    void rendersAndDigestsEachCoreVectorsInterfaceAsCoreExpects(String name) {
        JsonNode vector = vector(name);
        JsonNode nodeInterface = vector.path("expected_node_interface");

        assertThat(CanonicalJson.render(TaskInterfaces.forHashing(TaskInterfaces.withPorts(nodeInterface))))
                .isEqualTo(vector.path("expected_interface_canonical").asString());
        assertThat(TaskInterfaces.interfaceDigest(nodeInterface))
                .isEqualTo(vector.path("expected_interface_digest").asString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"defaulted-parameter", "required-parameter"})
    void rendersAndDigestsEachCoreVectorsParametersAsCoreExpects(String name) {
        JsonNode vector = vector(name);
        String canonical = CanonicalJson.render(TaskInterfaces.forHashing(vector.path("expected_pipeline_parameters")));

        assertThat(canonical).isEqualTo(vector.path("expected_parameters_canonical").asString());
        assertThat(Digests.digest(Utf8.bytes(canonical))).isEqualTo(vector.path("expected_parameters_digest").asString());
    }

    @Test
    void fillsAnInputAndAResultDatasetOfUnknownRowsWhenTheTaskDeclaresNoRowTypes() {
        assertThat(TaskInterfaces.interfaceDigest(null))
                .isEqualTo(vector("defaulted-parameter").path("expected_interface_digest").asString());
    }

    @Test
    void keepsADeclaredInterfaceAsItIs() {
        JsonNode nodeInterface = vector("dataset-record").path("expected_node_interface");

        assertThat(TaskInterfaces.withPorts(nodeInterface)).isSameAs(nodeInterface);
    }

    @Test
    void givesTheSameDigestWhateverOrderTheRecordFieldsAreDeclaredIn() {
        JsonNode declared = TaskFile.JSON.readTree(record("[{\"name\": \"b\", \"type\": {\"kind\": \"string\"}},"
                + " {\"name\": \"a\", \"type\": {\"kind\": \"int64\"}}]"));
        JsonNode reordered = TaskFile.JSON.readTree(record("[{\"name\": \"a\", \"type\": {\"kind\": \"int64\"}},"
                + " {\"name\": \"b\", \"type\": {\"kind\": \"string\"}}]"));

        assertThat(TaskInterfaces.interfaceDigest(declared)).isEqualTo(TaskInterfaces.interfaceDigest(reordered));
    }

    @Test
    void givesAnotherDigestWhenEnumValuesAreReordered() {
        JsonNode declared = TaskFile.JSON.readTree(enumRow("[\"pending\", \"done\"]"));
        JsonNode reordered = TaskFile.JSON.readTree(enumRow("[\"done\", \"pending\"]"));

        assertThat(TaskInterfaces.interfaceDigest(declared)).isNotEqualTo(TaskInterfaces.interfaceDigest(reordered));
    }

    private static JsonNode vector(String name) {
        return TaskFile.JSON.readTree(CoreContract.resource(VECTORS + name + ".json"));
    }

    private static String record(String fields) {
        return "{\"contract\": \"brokoli.task-interface/v1\", \"inputs\": {\"input\": {\"value\": {\"kind\": \"dataset\","
                + " \"row\": {\"kind\": \"record\", \"fields\": " + fields + ", \"additional_fields\": false}}}}}";
    }

    private static String enumRow(String values) {
        return record("[{\"name\": \"status\", \"type\": {\"kind\": \"enum\", \"values\": " + values + "}}]");
    }
}
