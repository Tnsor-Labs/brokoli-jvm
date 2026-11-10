package dev.brokoli.task.processor;

import dev.brokoli.task.processor.TestCompiler.Compilation;
import dev.brokoli.task.processor.TestCompiler.Source;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static dev.brokoli.task.processor.Fixtures.TASKS;
import static dev.brokoli.task.processor.Fixtures.compileTasks;
import static dev.brokoli.task.processor.Fixtures.compileUnit;
import static org.assertj.core.api.Assertions.assertThat;

class TaskProcessorTest {

    @TempDir
    Path output;

    @Test
    void writesOneTaskFileForTheClassWithEachTaskInDeclarationOrder() {
        Compilation compilation = compileTasks(output, """
                @Task(name = "Daily Rollup")
                public static Rows rollup(Rows input, @Default("0.5") double threshold) {
                    return input;
                }

                @Task
                public static Rows cleanOrders(Rows input) {
                    return input;
                }

                public static Rows notATask(Rows input) {
                    return input;
                }
                """);

        assertThat(compilation.errors()).isEmpty();
        JsonNode file = compilation.taskFile(TASKS);
        assertThat(file.get("format").asString()).isEqualTo("brokoli.jvm-tasks/v1");
        assertThat(file.get("class").asString()).isEqualTo(TASKS);
        assertThat(file.get("tasks")).hasSize(2);
        assertThat(file.get("tasks").get(0).get("method").asString()).isEqualTo("rollup");
        assertThat(file.get("tasks").get(0).get("name").asString()).isEqualTo("Daily Rollup");
        assertThat(file.get("tasks").get(1).get("method").asString()).isEqualTo("cleanOrders");
        assertThat(file.get("tasks").get(1).get("name").asString()).isEqualTo("Clean Orders");
        assertThat(file.get("tasks").get(1).get("interface").isNull()).isTrue();
    }

    @Test
    void aClassWithoutTasksGetsNoTaskFile() {
        Compilation compilation = compileTasks(output, """
                public static Rows notATask(Rows input) {
                    return input;
                }
                """);

        assertThat(compilation.errors()).isEmpty();
        assertThat(Files.exists(compilation.taskFilePath(TASKS))).isFalse();
    }

    @Test
    void aPublicStaticNestedClassIsReachableAndItsFileUsesTheBinaryName() {
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

        assertThat(compilation.errors()).isEmpty();
        assertThat(compilation.taskFile("fixtures.Tasks$Inner").get("tasks")).hasSize(1);
    }

    @Test
    void refusesAMethodThatIsNotPublicStaticAndWritesNoFile() {
        Compilation compilation = compileTasks(output, """
                @Task
                public Rows instance(Rows input) {
                    return input;
                }

                @Task
                static Rows packagePrivate(Rows input) {
                    return input;
                }
                """);

        assertThat(compilation.errors()).containsExactly(
                "@Task method Tasks.instance must be public static",
                "@Task method Tasks.packagePrivate must be public static");
        assertThat(Files.exists(compilation.taskFilePath(TASKS))).isFalse();
    }

    @Test
    void refusesATaskInAClassTheHarnessCannotReach() {
        Compilation hidden = compileUnit(output, TASKS, """
                public final class Tasks {

                    static final class Hidden {

                        @Task
                        public static Rows run(Rows input) {
                            return input;
                        }
                    }

                    public final class Inner {

                        @Task
                        public static Rows run(Rows input) {
                            return input;
                        }
                    }
                }
                """);

        assertThat(hidden.errors()).containsExactlyInAnyOrder(
                "@Task method Hidden.run is in fixtures.Tasks.Hidden, which must be a public top-level class "
                        + "or a public static nested class",
                "@Task method Inner.run is in fixtures.Tasks.Inner, which must be a public top-level class "
                        + "or a public static nested class");
    }

    @Test
    void refusesTypeParameters() {
        Compilation compilation = compileTasks(output, """
                @Task
                public static <T> Rows generic(Rows input) {
                    return input;
                }
                """);

        assertThat(compilation.errors()).containsExactly("@Task method Tasks.generic must not declare type parameters");
    }

    @Test
    void refusesATaskWithoutInputRows() {
        Compilation compilation = compileTasks(output, """
                @Task
                public static Rows nothing() {
                    return Rows.empty();
                }
                """);

        assertThat(compilation.errors()).containsExactly(
                "@Task method Tasks.nothing must take its input rows as its first parameter: Rows, or a List of a record");
    }

    @Test
    void refusesAnInputOrResultThatIsNotRowsOrAListOfARecord() {
        Compilation compilation = compileTasks(output, """
                @Task
                public static Rows texts(List<String> input) {
                    return Rows.empty();
                }

                @Task
                public static void nothingBack(Rows input) {
                }

                @Task
                public static List<Map<String, Object>> maps(Rows input) {
                    return List.of();
                }
                """);

        assertThat(compilation.errors()).containsExactly(
                "@Task method Tasks.texts input must be Rows or a List of a record, found java.util.List<java.lang.String>",
                "@Task method Tasks.nothingBack result must be Rows or a List of a record, found void",
                "@Task method Tasks.maps result must be Rows or a List of a record, "
                        + "found java.util.List<java.util.Map<java.lang.String,java.lang.Object>>");
    }

    @Test
    void refusesADefaultOnTheInputRows() {
        Compilation compilation = compileTasks(output, """
                @Task
                public static Rows run(@Default("x") Rows input) {
                    return input;
                }
                """);

        assertThat(compilation.errors()).containsExactly(
                "@Task method Tasks.run has @Default on its input rows, which always come from the upstream node");
    }

    @Test
    void refusesAParameterNamedLikeTheInputRows() {
        Compilation compilation = compileTasks(output, """
                @Task
                public static Rows run(Rows rows, String input) {
                    return rows;
                }
                """);

        assertThat(compilation.errors()).containsExactly(
                "@Task method Tasks.run parameter input is named like the input rows, which the harness "
                        + "passes under 'input'; rename it");
    }

    @Test
    void refusesTwoTasksWithTheSameMethodName() {
        Compilation compilation = compileTasks(output, """
                @Task
                public static Rows run(Rows input) {
                    return input;
                }

                @Task
                public static Rows run(Rows input, String region) {
                    return input;
                }
                """);

        assertThat(compilation.errors()).containsExactly(
                "@Task method Tasks.run is declared twice; the harness finds a task by its method name, so each "
                        + "must be unique");
    }

    @Test
    void refusesABlankNameButAcceptsAnEmptyOneAsTheMethodName() {
        Compilation compilation = compileTasks(output, """
                @Task(name = "  ")
                public static Rows blank(Rows input) {
                    return input;
                }
                """);

        assertThat(compilation.errors()).containsExactly(
                "@Task method Tasks.blank has a blank @Task name; leave it out to use the method name");
    }

    @Test
    void readsATaskWhoseRecordAnotherProcessorGeneratesInTheNextRound() {
        Source order = Source.of("fixtures.generated.Order", """
                package fixtures.generated;

                public record Order(String id, long quantity) {
                }
                """);

        Compilation compilation = compileTasks(output, """
                @Task
                public static Rows run(List<fixtures.generated.Order> input) {
                    return Rows.empty();
                }
                """, List.of(new SourceGenerator(order)));

        assertThat(compilation.errors()).isEmpty();
        assertThat(compilation.task(TASKS, "run").get("interface").get("inputs").get("input").get("value")
                .get("row").get("fields")).isEqualTo(TestCompiler.JSON.readTree("""
                [
                    {"name": "id", "type": {"kind": "string"}, "required": true},
                    {"name": "quantity", "type": {"kind": "int64"}, "required": true}
                ]"""));
    }

    @Test
    void leavesAMissingTypeToJavacAndWritesNoFile() {
        Compilation compilation = compileTasks(output, """
                @Task
                public static Rows parameter(Rows input, Missing missing) {
                    return input;
                }

                @Task
                public static Rows nested(List<Missing> input, Map<String, List<Missing>> byKey) {
                    return Rows.empty();
                }
                """);

        assertThat(compilation.errors()).isNotEmpty().allSatisfy(error -> assertThat(error)
                .startsWith("cannot find symbol"));
        assertThat(Files.exists(compilation.taskFilePath(TASKS))).isFalse();
    }

    @Test
    void refusesATaskNameWithAnUnpairedSurrogate() {
        Compilation compilation = compileTasks(output, """
                @Task(name = "Rollup \\uDE00")
                public static Rows run(Rows input) {
                    return input;
                }
                """);

        assertThat(compilation.errors()).containsExactly(
                "@Task method Tasks.run has a @Task name with an unpaired surrogate at index 7; Brokoli text "
                        + "must be valid Unicode");
    }

    @Test
    void leavesTaskAndDefaultUnclaimedSoAnotherProcessorStillSeesThem() {
        AnnotationRecorder later = new AnnotationRecorder();

        Compilation compilation = compileTasks(output, """
                @Task
                public static Rows run(Rows input, @Default("eu") String region) {
                    return input;
                }
                """, List.of(later));

        assertThat(compilation.errors()).isEmpty();
        assertThat(later.seen()).contains("dev.brokoli.task.Task", "dev.brokoli.task.Default");
    }

    @Test
    void theOnlyWarningATaskGetsUnderXlintAllIsJavacsUnclaimedAnnotationsWarning() {
        Compilation compilation = compileTasks(output, """
                @Task
                public static Rows run(Rows input, @Default("eu") String region) {
                    return input;
                }
                """);

        assertThat(compilation.errors()).isEmpty();
        assertThat(compilation.warningCodes()).containsExactly("compiler.warn.proc.annotations.without.processors");
    }

    @Test
    void anInvalidTaskStopsOnlyItsOwnClassFile() {
        Compilation compilation = compileUnit(output, TASKS, """
                public final class Tasks {

                    public static final class Good {

                        @Task
                        public static Rows run(Rows input) {
                            return input;
                        }
                    }

                    public static final class Bad {

                        @Task
                        public Rows run(Rows input) {
                            return input;
                        }
                    }
                }
                """);

        assertThat(compilation.errors()).containsExactly("@Task method Bad.run must be public static");
        assertThat(Files.exists(compilation.taskFilePath("fixtures.Tasks$Good"))).isTrue();
        assertThat(Files.exists(compilation.taskFilePath("fixtures.Tasks$Bad"))).isFalse();
    }
}
