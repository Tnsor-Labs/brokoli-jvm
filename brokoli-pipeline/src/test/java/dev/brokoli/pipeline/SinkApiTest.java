package dev.brokoli.pipeline;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SinkApiTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void writesItsOptionsInThePythonSdksOrderWithTheCredentialsBeforeTheRetries() {
        Pipeline p = Pipeline.builder("Push").build();

        p.sinkApi("Push", SinkApi.options()
                .pool("api")
                .timeout(Duration.ofSeconds(30))
                .retries(1)
                .basicAuth("loader", "${secret.sink_password}")
                .batchSize(250)
                .connId("sink_api")
                .headers(Map.of("X-Source", "brokoli"))
                .method("PUT")
                .url("https://sink.example.com/ingest"));

        assertThat(config(p).toString()).isEqualTo("""
                {"url":"https://sink.example.com/ingest","method":"PUT","headers":{"X-Source":"brokoli"},\
                "conn_id":"sink_api","batch_size":250,"auth_user":"loader","auth_password":"${secret.sink_password}",\
                "max_retries":1,"retry_backoff":"exponential","timeout":30,"pool":"api"}""");
        assertThat(CoreSchema.pipelineErrors(p.toJson())).isEmpty();
    }

    @Test
    void postsByDefaultAndSendsItsInput() {
        Pipeline p = Pipeline.builder("Push").build();
        DatasetRef rows = p.sourceDb("Orders", SourceDb.options().query("SELECT 1"));

        p.sinkApi("Push", rows, SinkApi.options().url("https://sink.example.com/ingest"));

        JsonNode ir = JSON.readTree(p.toJson());
        assertThat(ir.path("nodes").get(1).path("config").toString()).isEqualTo("""
                {"url":"https://sink.example.com/ingest","method":"POST"}""");
        assertThat(ir.path("nodes").get(1).path("capabilities")).isEqualTo(JSON.readTree("""
                ["sink"]"""));
        assertThat(ir.path("edges")).isEqualTo(JSON.readTree("""
                [{"from":"orders_1","to":"push_1"}]"""));
    }

    @Test
    void takesABatchOfOneRowButRefusesABatchSizeThatIsNotPositive() {
        assertThat(SinkApi.options().batchSize(1).batchSize()).hasValue(1);
        assertThatThrownBy(() -> SinkApi.options().batchSize(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("batch size 0 is not positive");
    }

    @Test
    void refusesAUserWithoutAPasswordAndAPasswordWithoutAUser() {
        assertThatThrownBy(() -> new SinkApi("/events", "POST", Map.of(), Optional.empty(), OptionalInt.empty(),
                Optional.of("loader"), Optional.empty(), NodeOptions.defaults()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("HTTP Basic needs both a user and a password; give them with basicAuth(user, password)");
        assertThatThrownBy(() -> new SinkApi("/events", "POST", Map.of(), Optional.empty(), OptionalInt.empty(),
                Optional.empty(), Optional.of("secret"), NodeOptions.defaults()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("HTTP Basic needs both a user and a password; give them with basicAuth(user, password)");
    }

    private static JsonNode config(Pipeline p) {
        return JSON.readTree(p.toJson()).path("nodes").get(0).path("config");
    }
}
