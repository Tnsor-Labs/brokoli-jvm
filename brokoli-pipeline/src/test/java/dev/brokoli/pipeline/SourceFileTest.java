package dev.brokoli.pipeline;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SourceFileTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void writesTheSchemaAfterTheFormatAndBeforeTheConnectionAsThePythonSdkDoes() {
        Pipeline p = Pipeline.builder("Lake").build();

        p.sourceFile("Read", SourceFile.options()
                .connId("lake")
                .schema(DatasetSchema.of(Column.of("id", DataType.int64())).open())
                .path("/data/in.csv"));

        assertThat(JSON.readTree(p.toJson()).path("nodes").get(0).path("config").toString()).isEqualTo("""
                {"path":"/data/in.csv","format":"csv",\
                "schema":{"contract":"brokoli.dataset-schema/v1","columns":[{"name":"id","type":{"kind":"int64"}}],\
                "additional_columns":"open"},"conn_id":"lake"}""");
        assertThat(CoreSchema.pipelineErrors(p.toJson())).isEmpty();
    }
}
