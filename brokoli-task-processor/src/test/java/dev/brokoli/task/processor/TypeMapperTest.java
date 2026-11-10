package dev.brokoli.task.processor;

import dev.brokoli.task.processor.TestCompiler.Compilation;
import dev.brokoli.task.processor.TestCompiler.Source;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static dev.brokoli.task.processor.Fixtures.TASKS;
import static dev.brokoli.task.processor.Fixtures.compileTasks;
import static org.assertj.core.api.Assertions.assertThat;

class TypeMapperTest {

    @TempDir
    Path output;

    @Test
    void mapsEveryParameterTypePythonMaps() {
        JsonNode parameters = parameters("""
                @Task
                public static Rows run(Rows input, long count, int small, Long boxedCount, Integer boxedSmall,
                        double ratio, float single, Double boxedRatio, Float boxedSingle, boolean flag,
                        Boolean boxedFlag, String region, BigDecimal price, LocalDate day, Instant at,
                        Duration window, byte[] payload) {
                    return input;
                }
                """);

        assertThat(kinds(parameters)).containsExactly("int64", "int64", "int64", "int64", "float64", "float64",
                "float64", "float64", "boolean", "boolean", "string", "decimal", "date", "timestamp", "duration",
                "bytes");
        assertThat(parameters.get("count").get("required").asBoolean()).isTrue();
    }

    @Test
    void mapsListsMapsEnumsAndRecordsWithTheirInnerTypes() {
        JsonNode parameters = parameters("""
                public enum Tier { gold, silver }

                public record Window(Instant start, Duration length) {
                }

                @Task
                public static Rows run(Rows input, List<String> tags, Map<String, Double> weights, Tier tier,
                        Window window, List<List<Long>> grid) {
                    return input;
                }
                """);

        assertThat(parameters.get("tags").get("type")).isEqualTo(json("""
                {"kind": "array", "items": {"kind": "string"}}"""));
        assertThat(parameters.get("weights").get("type")).isEqualTo(json("""
                {"kind": "map", "keys": "string", "values": {"kind": "float64"}}"""));
        assertThat(parameters.get("tier").get("type")).isEqualTo(json("""
                {"kind": "enum", "values": ["gold", "silver"]}"""));
        assertThat(parameters.get("window").get("type")).isEqualTo(json("""
                {"kind": "record", "fields": [
                    {"name": "start", "type": {"kind": "timestamp"}, "required": true},
                    {"name": "length", "type": {"kind": "duration"}, "required": true}
                ], "additional_fields": false}"""));
        assertThat(parameters.get("grid").get("type")).isEqualTo(json("""
                {"kind": "array", "items": {"kind": "array", "items": {"kind": "int64"}}}"""));
    }

    @Test
    void anyAnnotationNamedNullableMakesAValueNullableAndNothingElseDoes() {
        JsonNode parameters = parameters("""
                public record Order(String id, @Nullable String coupon, @fixtures.other.Nullable Long discount) {
                }

                @Task
                public static Rows run(Rows input, @Nullable String typeUse, @fixtures.other.Nullable String declared,
                        String plain, Double boxed, List<@Nullable String> items, Order order) {
                    return input;
                }
                """);

        assertThat(parameters.get("typeUse").get("type")).isEqualTo(json("""
                {"kind": "string", "nullable": true}"""));
        assertThat(parameters.get("declared").get("type")).isEqualTo(json("""
                {"kind": "string", "nullable": true}"""));
        assertThat(parameters.get("plain").get("type")).isEqualTo(json("""
                {"kind": "string"}"""));
        assertThat(parameters.get("boxed").get("type")).isEqualTo(json("""
                {"kind": "float64"}"""));
        assertThat(parameters.get("items").get("type")).isEqualTo(json("""
                {"kind": "array", "items": {"kind": "string", "nullable": true}}"""));
        assertThat(parameters.get("order").get("type").get("fields")).isEqualTo(json("""
                [
                    {"name": "id", "type": {"kind": "string"}, "required": true},
                    {"name": "coupon", "type": {"kind": "string", "nullable": true}, "required": true},
                    {"name": "discount", "type": {"kind": "int64", "nullable": true}, "required": true}
                ]"""));
    }

    @Test
    void aRecordInputAndARowsResultGiveARecordPortAndAnUnknownOne() {
        Compilation compilation = compileTasks(output, """
                public record Order(String id, double amount) {
                }

                @Task
                public static Rows run(List<Order> input) {
                    return Rows.empty();
                }
                """);

        assertThat(compilation.errors()).isEmpty();
        JsonNode taskInterface = compilation.task(TASKS, "run").get("interface");
        assertThat(taskInterface.get("outputs")).isEqualTo(json("""
                {"result": {"value": {"kind": "dataset", "row": {"kind": "unknown"}}}}"""));
        assertThat(taskInterface.get("inputs").get("input").get("value").get("row").get("kind").asString())
                .isEqualTo("record");
    }

    @Test
    void writesTheIntAndFloatRangesWhereverAnIntOrFloatAppears() {
        Compilation compilation = compileTasks(output, """
                public record Sample(int count, Float weight) {
                }

                @Task
                public static Rows run(List<Sample> input, int small, Integer boxedSmall, @Nullable Integer maybe,
                        float single, Float boxedSingle, List<Integer> counts, Map<String, Float> weights,
                        long count, double ratio) {
                    return Rows.empty();
                }
                """);

        assertThat(compilation.errors()).isEmpty();
        JsonNode task = compilation.task(TASKS, "run");
        JsonNode parameters = task.get("parameters");
        String intRange = "\"kind\": \"int64\", \"minimum\": \"-2147483648\", \"maximum\": \"2147483647\"";
        String floatRange = "\"kind\": \"float64\", \"minimum\": -3.4028234663852886E38, "
                + "\"maximum\": 3.4028234663852886E38";
        assertThat(parameters.get("small").get("type")).isEqualTo(json("{" + intRange + "}"));
        assertThat(parameters.get("boxedSmall").get("type")).isEqualTo(json("{" + intRange + "}"));
        assertThat(parameters.get("maybe").get("type")).isEqualTo(json("{" + intRange + ", \"nullable\": true}"));
        assertThat(parameters.get("single").get("type")).isEqualTo(json("{" + floatRange + "}"));
        assertThat(parameters.get("boxedSingle").get("type")).isEqualTo(json("{" + floatRange + "}"));
        assertThat(parameters.get("counts").get("type").get("items")).isEqualTo(json("{" + intRange + "}"));
        assertThat(parameters.get("weights").get("type").get("values")).isEqualTo(json("{" + floatRange + "}"));
        assertThat(parameters.get("count").get("type")).isEqualTo(json("""
                {"kind": "int64"}"""));
        assertThat(parameters.get("ratio").get("type")).isEqualTo(json("""
                {"kind": "float64"}"""));
        JsonNode fields = task.get("interface").get("inputs").get("input").get("value").get("row").get("fields");
        assertThat(fields.get(0).get("type")).isEqualTo(json("{" + intRange + "}"));
        assertThat(fields.get(1).get("type")).isEqualTo(json("{" + floatRange + "}"));
    }

    @Test
    void refusesAnEnumWithNoConstants() {
        Compilation compilation = compileTasks(output, """
                public enum Nothing { }

                @Task
                public static Rows run(Rows input, Nothing nothing) {
                    return input;
                }
                """);

        assertThat(compilation.errors()).containsExactly(
                "@Task method Tasks.run parameter nothing is enum fixtures.Tasks.Nothing, which has no constants; "
                        + "a Brokoli enum needs at least one value");
    }

    @Test
    void acceptsAPackagePrivateRecordAndRefusesOneTheGeneratedClassCannotReach() {
        Compilation reachable = compileTasks(output.resolve("reachable"), """
                record Order(String id) {
                }

                @Task
                public static Rows run(List<Order> input) {
                    return Rows.empty();
                }
                """);

        assertThat(reachable.errors()).isEmpty();

        Compilation hidden = compileTasks(output.resolve("hidden"), """
                private record Hidden(String id) {
                }

                private static final class Box {

                    public enum Tier { gold }
                }

                @Task
                public static Rows privateRecord(List<Hidden> input) {
                    return Rows.empty();
                }

                @Task
                public static Rows insidePrivateClass(Rows input, Box.Tier tier) {
                    return input;
                }
                """);

        assertThat(hidden.errors()).containsExactly(
                "@Task method Tasks.privateRecord input is fixtures.Tasks.Hidden, which the generated task class "
                        + "in package fixtures cannot reach: fixtures.Tasks.Hidden is private; make it "
                        + "package-private or public",
                "@Task method Tasks.insidePrivateClass parameter tier is fixtures.Tasks.Box.Tier, which the "
                        + "generated task class in package fixtures cannot reach: fixtures.Tasks.Box is private; "
                        + "make it package-private or public");
    }

    @Test
    void refusesAProtectedRecordFromAnotherPackage() {
        Compilation compilation = Fixtures.compileUnit(output, TASKS, """
                public final class Tasks extends fixtures.base.Base {

                    @Task
                    public static Rows run(Rows input, Point point) {
                        return input;
                    }
                }
                """, Source.of("fixtures.base.Base", """
                package fixtures.base;

                public class Base {

                    protected record Point(long x, long y) {
                    }
                }
                """));

        assertThat(compilation.errors()).containsExactly(
                "@Task method Tasks.run parameter point is fixtures.base.Base.Point, which the generated task "
                        + "class in package fixtures cannot reach: fixtures.base.Base.Point is not public and is "
                        + "in another package; make it public");
    }

    @Test
    void refusesATypeWithNoBrokoliTypeNamingTheParameterAndTheSupportedTypes() {
        Compilation compilation = compileTasks(output, """
                @Task
                public static Rows run(Rows input, LocalTime at) {
                    return input;
                }
                """);

        assertThat(compilation.errors()).containsExactly(
                "@Task method Tasks.run parameter at is a java.time.LocalTime, which has no Brokoli type; use "
                        + "long, int, double, float, boolean (or their boxed types), String, BigDecimal, LocalDate, "
                        + "Instant, Duration, byte[], an enum, a record, List<T> or Map<String, T>");
    }

    @Test
    void refusesANestedTypeWithNoBrokoliTypeNamingItsPath() {
        Compilation compilation = compileTasks(output, """
                public record Shift(String name, List<LocalTime> starts) {
                }

                @Task
                public static Rows run(Rows input, Shift shift) {
                    return input;
                }
                """);

        assertThat(compilation.errors()).singleElement().asString()
                .startsWith("@Task method Tasks.run parameter shift.starts[] is a java.time.LocalTime");
    }

    @Test
    void refusesANullablePrimitive() {
        Compilation compilation = compileTasks(output, """
                @Task
                public static Rows run(Rows input, @fixtures.other.Nullable double ratio) {
                    return input;
                }
                """);

        assertThat(compilation.errors()).containsExactly(
                "@Task method Tasks.run parameter ratio is a double, which cannot be null; use its boxed type "
                        + "to make it nullable");
    }

    @Test
    void refusesMapsWithoutTextKeysRawTypesAndWildcards() {
        Compilation compilation = compileTasks(output, """
                @Task
                public static Rows keys(Rows input, Map<Long, String> byId) {
                    return input;
                }

                @Task
                public static Rows raw(Rows input, List values) {
                    return input;
                }

                @Task
                public static Rows wildcard(Rows input, List<? extends Number> values) {
                    return input;
                }
                """);

        assertThat(compilation.errors()).containsExactly(
                "@Task method Tasks.keys parameter byId is a Map with java.lang.Long keys; a Brokoli map has "
                        + "String keys",
                "@Task method Tasks.raw parameter values is a raw java.util.List; give it its type arguments",
                "@Task method Tasks.wildcard parameter values is a java.util.List<? extends java.lang.Number>; "
                        + "a Brokoli type needs concrete type arguments, not wildcards or type variables");
    }

    @Test
    void refusesARecordThatContainsItself() {
        Compilation compilation = compileTasks(output, """
                public record Node(String name, List<Node> children) {
                }

                @Task
                public static Rows run(Rows input, Node tree) {
                    return input;
                }
                """);

        assertThat(compilation.errors()).containsExactly(
                "@Task method Tasks.run parameter tree.children[] makes record Node contain itself; a row "
                        + "schema cannot be recursive");
    }

    private JsonNode parameters(String body) {
        Compilation compilation = compileTasks(output, body);
        assertThat(compilation.errors()).isEmpty();
        return compilation.task(TASKS, "run").get("parameters");
    }

    private static List<String> kinds(JsonNode parameters) {
        List<String> kinds = new ArrayList<>();
        for (JsonNode declaration : parameters) {
            kinds.add(declaration.get("type").get("kind").asString());
        }
        return kinds;
    }

    private static JsonNode json(String text) {
        return TestCompiler.JSON.readTree(text);
    }
}
