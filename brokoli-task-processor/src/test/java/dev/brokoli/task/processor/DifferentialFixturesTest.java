package dev.brokoli.task.processor;

import dev.brokoli.task.processor.TestCompiler.Compilation;
import dev.brokoli.task.processor.TestCompiler.Source;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DifferentialFixturesTest {

    private static final String VECTORS = "/schema/fixtures/task-interface/differential/";

    private static final String NULLABLE = """
            package fixtures;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Target;

            @Target(ElementType.TYPE_USE)
            public @interface Nullable {
            }
            """;

    @TempDir
    Path output;

    @Test
    void datasetRecordCompilesToTheCoreVector() {
        assertCompilesTo("dataset-record", "fixtures.DatasetRecord", "score", """
                package fixtures;

                import dev.brokoli.task.Task;
                import java.util.List;

                public final class DatasetRecord {

                    public record InputRow(long id, double amount) {
                    }

                    public record ScoredRow(long id, double score) {
                    }

                    @Task
                    public static List<ScoredRow> score(List<InputRow> rows) {
                        return List.of();
                    }
                }
                """);
    }

    @Test
    void defaultedParameterCompilesToTheCoreVector() {
        assertCompilesTo("defaulted-parameter", "fixtures.DefaultedParameter", "score", """
                package fixtures;

                import dev.brokoli.task.Default;
                import dev.brokoli.task.Rows;
                import dev.brokoli.task.Task;

                public final class DefaultedParameter {

                    @Task
                    public static Rows score(Rows rows, @Default("0.5") double threshold) {
                        return rows;
                    }
                }
                """);
    }

    @Test
    void enumFieldCompilesToTheCoreVector() {
        assertCompilesTo("enum-field", "fixtures.EnumField", "classify", """
                package fixtures;

                import dev.brokoli.task.Task;
                import java.util.List;

                public final class EnumField {

                    public enum Status { pending, done, failed }

                    public record Row(Status status) {
                    }

                    @Task
                    public static List<Row> classify(List<Row> rows) {
                        return rows;
                    }
                }
                """);
    }

    @Test
    void nestedRecordCompilesToTheCoreVector() {
        assertCompilesTo("nested-record", "fixtures.NestedRecord", "enrich", """
                package fixtures;

                import dev.brokoli.task.Task;
                import java.util.List;

                public final class NestedRecord {

                    public record Address(String street, String city) {
                    }

                    public record Customer(long id, Address address) {
                    }

                    @Task
                    public static List<Customer> enrich(List<Customer> rows) {
                        return rows;
                    }
                }
                """);
    }

    @Test
    void nullableRequiredFieldCompilesToTheCoreVector() {
        assertCompilesTo("nullable-required-field", "fixtures.NullableRequiredField", "greet", """
                package fixtures;

                import dev.brokoli.task.Task;
                import java.util.List;

                public final class NullableRequiredField {

                    public record Row(String name, @Nullable String nickname) {
                    }

                    @Task
                    public static List<Row> greet(List<Row> rows) {
                        return rows;
                    }
                }
                """);
    }

    @Test
    void requiredParameterCompilesToTheCoreVector() {
        assertCompilesTo("required-parameter", "fixtures.RequiredParameter", "export", """
                package fixtures;

                import dev.brokoli.task.Rows;
                import dev.brokoli.task.Task;

                public final class RequiredParameter {

                    @Task
                    public static Rows export(Rows rows, String region) {
                        return rows;
                    }
                }
                """);
    }

    private void assertCompilesTo(String vector, String className, String method, String code) {
        Compilation compilation = TestCompiler.compile(output, Source.of(className, code),
                Source.of("fixtures.Nullable", NULLABLE));
        assertThat(compilation.errors()).isEmpty();

        JsonNode task = compilation.task(className, method);
        JsonNode expected = fixture(vector);
        JsonNode expectedInterface = expected.get("expected_node_interface");
        JsonNode expectedParameters = expected.get("expected_pipeline_parameters");

        assertThat(task.get("interface")).isEqualTo(expectedInterface);
        if (expectedParameters.isNull()) {
            assertThat(task.get("parameters").isEmpty()).isTrue();
        } else {
            assertThat(task.get("parameters")).isEqualTo(expectedParameters);
        }
    }

    private static JsonNode fixture(String vector) {
        try (InputStream in = DifferentialFixturesTest.class.getResourceAsStream(VECTORS + vector + ".json")) {
            return TestCompiler.JSON.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
