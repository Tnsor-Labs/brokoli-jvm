package dev.brokoli.pipeline;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QualityCheckTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void writesItsRulesThenItsRetriesAndChecksItsInput() {
        Pipeline p = Pipeline.builder("QA").build();
        DatasetRef users = p.sourceDb("Users", SourceDb.options().query("SELECT * FROM users"));
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("column", "age");
        rule.put("rule", "min");
        rule.put("params", Map.of("min", 0));
        rule.put("on_failure", "warn");

        NodeRef gate = p.qualityCheck("Gate", users, QualityCheck.options()
                .timeout(Duration.ofSeconds(10))
                .retries(1)
                .rules(List.of(rule)));

        JsonNode ir = JSON.readTree(p.toJson());
        assertThat(gate.nodeId()).isEqualTo("gate_1");
        assertThat(ir.path("nodes").get(1).path("config").toString()).isEqualTo("""
                {"rules":[{"column":"age","rule":"min","params":{"min":0},"on_failure":"warn"}],"max_retries":1,\
                "retry_backoff":"exponential","timeout":10}""");
        assertThat(ir.path("nodes").get(1).path("capabilities")).isEqualTo(JSON.readTree("""
                ["compute"]"""));
        assertThat(ir.path("edges")).isEqualTo(JSON.readTree("""
                [{"from":"users_1","to":"gate_1"}]"""));
        assertThat(CoreSchema.pipelineErrors(p.toJson())).isEmpty();
    }

    @Test
    void writesNoRulesWhenItHasNone() {
        Pipeline p = Pipeline.builder("QA").build();

        p.qualityCheck("Gate", QualityCheck.options());

        assertThat(JSON.readTree(p.toJson()).path("nodes").get(0).path("config").toString()).isEqualTo("{}");
    }

    @Test
    void refusesARuleValueWithNoJsonFormNamingWhereItIs() {
        Pipeline p = Pipeline.builder("QA").build();
        QualityCheck options = QualityCheck.options().rules(List.of(
                Map.of("rule", "min", "column", "age", "params", Map.of("min", Double.NaN))));

        assertThatThrownBy(() -> p.qualityCheck("Gate", options))
                .isInstanceOf(PipelineException.class)
                .hasMessage("quality_check Gate: rules[0].params.min is NaN; a rule value is null, a String, a"
                        + " Boolean, an Integer, a Long, a BigInteger, a finite Double, a List, or a Map with String"
                        + " keys");
        assertThat(JSON.readTree(p.toJson()).path("nodes")).isEmpty();
    }

    @Test
    void refusesAPoolAsThePythonSdksQualityCheckHasNone() {
        assertThatThrownBy(() -> QualityCheck.options().node(NodeOptions.defaults().pool("compute")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("a quality_check node runs in no pool, but its options have the pool compute");
    }
}
