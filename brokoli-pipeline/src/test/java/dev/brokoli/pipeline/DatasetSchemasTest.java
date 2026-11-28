package dev.brokoli.pipeline;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatasetSchemasTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final DatasetSchema USERS = DatasetSchema.of(Column.of("id", DataType.int64()),
            Column.of("name", DataType.string())).closed();
    private static final DatasetSchema ORDERS = DatasetSchema.of(Column.of("id", DataType.int64()),
            Column.of("name", DataType.string().nullable()), Column.of("total", DataType.decimal(18, 2))).closed();

    @Test
    void writesTheDerivedSchemaAfterTheRetriesAndTimeoutAsThePythonSdkDoes() {
        Pipeline p = Pipeline.builder("Shop").build();
        DatasetRef users = p.sourceDb("Users", SourceDb.options().query("SELECT 1").schema(USERS));
        DatasetRef orders = p.sourceDb("Orders", SourceDb.options().query("SELECT 2").schema(ORDERS));

        p.join("Merge", users, orders, Join.options().on("id").timeout(Duration.ofSeconds(30)).retries(1));

        assertThat(config(p, "merge_1").toString()).isEqualTo("""
                {"join_type":"inner","left_key":"id","right_key":"id","collision_policy":"prefix","max_retries":1,\
                "retry_backoff":"exponential","timeout":30,"schema":{"contract":"brokoli.dataset-schema/v1",\
                "columns":[{"name":"id","type":{"kind":"int64"}},{"name":"name","type":{"kind":"string"}},\
                {"name":"right_name","type":{"kind":"string","nullable":true}},\
                {"name":"right_total","type":{"kind":"decimal","precision":18,"scale":2}}],\
                "additional_columns":"closed"}}""");
        assertThat(CoreSchema.datasetSchemaErrors(config(p, "merge_1").path("schema").toString())).isEmpty();
        assertThat(CoreSchema.pipelineErrors(p.toJson())).isEmpty();
    }

    @Test
    void prefixesEveryRightColumnOnceOneCollidesUntilTheNameIsFree() {
        DatasetSchema left = DatasetSchema.of(Column.of("id", DataType.int64()), Column.of("name", DataType.string()),
                Column.of("right_name", DataType.string()));

        assertThat(columns(left, ORDERS, Join.options().on("id")))
                .containsExactly("id", "name", "right_name", "right_right_name", "right_total");
    }

    @Test
    void keepsTheRightColumnNamesWithThePrefixPolicyWhenNoneCollides() {
        DatasetSchema right = DatasetSchema.of(Column.of("id", DataType.int64()), Column.of("email",
                DataType.string()));

        assertThat(columns(USERS, right, Join.options().on("id"))).containsExactly("id", "name", "email");
    }

    @Test
    void keepsTheRightColumnNamesWithTheErrorPolicyWhenNoneCollides() {
        DatasetSchema right = DatasetSchema.of(Column.of("id", DataType.int64()), Column.of("email",
                DataType.string()));

        assertThat(columns(USERS, right, Join.options().on("id").collisionPolicy(CollisionPolicy.ERROR)))
                .containsExactly("id", "name", "email");
    }

    @Test
    void writesEveryRightColumnUnderTheAliasWithTheAliasPolicy() {
        DatasetSchema right = DatasetSchema.of(Column.of("id", DataType.int64()), Column.of("email",
                DataType.string()));

        assertThat(columns(USERS, right, Join.options().on("id").rightAlias("buyer")))
                .containsExactly("id", "name", "buyer_email");
    }

    @Test
    void keepsTheRightKeyWhenTheKeysHaveDifferentNames() {
        DatasetSchema right = DatasetSchema.of(Column.of("user_id", DataType.int64()), Column.of("name",
                DataType.string()));

        assertThat(columns(USERS, right, Join.options().on("id", "user_id")))
                .containsExactly("id", "name", "right_user_id", "right_name");
        assertThat(columns(USERS, right, Join.options().on("id", "user_id").rightAlias("r")))
                .containsExactly("id", "name", "r_user_id", "r_name");
    }

    @Test
    void writesAClosedSchemaOnlyWhenBothInputsAreClosed() {
        DatasetSchema right = DatasetSchema.of(Column.of("id", DataType.int64()), Column.of("email",
                DataType.string()));

        assertThat(derived(USERS, right.closed(), Join.options().on("id")).path("additional_columns").stringValue())
                .isEqualTo("closed");
        assertThat(derived(USERS, right.open(), Join.options().on("id")).path("additional_columns").stringValue())
                .isEqualTo("unknown");
        assertThat(derived(USERS, right, Join.options().on("id")).path("additional_columns").stringValue())
                .isEqualTo("unknown");
    }

    @Test
    void writesNoSchemaWhenOnlyOneInputDeclaresOne() {
        Pipeline p = Pipeline.builder("Shop").build();
        DatasetRef users = p.sourceDb("Users", SourceDb.options().query("SELECT 1").schema(USERS));
        DatasetRef orders = p.sourceDb("Orders", SourceDb.options().query("SELECT 2"));

        p.join("Left", users, orders, Join.options().on("id"));
        p.join("Right", orders, users, Join.options().on("id"));

        assertThat(config(p, "left_1").has("schema")).isFalse();
        assertThat(config(p, "right_1").has("schema")).isFalse();
    }

    @Test
    void derivesTheSchemaOfAJoinOfAJoinFromTheSchemaTheFirstJoinWrote() {
        Pipeline p = Pipeline.builder("Shop").build();
        DatasetRef users = p.sourceDb("Users", SourceDb.options().query("SELECT 1").schema(USERS));
        DatasetRef orders = p.sourceApi("Orders", SourceApi.options().url("/orders").schema(ORDERS));
        DatasetRef files = p.sourceFile("Notes", SourceFile.options().path("/notes.csv").schema(DatasetSchema.of(
                Column.of("id", DataType.unknown()), Column.of("note", DataType.string())).closed()));
        DatasetRef merged = p.join("Merge", users, orders, Join.options().on("id"));

        p.join("Annotate", merged, files, Join.options().on("id"));

        assertThat(names(config(p, "annotate_1").path("schema")))
                .containsExactly("id", "name", "right_name", "right_total", "note");
    }

    @Test
    void acceptsKeysOfDifferentKindsWhenOneIsUnknown() {
        DatasetSchema right = DatasetSchema.of(Column.of("id", DataType.unknown()));

        assertThat(columns(USERS, right, Join.options().on("id"))).containsExactly("id", "name");
        assertThat(columns(right, USERS, Join.options().on("id"))).containsExactly("id", "name");
    }

    @Test
    void refusesAKeyThatIsNotAColumnOfItsSidesSchema() {
        assertThatThrownBy(() -> derived(USERS, ORDERS, Join.options().on("user_id", "id")))
                .isInstanceOf(PipelineException.class)
                .hasMessage("join Merge: the left key \"user_id\" is not a column of the schema the left input"
                        + " declares");
        assertThatThrownBy(() -> derived(USERS, ORDERS, Join.options().on("id", "user_id")))
                .isInstanceOf(PipelineException.class)
                .hasMessage("join Merge: the right key \"user_id\" is not a column of the schema the right input"
                        + " declares");
        assertThatThrownBy(() -> derived(USERS, ORDERS, Join.options()))
                .isInstanceOf(PipelineException.class)
                .hasMessage("join Merge: the left key \"\" is not a column of the schema the left input declares");
    }

    @Test
    void refusesKeysWhoseDeclaredKindsDifferWithNeitherUnknown() {
        DatasetSchema right = DatasetSchema.of(Column.of("user_id", DataType.string().nullable()));

        assertThatThrownBy(() -> derived(USERS, right, Join.options().on("id", "user_id")))
                .isInstanceOf(PipelineException.class)
                .hasMessage("join Merge: the keys \"id\" and \"user_id\" have incompatible declared types int64 and"
                        + " string");
    }

    @Test
    void refusesARightColumnTheLeftInputHasTooWithTheErrorPolicy() {
        DatasetSchema right = DatasetSchema.of(Column.of("id", DataType.int64()), Column.of("name",
                DataType.string()), Column.of("total", DataType.float64()));
        DatasetSchema left = DatasetSchema.of(Column.of("id", DataType.int64()), Column.of("name",
                DataType.string()), Column.of("total", DataType.float64()));

        assertThatThrownBy(() -> derived(left, right, Join.options().on("id").collisionPolicy(CollisionPolicy.ERROR)))
                .isInstanceOf(PipelineException.class)
                .hasMessage("join Merge: the error collision policy refuses the right columns \"name\", \"total\","
                        + " which the left input has too");
    }

    @Test
    void refusesAnAliasedRightColumnWhoseNameTheOutputAlreadyHas() {
        DatasetSchema left = DatasetSchema.of(Column.of("id", DataType.int64()), Column.of("r_name",
                DataType.string()));

        assertThatThrownBy(() -> derived(left, USERS, Join.options().on("id").rightAlias("r")))
                .isInstanceOf(PipelineException.class)
                .hasMessage("join Merge: the right column \"name\" is written as \"r_name\", which the output already"
                        + " has");
    }

    @Test
    void refusesAnInputOfAnotherPipelineBeforeLookingAtItsSchema() {
        Pipeline other = Pipeline.builder("Other").build();
        DatasetRef foreign = other.sourceDb("Users", SourceDb.options().query("SELECT 1").schema(USERS));
        Pipeline p = Pipeline.builder("Shop").build();
        p.sourceDb("Users", SourceDb.options().query("SELECT 1").schema(DatasetSchema.of(Column.of("email",
                DataType.string()))));
        DatasetRef orders = p.sourceDb("Orders", SourceDb.options().query("SELECT 2").schema(ORDERS));

        assertThatThrownBy(() -> p.join("Merge", foreign, orders, Join.options().on("id")))
                .isInstanceOf(PipelineException.class)
                .hasMessage("nodes from different pipelines cannot be connected: users_1 is not a node of pipeline"
                        + " Shop");
    }

    private static List<String> columns(DatasetSchema left, DatasetSchema right, Join options) {
        JsonNode schema = derived(left, right, options);
        assertThat(CoreSchema.datasetSchemaErrors(schema.toString())).isEmpty();
        return names(schema);
    }

    private static JsonNode derived(DatasetSchema left, DatasetSchema right, Join options) {
        Pipeline p = Pipeline.builder("Shop").build();
        DatasetRef leftNode = p.sourceDb("Left", SourceDb.options().query("SELECT 1").schema(left));
        DatasetRef rightNode = p.sourceDb("Right", SourceDb.options().query("SELECT 2").schema(right));
        p.join("Merge", leftNode, rightNode, options);
        return config(p, "merge_1").path("schema");
    }

    private static List<String> names(JsonNode schema) {
        return StreamSupport.stream(schema.path("columns").spliterator(), false)
                .map(column -> column.path("name").stringValue())
                .toList();
    }

    private static JsonNode config(Pipeline p, String nodeId) {
        return StreamSupport.stream(JSON.readTree(p.toJson()).path("nodes").spliterator(), false)
                .filter(node -> node.path("id").stringValue().equals(nodeId))
                .findFirst()
                .orElseThrow()
                .path("config");
    }
}
