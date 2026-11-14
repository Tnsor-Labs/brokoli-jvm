package dev.brokoli.pipeline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void dropsServerFieldsAndFillsTheDefaultsOfAbsentOrNullListsAndMaps() {
        JsonNode normalized = Normalization.normalize(ir("""
                {"id":"p-1","source":"api","workspace_id":"w","org_id":"o","created_at":"t","updated_at":"t",\
                "name":"P","nodes":null,"tags":["b","a"],"params":null}"""));

        assertThat(normalized).isEqualTo(JSON.readTree("""
                {"name":"P","nodes":[],"edges":[],"tags":["a","b"],"depends_on":[],"dependency_rules":[],\
                "params":{},"hooks":{}}"""));
    }

    @Test
    void dropsAnEmptyWebhookUrlAndBlanksAWebhookToken() {
        JsonNode normalized = Normalization.normalize(ir("""
                {"webhook_url":"","webhook_token":"secret"}"""));

        assertThat(normalized.has("webhook_url")).isFalse();
        assertThat(normalized.path("webhook_token").stringValue()).isEmpty();
    }

    @Test
    void keepsAWebhookUrlThatIsSet() {
        JsonNode normalized = Normalization.normalize(ir("""
                {"webhook_url":"https://example.com/hook"}"""));

        assertThat(normalized.path("webhook_url").stringValue()).isEqualTo("https://example.com/hook");
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"\"", "\"UTC\""})
    void dropsTheDefaultScheduleTimezone(String timezone) {
        JsonNode normalized = Normalization.normalize(ir("{\"schedule_timezone\":" + timezone + "}"));

        assertThat(normalized.has("schedule_timezone")).isFalse();
    }

    @Test
    void keepsAScheduleTimezoneThatIsNotUtc() {
        JsonNode normalized = Normalization.normalize(ir("""
                {"schedule_timezone":"Europe/Lisbon"}"""));

        assertThat(normalized.path("schedule_timezone").stringValue()).isEqualTo("Europe/Lisbon");
    }

    @Test
    void dropsNodePositionsFillsMissingCapabilitiesAndSortsCapabilitiesAndNodes() {
        JsonNode normalized = Normalization.normalize(ir("""
                {"nodes":[\
                {"id":"write_1","type":"sink_file","position":{"x":1,"y":2},"capabilities":[]},\
                {"id":"read_1","type":"source_file"},\
                {"id":"clean_1","type":"transform","capabilities":["sink","compute"]}]}"""));

        assertThat(normalized.path("nodes")).isEqualTo(JSON.readTree("""
                [{"id":"clean_1","type":"transform","capabilities":["compute","sink"]},\
                {"id":"read_1","type":"source_file","capabilities":["dataset-output","source"]},\
                {"id":"write_1","type":"sink_file","capabilities":["sink"]}]"""));
    }

    @Test
    void sortsTagsAboveU0FfffByCodePointNotByUtf16Units() {
        JsonNode normalized = Normalization.normalize(ir("""
                {"tags":["😀","～"]}"""));

        assertThat(normalized.path("tags")).isEqualTo(JSON.readTree("""
                ["～","😀"]"""));
    }

    @Test
    void leavesEdgesInTheOrderTheyWereDrawn() {
        JsonNode normalized = Normalization.normalize(ir("""
                {"edges":[{"from":"b","to":"c"},{"from":"a","to":"b"}]}"""));

        assertThat(normalized.path("edges")).isEqualTo(JSON.readTree("""
                [{"from":"b","to":"c"},{"from":"a","to":"b"}]"""));
    }

    private static ObjectNode ir(String json) {
        return (ObjectNode) JSON.readTree(json);
    }
}
