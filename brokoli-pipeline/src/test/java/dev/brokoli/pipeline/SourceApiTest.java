package dev.brokoli.pipeline;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceApiTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void writesItsOptionsInThePythonSdksOrderWithTheSchemaHintLast() {
        Pipeline p = Pipeline.builder("Items").build();

        p.sourceApi("Search", SourceApi.options()
                .pool("api")
                .sourceType("rest")
                .timeout(Duration.ofSeconds(45))
                .retryDelay(Duration.ofMillis(1500))
                .retries(2)
                .records("data.items")
                .params(Map.of("status", "active", "since", "${param.since}"))
                .connId("items_api")
                .body(Map.of("query", "{ items { id } }"))
                .headers(Map.of("X-Api-Key", "${secret.items_key}", "Accept", "application/json"))
                .method("POST")
                .url("/items/search"));

        assertThat(config(p).toString()).isEqualTo("""
                {"url":"/items/search","method":"POST","response":"dataset",\
                "headers":{"Accept":"application/json","X-Api-Key":"${secret.items_key}"},\
                "body":{"query":"{ items { id } }"},"conn_id":"items_api",\
                "params":{"since":"${param.since}","status":"active"},"records":"data.items","max_retries":2,\
                "retry_backoff":"exponential","retry_delay":1500,"timeout":45,"source_type":"rest","pool":"api",\
                "_schema_hint":"api_response"}""");
        assertThat(CoreSchema.pipelineErrors(p.toJson())).isEmpty();
    }

    @Test
    void writesAGetForADatasetWithOnlyTheUrlAsADatasetSource() {
        Pipeline p = Pipeline.builder("Items").build();

        DatasetRef items = p.sourceApi("Fetch", SourceApi.options().url("https://api.example.com/items"));

        JsonNode node = JSON.readTree(p.toJson()).path("nodes").get(0);
        assertThat(items.nodeId()).isEqualTo("fetch_1");
        assertThat(node.path("config").toString()).isEqualTo("""
                {"url":"https://api.example.com/items","method":"GET","response":"dataset",\
                "_schema_hint":"api_response"}""");
        assertThat(node.path("capabilities")).isEqualTo(JSON.readTree("""
                ["source","dataset-output"]"""));
    }

    @Test
    void writesTheHeadersAndTheQueryParametersInNameOrderWhateverTheMapsOrder() {
        Pipeline p = Pipeline.builder("Items").build();
        List<String> names = List.of("Accept", "Accept-Language", "Authorization", "Cache-Control", "Content-Type",
                "If-None-Match", "Origin", "Pragma", "Referer", "User-Agent", "X-Api-Key", "X-Request-Id");
        Map<String, String> values = names.stream().collect(Collectors.toMap(name -> name, name -> "v"));

        p.sourceApi("Fetch", SourceApi.options().url("/items").headers(values).params(values));

        assertThat(config(p).path("headers").propertyNames()).containsExactlyElementsOf(names);
        assertThat(config(p).path("params").propertyNames()).containsExactlyElementsOf(names);
    }

    @Test
    void writesABodyThatIsNotAnObject() {
        Pipeline p = Pipeline.builder("Items").build();

        p.sourceApi("Fetch", SourceApi.options().url("https://api.example.com/items").method("POST").body("raw"));

        assertThat(config(p).path("body").stringValue()).isEqualTo("raw");
    }

    @Test
    void refusesABodyWithAValueThatHasNoJsonForm() {
        Pipeline p = Pipeline.builder("Items").build();
        SourceApi options = SourceApi.options().url("/items").body(Map.of("since", LocalDate.EPOCH));

        assertThatThrownBy(() -> p.sourceApi("Fetch", options))
                .isInstanceOf(PipelineException.class)
                .hasMessageStartingWith("source_api Fetch: body.since is a java.time.LocalDate; a JSON value is");
    }

    @Test
    void refusesANullBodyHeaderOrParameter() {
        assertThatThrownBy(() -> SourceApi.options().body(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("body");
        assertThatThrownBy(() -> SourceApi.options().headers(Collections.singletonMap("Accept", null)))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SourceApi.options().params(Collections.singletonMap("status", null)))
                .isInstanceOf(NullPointerException.class);
    }

    private static JsonNode config(Pipeline p) {
        return JSON.readTree(p.toJson()).path("nodes").get(0).path("config");
    }
}
