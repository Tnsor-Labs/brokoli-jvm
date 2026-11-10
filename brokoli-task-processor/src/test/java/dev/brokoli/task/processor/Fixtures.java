package dev.brokoli.task.processor;

import dev.brokoli.task.processor.TestCompiler.Compilation;
import dev.brokoli.task.processor.TestCompiler.Source;

import java.nio.file.Path;

final class Fixtures {

    static final String TASKS = "fixtures.Tasks";

    private static final String HEADER = """
            package fixtures;

            import dev.brokoli.task.Default;
            import dev.brokoli.task.Rows;
            import dev.brokoli.task.Task;
            import java.math.BigDecimal;
            import java.time.Duration;
            import java.time.Instant;
            import java.time.LocalDate;
            import java.time.LocalTime;
            import java.util.List;
            import java.util.Map;

            """;

    private static final Source TYPE_USE_NULLABLE = Source.of("fixtures.Nullable", """
            package fixtures;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Target;

            @Target(ElementType.TYPE_USE)
            public @interface Nullable {
            }
            """);

    private static final Source DECLARATION_NULLABLE = Source.of("fixtures.other.Nullable", """
            package fixtures.other;

            import java.lang.annotation.ElementType;
            import java.lang.annotation.Target;

            @Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.METHOD, ElementType.RECORD_COMPONENT})
            public @interface Nullable {
            }
            """);

    private Fixtures() {
    }

    static Compilation compileTasks(Path output, String body) {
        return TestCompiler.compile(output, Source.of(TASKS, HEADER + "public final class Tasks {\n" + body + "\n}\n"),
                TYPE_USE_NULLABLE, DECLARATION_NULLABLE);
    }

    static Compilation compileUnit(Path output, String qualifiedName, String unit) {
        return TestCompiler.compile(output, Source.of(qualifiedName, HEADER + unit), TYPE_USE_NULLABLE,
                DECLARATION_NULLABLE);
    }
}
