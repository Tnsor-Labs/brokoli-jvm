package dev.brokoli.pipeline;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JoinTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void writesItsOptionsInThePythonSdksOrderAndDrawsTheLeftEdgeFirst() {
        Pipeline p = Pipeline.builder("Shop").build();
        DatasetRef users = p.sourceDb("Users", SourceDb.options().query("SELECT * FROM users"));
        DatasetRef orders = p.sourceDb("Orders", SourceDb.options().query("SELECT * FROM orders"));

        DatasetRef joined = p.join("Enrich", users, orders, Join.options()
                .timeout(Duration.ofSeconds(30))
                .retries(1)
                .collisionPolicy(CollisionPolicy.ERROR)
                .type(JoinType.FULL)
                .on("id", "user_id"));

        JsonNode ir = JSON.readTree(p.toJson());
        assertThat(joined.nodeId()).isEqualTo("enrich_1");
        assertThat(ir.path("nodes").get(2).path("config").toString()).isEqualTo("""
                {"join_type":"full","left_key":"id","right_key":"user_id","collision_policy":"error",\
                "max_retries":1,"retry_backoff":"exponential","timeout":30}""");
        assertThat(ir.path("nodes").get(2).path("capabilities")).isEqualTo(JSON.readTree("""
                ["compute"]"""));
        assertThat(ir.path("edges")).isEqualTo(JSON.readTree("""
                [{"from":"users_1","to":"enrich_1"},{"from":"orders_1","to":"enrich_1"}]"""));
        assertThat(CoreSchema.pipelineErrors(p.toJson())).isEmpty();
    }

    @Test
    void writesEachJoinTypeAsTheServerReadsIt() {
        Map<JoinType, String> written = Map.of(JoinType.INNER, "inner", JoinType.LEFT, "left", JoinType.RIGHT,
                "right", JoinType.FULL, "full");

        written.forEach((type, value) -> assertThat(config(Join.options().on("id").type(type))
                .path("join_type").stringValue()).isEqualTo(value));
    }

    @Test
    void readsAKeyColumnWhoseNameHoldsAnEqualsSignWhole() {
        assertThat(config(Join.options().on("a=b")).toString()).isEqualTo("""
                {"join_type":"inner","left_key":"a=b","right_key":"a=b","collision_policy":"prefix"}""");
    }

    @Test
    void writesTheRightAliasWithTheAliasPolicyAndDropsItForAnotherPolicy() {
        Join aliased = Join.options().on("id").rightAlias("right_row");

        assertThat(config(aliased).toString()).isEqualTo("""
                {"join_type":"inner","left_key":"id","right_key":"id","collision_policy":"alias",\
                "right_alias":"right_row"}""");
        assertThat(config(aliased.collisionPolicy(CollisionPolicy.PREFIX)).toString()).isEqualTo("""
                {"join_type":"inner","left_key":"id","right_key":"id","collision_policy":"prefix"}""");
    }

    @Test
    void refusesTheAliasPolicyWithoutAnAliasAndAnAliasWithAnotherPolicy() {
        assertThatThrownBy(() -> Join.options().collisionPolicy(CollisionPolicy.ALIAS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("the alias collision policy needs the alias of the right columns; give it with"
                        + " rightAlias(\"right_row\") instead of collisionPolicy(CollisionPolicy.ALIAS)");
        assertThatThrownBy(() -> new Join("id", "id", JoinType.INNER, CollisionPolicy.ALIAS, Optional.empty(),
                NodeOptions.defaults()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("the alias collision policy needs the alias of the right columns; give it with"
                        + " rightAlias(\"right_row\")");
        assertThatThrownBy(() -> new Join("id", "id", JoinType.INNER, CollisionPolicy.ERROR, Optional.of("r"),
                NodeOptions.defaults()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("the right alias r is only written with the alias collision policy, but the policy is"
                        + " error");
        assertThatThrownBy(() -> Join.options().rightAlias(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("the right alias \" \" is blank");
    }

    @Test
    void refusesAPoolAsThePythonSdksJoinHasNone() {
        assertThatThrownBy(() -> Join.options().node(NodeOptions.defaults().pool("compute")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("a join node runs in no pool, but its options have the pool compute");
    }

    private static JsonNode config(Join options) {
        Pipeline p = Pipeline.builder("Shop").build();
        DatasetRef left = p.sourceDb("Left", SourceDb.options().query("SELECT 1"));
        DatasetRef right = p.sourceDb("Right", SourceDb.options().query("SELECT 2"));
        p.join("Merge", left, right, options);
        return JSON.readTree(p.toJson()).path("nodes").get(2).path("config");
    }
}
