package dev.brokoli.task.processor;

import dev.brokoli.task.processor.TestCompiler.Compilation;
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
        assertThat(CoreSchema.parameterErrors(parameters)).isEmpty();
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
        assertThat(CoreSchema.parameterErrors(parameters)).isEmpty();
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
        assertThat(CoreSchema.parameterErrors(parameters)).isEmpty();
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
        assertThat(CoreSchema.interfaceErrors(taskInterface)).isEmpty();
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
