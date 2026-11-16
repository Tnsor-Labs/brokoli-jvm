package dev.brokoli.pipeline;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SinkDbTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void writesItsOptionsInThePythonSdksOrderWithTheRetriesBeforeCreatingTheTable() {
        Pipeline p = Pipeline.builder("Load").build();

        p.sinkDb("Load", SinkDb.options()
                .pool("clickhouse")
                .tableEngine("ReplacingMergeTree")
                .createTable(true)
                .timeout(Duration.ofMinutes(5))
                .retries(2)
                .truncate(false)
                .upsert("id", "updated_at")
                .uri("clickhouse://db:9000/events")
                .connId("clickhouse")
                .table("events"));

        assertThat(config(p).toString()).isEqualTo("""
                {"table":"events","mode":"upsert","conn_id":"clickhouse","uri":"clickhouse://db:9000/events",\
                "key_columns":["id","updated_at"],"truncate":false,"max_retries":2,"retry_backoff":"exponential",\
                "timeout":300,"create_table":true,"table_engine":"ReplacingMergeTree","pool":"clickhouse"}""");
        assertThat(CoreSchema.pipelineErrors(p.toJson())).isEmpty();
    }

    @Test
    void appendsByDefaultAndWritesItsInput() {
        Pipeline p = Pipeline.builder("Load").build();
        DatasetRef rows = p.sourceDb("Orders", SourceDb.options().query("SELECT 1"));

        p.sinkDb("Load", rows, SinkDb.options().table("orders"));

        JsonNode ir = JSON.readTree(p.toJson());
        assertThat(ir.path("nodes").get(1).path("config").toString()).isEqualTo("""
                {"table":"orders","mode":"append"}""");
        assertThat(ir.path("nodes").get(1).path("capabilities")).isEqualTo(JSON.readTree("""
                ["sink"]"""));
        assertThat(ir.path("edges")).isEqualTo(JSON.readTree("""
                [{"from":"orders_1","to":"load_1"}]"""));
    }

    @Test
    void dropsTheKeyColumnsWhenAnUpsertBecomesAnOverwrite() {
        Pipeline p = Pipeline.builder("Load").build();

        p.sinkDb("Load", SinkDb.options().table("orders").upsert("id").mode(WriteMode.OVERWRITE));

        assertThat(config(p).toString()).isEqualTo("""
                {"table":"orders","mode":"overwrite"}""");
    }

    @Test
    void refusesAnUpsertWithoutKeyColumns() {
        assertThatThrownBy(() -> SinkDb.options().upsert())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("an upsert needs the key columns it matches rows on; give them with upsert(\"id\")");
        assertThatThrownBy(() -> SinkDb.options().mode(WriteMode.UPSERT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("an upsert needs the key columns it matches rows on; give them with upsert(\"id\")"
                        + " instead of mode(WriteMode.UPSERT)");
    }

    @Test
    void refusesKeyColumnsForAModeThatIsNotAnUpsert() {
        assertThatThrownBy(() -> new SinkDb("orders", WriteMode.APPEND, List.of("id"), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), NodeOptions.defaults()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("the key columns [id] are only written for an upsert, but the mode is append");
    }

    private static JsonNode config(Pipeline p) {
        return JSON.readTree(p.toJson()).path("nodes").get(0).path("config");
    }
}
