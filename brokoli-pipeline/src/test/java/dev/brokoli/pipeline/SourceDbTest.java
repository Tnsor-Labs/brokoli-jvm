package dev.brokoli.pipeline;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class SourceDbTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void writesItsOptionsInThePythonSdksOrderWithTheSchemaHintLast() {
        Pipeline p = Pipeline.builder("Shop").build();

        DatasetRef orders = p.sourceDb("Orders", SourceDb.options()
                .pool("warehouse")
                .timeout(Duration.ofMinutes(1))
                .retries(3)
                .uri("sqlite:///shop.db")
                .connId("shop")
                .query("SELECT * FROM orders"));

        assertThat(orders.nodeId()).isEqualTo("orders_1");
        assertThat(node(p).path("config").toString()).isEqualTo("""
                {"query":"SELECT * FROM orders","conn_id":"shop","uri":"sqlite:///shop.db","max_retries":3,\
                "retry_backoff":"exponential","timeout":60,"pool":"warehouse","_schema_hint":"query_result"}""");
        assertThat(node(p).path("capabilities")).isEqualTo(JSON.readTree("""
                ["source","dataset-output"]"""));
        assertThat(CoreSchema.pipelineErrors(p.toJson())).isEmpty();
    }

    @Test
    void writesTheSchemaAfterTheUriAndBeforeTheRetriesAsThePythonSdkDoes() {
        Pipeline p = Pipeline.builder("Shop").build();

        p.sourceDb("Orders", SourceDb.options()
                .retries(2)
                .schema(DatasetSchema.of(Column.of("id", DataType.int64())))
                .uri("sqlite:///shop.db")
                .query("SELECT id FROM orders"));

        assertThat(node(p).path("config").toString()).isEqualTo("""
                {"query":"SELECT id FROM orders","uri":"sqlite:///shop.db",\
                "schema":{"contract":"brokoli.dataset-schema/v1","columns":[{"name":"id","type":{"kind":"int64"}}],\
                "additional_columns":"unknown"},"max_retries":2,"retry_backoff":"exponential",\
                "_schema_hint":"query_result"}""");
        assertThat(CoreSchema.pipelineErrors(p.toJson())).isEmpty();
    }

    @Test
    void writesTheSchemaHintForAnEmptyQueryAsThePythonSdkDoes() {
        Pipeline p = Pipeline.builder("Shop").build();

        p.sourceDb("Orders", SourceDb.options());

        assertThat(node(p).path("config").toString()).isEqualTo("""
                {"query":"","_schema_hint":"query_result"}""");
    }

    private static JsonNode node(Pipeline p) {
        return JSON.readTree(p.toJson()).path("nodes").get(0);
    }
}
