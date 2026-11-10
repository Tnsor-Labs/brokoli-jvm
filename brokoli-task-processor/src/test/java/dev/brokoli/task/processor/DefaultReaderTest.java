package dev.brokoli.task.processor;

import dev.brokoli.task.processor.TestCompiler.Compilation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Path;

import static dev.brokoli.task.processor.Fixtures.TASKS;
import static dev.brokoli.task.processor.Fixtures.compileTasks;
import static org.assertj.core.api.Assertions.assertThat;

class DefaultReaderTest {

    @TempDir
    Path output;

    @Test
    void writesEachDefaultInTheFormCoreValidates() {
        Compilation compilation = compileTasks(output, """
                public enum Tier { gold, silver }

                @Task
                public static Rows run(Rows input,
                        @Default("42") long count,
                        @Default("0.5") double ratio,
                        @Default("-0") double zero,
                        @Default("true") boolean flag,
                        @Default("eu-west") String region,
                        @Default("silver") Tier tier,
                        @Default("12.50") BigDecimal price,
                        @Default("-0.00") BigDecimal noPrice,
                        @Default("2026-01-31") LocalDate day,
                        @Default("2026-01-31T12:00:00+02:00") Instant at,
                        @Default("-PT1.5S") Duration back,
                        @Default("SGVsbG8=") byte[] payload) {
                    return input;
                }
                """);

        assertThat(compilation.errors()).isEmpty();
        JsonNode parameters = compilation.task(TASKS, "run").get("parameters");
        assertThat(defaults(parameters)).isEqualTo(json("""
                {
                    "count": 42,
                    "ratio": 0.5,
                    "zero": 0.0,
                    "flag": true,
                    "region": "eu-west",
                    "tier": "silver",
                    "price": {"$bptd": "decimal", "value": "12.50"},
                    "noPrice": {"$bptd": "decimal", "value": "0.00"},
                    "day": "2026-01-31",
                    "at": {"$bptd": "timestamp", "value": "2026-01-31T10:00:00Z"},
                    "back": {"$bptd": "duration", "seconds": "-1", "nanos": -500000000},
                    "payload": {"$bptd": "bytes", "value": "SGVsbG8="}
                }"""));
        assertThat(parameters.get("count").get("required").asBoolean()).isFalse();
        assertThat(CoreSchema.parameterErrors(parameters)).isEmpty();
    }

    @Test
    void refusesEachKindOfBadDefaultNamingTheParameterAndWhatItExpects() {
        assertRefused("long count", "4.2", "a whole number such as 42");
        assertRefused("int small", "3000000000", "a whole number that fits in an int");
        assertRefused("long big", "9223372036854775808", "a whole number that fits in 64 bits");
        assertRefused("float single", "1e39", "a finite number");
        assertRefused("double ratio", "0x1p3", "a number such as 0.5");
        assertRefused("boolean flag", "yes", "true or false");
        assertRefused("BigDecimal price", "1e2", "a decimal such as 12.50, with no exponent");
        assertRefused("LocalDate day", "2026-02-30", "a date such as 2026-01-31");
        assertRefused("Instant at", "tomorrow", "a UTC timestamp such as 2026-01-31T12:00:00Z");
        assertRefused("Duration window", "90s", "an ISO-8601 duration such as PT1H30M");
        assertRefused("byte[] payload", "SGVsbG8", "padded standard base64");
    }

    @Test
    void refusesADefaultOnAListMapOrRecordParameter() {
        Compilation compilation = compileTasks(output, """
                @Task
                public static Rows run(Rows input, @Default("[]") List<String> tags) {
                    return input;
                }
                """);

        assertThat(compilation.errors()).containsExactly(
                "@Default is not supported on @Task method Tasks.run parameter tags, whose type is array");
    }

    private void assertRefused(String parameter, String text, String expected) {
        String name = parameter.substring(parameter.indexOf(' ') + 1);
        Compilation compilation = compileTasks(output, """
                @Task
                public static Rows run(Rows input, @Default("%s") %s) {
                    return input;
                }
                """.formatted(text, parameter));

        assertThat(compilation.errors()).containsExactly(
                "@Default(\"" + text + "\") on @Task method Tasks.run parameter " + name + " is not " + expected);
    }

    private static JsonNode defaults(JsonNode parameters) {
        ObjectNode defaults = TestCompiler.JSON.createObjectNode();
        parameters.properties().forEach(entry -> defaults.set(entry.getKey(), entry.getValue().get("default")));
        return defaults;
    }

    private static JsonNode json(String text) {
        return TestCompiler.JSON.readTree(text);
    }
}
