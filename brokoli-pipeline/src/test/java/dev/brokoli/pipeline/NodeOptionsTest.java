package dev.brokoli.pipeline;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NodeOptionsTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SIXTY_FOUR = "k" + "0".repeat(63);

    @Test
    void writesTheRetriesAndTimeoutInMillisecondsAndSecondsAfterTheNodesOwnOptionsAndThePoolLast() {
        Pipeline p = Pipeline.builder("Run").build();

        p.sourceFile("Read", SourceFile.options()
                .path("/in.csv")
                .pool("files")
                .timeout(Duration.ofMinutes(1))
                .retryDelay(Duration.ofMillis(1500))
                .retries(3, Backoff.FIXED)
                .connId("lake"));

        assertThat(config(p, 0).toString()).isEqualTo("""
                {"path":"/in.csv","format":"csv","conn_id":"lake","max_retries":3,"retry_backoff":"fixed",\
                "retry_delay":1500,"timeout":60,"pool":"files"}""");
    }

    @Test
    void spacesRetriesExponentiallyUnlessToldOtherwiseAsThePythonSdkDoes() {
        Pipeline p = Pipeline.builder("Run").build();

        p.sinkFile("Write", SinkFile.options().path("/out.csv").retries(0));

        assertThat(config(p, 0).toString()).isEqualTo("""
                {"path":"/out.csv","format":"csv","max_retries":0,"retry_backoff":"exponential"}""");
    }

    @Test
    void writesTheCompressionAndConnectionOfASinkFileBeforeItsRetries() {
        Pipeline p = Pipeline.builder("Run").build();

        p.sinkFile("Write", SinkFile.options()
                .path("/out.parquet")
                .format("parquet")
                .retries(1, Backoff.LINEAR)
                .connId("lake")
                .compress("snappy"));

        assertThat(config(p, 0).toString()).isEqualTo("""
                {"path":"/out.parquet","format":"parquet","compress":"snappy","conn_id":"lake","max_retries":1,\
                "retry_backoff":"linear"}""");
    }

    @Test
    void writesTheRetriesAndTimeoutOfATransformAfterItsRules() {
        Pipeline p = Pipeline.builder("Run").build();

        p.transform("Clean", Transform.options()
                .timeout(Duration.ofSeconds(30))
                .retries(1)
                .rules(List.of(Map.of("type", "deduplicate"))));

        assertThat(config(p, 0).toString()).isEqualTo("""
                {"rules":[{"type":"deduplicate"}],"max_retries":1,"retry_backoff":"exponential","timeout":30}""");
    }

    @Test
    void refusesATransformWithAPoolAsThePythonSdkHasNone() {
        NodeOptions pooled = NodeOptions.defaults().pool("compute");

        assertThatThrownBy(() -> new Transform(List.of(), pooled))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("a transform node runs in no pool, but its options have the pool compute");
    }

    @Test
    void replacesTheNodeOptionsOfEachNodeTypeAndKeepsItsOwnOptions() {
        NodeOptions node = NodeOptions.defaults()
                .nodeKey("step")
                .retries(2, Backoff.LINEAR)
                .timeout(Duration.ofSeconds(5));
        NodeOptions pooled = node.pool("files");
        List<Map<String, Object>> rules = List.of(Map.of("type", "deduplicate"));

        assertThat(SourceFile.options().path("/in.csv").format("json").connId("lake").node(pooled))
                .isEqualTo(new SourceFile("/in.csv", "json", Optional.of("lake"), pooled));
        assertThat(SinkFile.options().path("/out.csv").compress("gzip").connId("lake").node(pooled))
                .isEqualTo(new SinkFile("/out.csv", "csv", Optional.of("gzip"), Optional.of("lake"), pooled));
        assertThat(Bash.options().command("./run.sh").workingDir("/jobs").env(Map.of("A", "1")).maxMemoryMb(64)
                .maxCpuTime(Duration.ofSeconds(9)).node(pooled))
                .isEqualTo(new Bash("./run.sh", Optional.of("/jobs"), Map.of("A", "1"), OptionalInt.of(64),
                        Optional.of(Duration.ofSeconds(9)), pooled));
        assertThat(Transform.options().rules(rules).node(node)).isEqualTo(new Transform(rules, node));
    }

    @Test
    void givesAPoolToEveryNodeTypeButATransform() {
        assertThat(List.of(SourceFile.class, SinkFile.class, Bash.class))
                .allSatisfy(type -> assertThat(WithPool.class.isAssignableFrom(type)).isTrue());
        assertThat(WithPool.class.isAssignableFrom(Transform.class)).isFalse();
        assertThatThrownBy(() -> Transform.options().node(NodeOptions.defaults().pool("compute")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("a transform node runs in no pool, but its options have the pool compute");
    }

    @Test
    void usesTheNodeKeyAsTheNodesIdAndSkipsItWhenMakingAnIdFromAName() {
        Pipeline p = Pipeline.builder("Run").build();

        NodeRef keyed = p.sourceFile("Read", SourceFile.options().path("/in.csv").nodeKey("read_1"));
        NodeRef named = p.sourceFile("Read", SourceFile.options().path("/in.csv"));

        assertThat(List.of(keyed.nodeId(), named.nodeId())).containsExactly("read_1", "read_2");
    }

    @Test
    void refusesANodeKeyThatIsAlreadyTheIdOfANode() {
        Pipeline p = Pipeline.builder("Run").build();
        p.sourceFile("Read", SourceFile.options().path("/in.csv"));
        SinkFile keyed = SinkFile.options().path("/out.csv").nodeKey("read_1");

        assertThatThrownBy(() -> p.sinkFile("Write", keyed))
                .isInstanceOf(PipelineException.class)
                .hasMessage("pipeline Run already has a node with the id read_1; give the node another key");
        assertThat(JSON.readTree(p.toJson()).path("nodes")).hasSize(1);
    }

    @Test
    void takesANodeKeyOfUpToSixtyFourCharactersStartingWithALetter() {
        assertThat(NodeOptions.defaults().nodeKey(SIXTY_FOUR).nodeKey()).contains(SIXTY_FOUR);
        assertThat(NodeOptions.defaults().nodeKey("a-b_9").nodeKey()).contains("a-b_9");
    }

    @Test
    void refusesANodeKeyThePythonSdkRefuses() {
        for (String key : List.of("Read", "9read", "", SIXTY_FOUR + "0", "read.csv")) {
            assertThatThrownBy(() -> NodeOptions.defaults().nodeKey(key))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("node key " + key + " does not match [a-z][a-z0-9_-]{0,63}");
        }
    }

    @Test
    void takesWholeMillisecondsForTheRetryDelayAndWholeSecondsForTheTimeout() {
        NodeOptions options = NodeOptions.defaults().retryDelay(Duration.ofMillis(1)).timeout(Duration.ZERO);

        assertThat(options.retryDelay()).contains(Duration.ofMillis(1));
        assertThat(options.timeout()).contains(Duration.ZERO);
    }

    @Test
    void refusesARetryDelayFinerThanAMillisecond() {
        assertThatThrownBy(() -> NodeOptions.defaults().retryDelay(Duration.ofNanos(1_500_000)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("retry delay PT0.0015S is not a whole number of milliseconds, which the server takes");
    }

    @Test
    void refusesATimeoutFinerThanASecond() {
        assertThatThrownBy(() -> NodeOptions.defaults().timeout(Duration.ofMillis(1500)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("timeout PT1.5S is not a whole number of seconds, which the server takes");
    }

    @Test
    void refusesNegativeRetriesAndDurations() {
        assertThatThrownBy(() -> NodeOptions.defaults().retries(-1, Backoff.FIXED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("retries -1 is negative");
        assertThatThrownBy(() -> NodeOptions.defaults().timeout(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("timeout PT-1S is negative");
        assertThatThrownBy(() -> NodeOptions.defaults().retryDelay(Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("retry delay PT-0.001S is negative");
    }

    private static JsonNode config(Pipeline p, int node) {
        return JSON.readTree(p.toJson()).path("nodes").get(node).path("config");
    }
}
