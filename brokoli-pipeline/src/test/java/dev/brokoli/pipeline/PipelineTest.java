package dev.brokoli.pipeline;

import dev.brokoli.bundle.internal.CanonicalJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigInteger;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PipelineTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void compilesTheKeysThePythonSdkWritesInItsOrder() {
        Pipeline p = Pipeline.builder("Export").build();

        p.sourceFile("Read", SourceFile.options().path("/in.csv")).then(p.sinkFile("Write",
                SinkFile.options().path("/out.parquet").format("parquet")));

        assertThat(p.toJson()).isEqualTo("""
                {"pipeline_id":"export","name":"Export","description":"","schedule":"","enabled":true,\
                "ir_version":"2.0","nodes":[{"id":"read_1","type":"source_file","name":"Read",\
                "config":{"path":"/in.csv","format":"csv"},"capabilities":["source","dataset-output"]},\
                {"id":"write_1","type":"sink_file","name":"Write","config":{"path":"/out.parquet",\
                "format":"parquet"},"capabilities":["sink"]}],"edges":[{"from":"read_1","to":"write_1"}],\
                "tags":[],"depends_on":[]}""");
    }

    @Test
    void compilesIrThatCoresSchemaAccepts() {
        Pipeline p = Pipeline.builder("Sales ETL").description("Daily").tags("finance").build();
        DatasetRef rows = p.sourceFile("Read", SourceFile.options().path("/data/sales.csv"));
        DatasetRef clean = p.transform("Clean", rows, Transform.options().rules(List.of(
                Map.of("type", "filter", "condition", "amount > 0"))));
        p.sinkFile("Write", clean, SinkFile.options().path("/data/out.csv"));

        assertThat(CoreSchema.pipelineErrors(p.toJson())).isEmpty();
    }

    @Test
    void numbersNodesWithTheSameIdBaseInTheOrderTheyAreAdded() {
        Pipeline p = Pipeline.builder("Ids").build();

        Stream<String> ids = Stream.of("Read", "read", "READ!", "Read 2", "Read")
                .map(name -> p.sourceFile(name, SourceFile.options().path("/in.csv")).nodeId());

        assertThat(ids).containsExactly("read_1", "read_2", "read_3", "read_2_1", "read_4");
    }

    @Test
    void makesThePipelineIdFromTheNameWhenNoneOrAnEmptyOneIsGiven() {
        assertThat(pipelineId(Pipeline.builder("Sales ETL").build())).isEqualTo("sales-etl");
        assertThat(pipelineId(Pipeline.builder("Sales ETL").pipelineId("").build())).isEqualTo("sales-etl");
        assertThat(pipelineId(Pipeline.builder("Sales ETL").pipelineId("sales").build())).isEqualTo("sales");
    }

    @Test
    void drawsAnEdgeOnceWhenItIsDrawnTwice() {
        Pipeline p = Pipeline.builder("Edges").build();
        DatasetRef read = p.sourceFile("Read", SourceFile.options().path("/in.csv"));
        NodeRef write = p.sinkFile("Write", read, SinkFile.options().path("/out.csv"));

        read.then(write);

        assertThat(JSON.readTree(p.toJson()).path("edges")).isEqualTo(JSON.readTree("""
                [{"from":"read_1","to":"write_1"}]"""));
    }

    @Test
    void refusesAnEdgeToANodeOfAnotherPipeline() {
        Pipeline p = Pipeline.builder("Mine").build();
        DatasetRef read = p.sourceFile("Read", SourceFile.options().path("/in.csv"));
        NodeRef foreign = Pipeline.builder("Other").build().sinkFile("Write", SinkFile.options().path("/out.csv"));

        assertThatThrownBy(() -> read.then(foreign))
                .isInstanceOf(PipelineException.class)
                .hasMessage("nodes from different pipelines cannot be connected: write_1 is not a node of pipeline"
                        + " Mine");
    }

    @Test
    void refusesAnInputFromAnotherPipeline() {
        DatasetRef foreign = Pipeline.builder("Other").build().sourceFile("Read",
                SourceFile.options().path("/in.csv"));
        Pipeline p = Pipeline.builder("Mine").build();

        assertThatThrownBy(() -> p.transform("Clean", foreign, Transform.options()))
                .isInstanceOf(PipelineException.class)
                .hasMessage("nodes from different pipelines cannot be connected: read_1 is not a node of pipeline"
                        + " Mine");
    }

    @Test
    void sortsTagsByTheirJsonTextAsThePythonSdkDoes() {
        Pipeline p = Pipeline.builder("Tags").tags("daily", "daily x", "a#", "a\"x").build();

        assertThat(JSON.readTree(p.render()).path("tags")).isEqualTo(JSON.readTree("""
                ["a#", "a\\"x", "daily x", "daily"]"""));
    }

    @Test
    void leavesTheRulesOutOfATransformWithNone() {
        Pipeline p = Pipeline.builder("Rules").build();

        p.transform("Clean", Transform.options());

        assertThat(config(p)).isEqualTo(JSON.readTree("{}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"rename", "rename_columns"})
    void writesTheFromAndToOfARenameAsAMapping(String type) {
        Pipeline p = Pipeline.builder("Rules").build();

        p.transform("Clean", Transform.options().rules(List.of(Map.of("type", type, "from", "amt", "to", "amount"))));

        assertThat(config(p)).isEqualTo(JSON.readTree("""
                {"rules":[{"type":"%s","mapping":{"amt":"amount"}}]}""".formatted(type)));
    }

    @Test
    void refusesARenameWithOnlyOneOfFromAndTo() {
        Pipeline p = Pipeline.builder("Rules").build();
        Transform rules = Transform.options().rules(List.of(Map.of("type", "rename", "from", "amt")));

        assertThatThrownBy(() -> p.transform("Clean", rules))
                .isInstanceOf(PipelineException.class)
                .hasMessage("transform Clean: rules[0] (rename) has the \"from\" and \"to\" shorthand without both"
                        + " keys, or with a \"mapping\" too; give both, or only a \"mapping\" of old to new column"
                        + " names");
    }

    @Test
    void refusesARenameWithAnEmptyMapping() {
        Pipeline p = Pipeline.builder("Rules").build();
        Transform rules = Transform.options().rules(List.of(
                Map.of("type", "filter", "condition", "x > 0"),
                Map.of("type", "rename", "mapping", Map.of())));

        assertThatThrownBy(() -> p.transform("Clean", rules))
                .isInstanceOf(PipelineException.class)
                .hasMessage("transform Clean: rules[1] (rename) needs a non-empty \"mapping\" of old to new column"
                        + " names, such as Map.of(\"type\", \"rename\", \"mapping\","
                        + " Map.of(\"old_col\", \"new_col\"))");
    }

    @Test
    void refusesARuleValueWithNoJsonFormNamingWhereItIs() {
        Pipeline p = Pipeline.builder("Rules").build();
        Transform rules = Transform.options().rules(List.of(
                Map.of("type", "filter", "since", List.of(Instant.EPOCH))));

        assertThatThrownBy(() -> p.transform("Clean", rules))
                .isInstanceOf(PipelineException.class)
                .hasMessage("transform Clean: rules[0].since[0] is a java.time.Instant; a rule value is null, a"
                        + " String, a Boolean, an Integer, a Long, a BigInteger, a finite Double, a List, or a Map"
                        + " with String keys");
    }

    @Test
    void refusesARuleNumberThatIsNotFinite() {
        Pipeline p = Pipeline.builder("Rules").build();
        Transform rules = Transform.options().rules(List.of(Map.of("type", "sample", "fraction", Double.NaN)));

        assertThatThrownBy(() -> p.transform("Clean", rules))
                .isInstanceOf(PipelineException.class)
                .hasMessageStartingWith("transform Clean: rules[0].fraction is NaN; a rule value is");
    }

    @Test
    void keepsRuleValuesOfEveryAllowedKind() {
        Pipeline p = Pipeline.builder("Rules").build();
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("type", "custom");
        rule.put("none", null);
        rule.put("flag", true);
        rule.put("count", 3);
        rule.put("big", new BigInteger("123456789012345678901234567890"));
        rule.put("ratio", 0.25);
        rule.put("columns", List.of("a", "b"));

        p.transform("Clean", Transform.options().rules(List.of(rule)));

        assertThat(CanonicalJson.render(config(p))).isEqualTo("""
                {
                  "rules": [
                    {
                      "big": 123456789012345678901234567890,
                      "columns": [
                        "a",
                        "b"
                      ],
                      "count": 3,
                      "flag": true,
                      "none": null,
                      "ratio": 0.25,
                      "type": "custom"
                    }
                  ]
                }
                """);
    }

    private static JsonNode config(Pipeline p) {
        return JSON.readTree(p.toJson()).path("nodes").get(0).path("config");
    }

    private static String pipelineId(Pipeline p) {
        return JSON.readTree(p.toJson()).path("pipeline_id").stringValue();
    }
}
