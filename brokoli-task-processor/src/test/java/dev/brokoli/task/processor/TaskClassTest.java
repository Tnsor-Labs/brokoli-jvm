package dev.brokoli.task.processor;

import dev.brokoli.task.internal.TaskValueException;
import dev.brokoli.task.processor.TestCompiler.Compilation;
import dev.brokoli.task.processor.TestCompiler.Source;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.util.Map;

import static dev.brokoli.task.processor.Fixtures.TASKS;
import static dev.brokoli.task.processor.Fixtures.compileTasks;
import static dev.brokoli.task.processor.Fixtures.compileUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskClassTest {

    private static final JsonMapper HARNESS_JSON = JsonMapper.builder()
            .enable(DeserializationFeature.USE_LONG_FOR_INTS)
            .build();
    private static final TypeReference<Map<String, Object>> KWARGS = new TypeReference<>() {
    };

    private static final String RECORDS = """
            public enum Tier { gold, silver }

            public record Window(Instant start, Duration length) {
            }

            public record Order(long id, int units, double weight, float ratio, boolean paid, String region,
                    BigDecimal price, LocalDate day, Instant at, Duration hold, byte[] payload, Tier tier,
                    List<String> tags, Map<String, Long> counts, Window window, @Nullable String coupon) {
            }
            """;

    private static final String ORDER = """
            {
                "id": 9007199254740993,
                "units": 3,
                "weight": 2.5,
                "ratio": 0.5,
                "paid": true,
                "region": "eu-west",
                "price": {"$bptd": "decimal", "value": "12.50"},
                "day": "2026-01-31",
                "at": {"$bptd": "timestamp", "value": "2026-01-31T10:00:00.500Z"},
                "hold": {"$bptd": "duration", "seconds": "-1", "nanos": -500000000},
                "payload": {"$bptd": "bytes", "value": "SGVsbG8="},
                "tier": "silver",
                "tags": ["a", "b"],
                "counts": {"x": 1, "y": 2},
                "window": {
                    "start": {"$bptd": "timestamp", "value": "2026-01-31T00:00:00Z"},
                    "length": {"$bptd": "duration", "seconds": "3600", "nanos": 0}
                },
                "coupon": null
            }""";

    @TempDir
    Path output;

    @Test
    void theTaskFileNamesTheGeneratedEntryMethod() {
        Compilation compilation = compileTasks(output, """
                @Task
                public static Rows run(Rows input) {
                    return input;
                }
                """);

        assertThat(compilation.errors()).isEmpty();
        assertThat(compilation.task(TASKS, "run").get("entrypoint").asString())
                .isEqualTo("fixtures.Tasks_BrokoliTasks#run");
    }

    @Test
    void aRowsTaskGetsItsInputRowsAndItsParameterAndIgnoresOtherRunParameters() {
        Compilation compilation = compileTasks(output, """
                @Task
                public static Rows tag(Rows input, String region) {
                    return input.map(row -> row.with("region", region));
                }
                """);

        Object result = compilation.call(entrypoint(compilation, "tag"), kwargs("""
                {"input": [{"id": 1}, {"id": 2}], "region": "eu", "threshold": 0.5}"""));

        assertThat(json(result)).isEqualTo(json("""
                [{"id": 1, "region": "eu"}, {"id": 2, "region": "eu"}]"""));
    }

    @Test
    void aTaskWithNoStagedInputGetsNoRows() {
        Compilation compilation = compileTasks(output, RECORDS + """
                @Task
                public static Rows count(List<Order> input) {
                    return Rows.of(dev.brokoli.task.Row.of(Map.of("orders", input.size())));
                }
                """);

        assertThat(json(compilation.call(entrypoint(compilation, "count"), Map.of()))).isEqualTo(json("""
                [{"orders": 0}]"""));
    }

    @Test
    void aNullableRowIsReadAndWrittenAsNull() {
        Compilation compilation = compileTasks(output, """
                public record Reading(String sensor, double value) {
                }

                @Task
                public static List<@Nullable Reading> echo(List<@Nullable Reading> input) {
                    return input;
                }
                """);

        Object result = compilation.call(entrypoint(compilation, "echo"), kwargs("""
                {"input": [{"sensor": "a", "value": 1.5}, null]}"""));

        assertThat(json(result)).isEqualTo(json("""
                [{"sensor": "a", "value": 1.5}, null]"""));
    }

    @Test
    void aRecordRoundTripsEveryTypeInTheFormCoreValidates() {
        Compilation compilation = compileTasks(output, RECORDS + """
                @Task
                public static List<Order> echo(List<Order> input) {
                    return input;
                }
                """);

        Object result = compilation.call(entrypoint(compilation, "echo"), kwargs("{\"input\": [" + ORDER + "]}"));

        assertThat(json(result)).isEqualTo(json("[" + ORDER + "]"));
    }

    @Test
    void parametersOfEveryTypeAreReadAsTheMethodDeclaresThem() {
        Compilation compilation = compileTasks(output, RECORDS + """
                @Task
                public static List<Order> build(Rows input, long id, int units, double weight, float ratio,
                        boolean paid, String region, BigDecimal price, LocalDate day, Instant at, Duration hold,
                        byte[] payload, Tier tier, List<String> tags, Map<String, Long> counts, Window window,
                        @Nullable String coupon) {
                    return List.of(new Order(id, units, weight, ratio, paid, region, price, day, at, hold, payload,
                            tier, tags, counts, window, coupon));
                }
                """);

        Object result = compilation.call(entrypoint(compilation, "build"), kwargs(ORDER));

        assertThat(json(result)).isEqualTo(json("[" + ORDER + "]"));
    }

    @Test
    void refusesAParameterOrAFieldOfTheWrongFormNamingWhereItIs() {
        Compilation compilation = compileTasks(output, """
                public record Reading(String sensor, double amount) {
                }

                @Task
                public static List<Reading> scale(List<Reading> input, double threshold) {
                    return input;
                }
                """);
        String entrypoint = entrypoint(compilation, "scale");

        assertThatThrownBy(() -> compilation.call(entrypoint, kwargs("""
                {"threshold": "0.5"}""")))
                .isInstanceOf(TaskValueException.class)
                .hasMessage("parameter threshold is of type String, expected a number");
        assertThatThrownBy(() -> compilation.call(entrypoint, Map.of()))
                .hasMessage("parameter threshold is missing, expected a value in the run's parameters");
        assertThatThrownBy(() -> compilation.call(entrypoint, kwargs("""
                {"threshold": 0.5, "input": [{"sensor": "a", "amount": 1}, {"sensor": "b", "amount": "2"}]}""")))
                .hasMessage("input[1].amount is of type String, expected a number");
        assertThatThrownBy(() -> compilation.call(entrypoint, kwargs("""
                {"threshold": 0.5, "input": [{"sensor": "a"}]}""")))
                .hasMessage("input[0].amount is missing, expected a value");
    }

    @Test
    void refusesANaNOrAnInfinityInTheResultNamingItsPath() {
        Compilation compilation = compileTasks(output, """
                public record Score(String sensor, double score, float ratio, List<Double> history) {
                }

                @Task
                public static List<Score> score(Rows input, String fault) {
                    Score good = new Score("a", 0.5, 0.5f, List.of(1.0));
                    return switch (fault) {
                        case "score" -> List.of(good, good, good, new Score("b", Double.NaN, 0.5f, List.of()));
                        case "ratio" -> List.of(new Score("b", 0.5, Float.NEGATIVE_INFINITY, List.of()));
                        case "history" -> List.of(new Score("b", 0.5, 0.5f, List.of(1.0, Double.POSITIVE_INFINITY)));
                        default -> List.of(good);
                    };
                }
                """);
        String entrypoint = entrypoint(compilation, "score");

        assertThatThrownBy(() -> compilation.call(entrypoint, Map.of("fault", "score")))
                .isInstanceOf(TaskValueException.class)
                .hasMessage("result[3].score is NaN, expected a finite number");
        assertThatThrownBy(() -> compilation.call(entrypoint, Map.of("fault", "ratio")))
                .hasMessage("result[0].ratio is -Infinity, expected a finite number");
        assertThatThrownBy(() -> compilation.call(entrypoint, Map.of("fault", "history")))
                .hasMessage("result[0].history[1] is Infinity, expected a finite number");
        assertThat(json(compilation.call(entrypoint, Map.of("fault", "none")))).isEqualTo(json("""
                [{"sensor": "a", "score": 0.5, "ratio": 0.5, "history": [1.0]}]"""));
    }

    @Test
    void aTaskCompilesInAPackageThatDeclaresItsOwnStringObjectAndTaskValues() {
        Compilation compilation = compileUnit(output, TASKS, """
                public final class Tasks {

                    public record Reading(java.lang.String sensor, double value,
                            Map<java.lang.String, List<Long>> counts) {
                    }

                    @Task
                    public static List<Reading> tag(List<Reading> input, java.lang.String suffix) {
                        return input.stream()
                                .map(reading -> new Reading(reading.sensor() + suffix, reading.value(), reading.counts()))
                                .toList();
                    }
                }
                """, shadowing("String"), shadowing("Object"), shadowing("TaskValues"));

        Object result = compilation.call(entrypoint(compilation, "tag"), kwargs("""
                {"input": [{"sensor": "a", "value": 1.5, "counts": {"x": [1, 2]}}], "suffix": "-eu"}"""));

        assertThat(json(result)).isEqualTo(json("""
                [{"sensor": "a-eu", "value": 1.5, "counts": {"x": [1, 2]}}]"""));
    }

    @Test
    void aNestedClassGetsAFlattenedTaskClassName() {
        Compilation compilation = compileUnit(output, TASKS, """
                public final class Tasks {

                    public static final class Inner {

                        @Task
                        public static Rows run(Rows input) {
                            return input;
                        }
                    }
                }
                """);

        String entrypoint = compilation.task("fixtures.Tasks$Inner", "run").get("entrypoint").asString();

        assertThat(entrypoint).isEqualTo("fixtures.Tasks_Inner_BrokoliTasks#run");
        assertThat(json(compilation.call(entrypoint, kwargs("""
                {"input": [{"id": 1}]}""")))).isEqualTo(json("""
                [{"id": 1}]"""));
    }

    @Test
    void aRecordNamedOutsideTheBuildEncodingStillCompiles() {
        Compilation compilation = compileTasks(output, """
                public record Ładunek(String id) {
                }

                @Task
                public static List<Ładunek> echo(List<Ładunek> input) {
                    return input;
                }
                """);

        assertThat(json(compilation.call(entrypoint(compilation, "echo"), kwargs("""
                {"input": [{"id": "a"}]}""")))).isEqualTo(json("""
                [{"id": "a"}]"""));
    }

    private static Source shadowing(String simpleName) {
        return Source.of("fixtures." + simpleName, "package fixtures;\n\npublic final class " + simpleName
                + " {\n}\n");
    }

    private static String entrypoint(Compilation compilation, String method) {
        assertThat(compilation.errors()).isEmpty();
        return compilation.task(TASKS, method).get("entrypoint").asString();
    }

    private static Map<String, Object> kwargs(String text) {
        return HARNESS_JSON.readValue(text, KWARGS);
    }

    private static JsonNode json(Object value) {
        return HARNESS_JSON.readTree(value instanceof String text ? text : HARNESS_JSON.writeValueAsString(value));
    }
}
