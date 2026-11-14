package dev.brokoli.pipeline;

import dev.brokoli.bundle.TaskBundle;
import dev.brokoli.bundle.TaskBundles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskNodeTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String TASK_CLASS = TaskNodeTest.class.getName();
    private static final String SCORE_INTERFACE = """
            {"contract":"brokoli.task-interface/v1","inputs":{"input":{"value":{"kind":"dataset",\
            "row":{"kind":"record","fields":[{"name":"id","type":{"kind":"int64"},"required":true}],\
            "additional_fields":false}}}},\
            "outputs":{"result":{"value":{"kind":"dataset","row":{"kind":"unknown"}}}}}""";
    private static final String THRESHOLD = """
            {"threshold":{"type":{"kind":"float64"},"required":false,"default":0.5}}""";
    private static final String TASK_FILE = """
            {"format":"brokoli.jvm-tasks/v1","class":"%1$s","tasks":[\
            {"method":"score","entrypoint":"%1$s_BrokoliTasks#score","name":"Score",\
            "interface":%2$s,"parameters":%3$s},\
            {"method":"noop","entrypoint":"%1$s_BrokoliTasks#noop","name":"Noop","interface":null,"parameters":null},\
            {"method":"rank","entrypoint":"%1$s_BrokoliTasks#rank","name":"Rank","interface":null,\
            "parameters":{"threshold":{"type":{"kind":"int64"},"required":true}}}]}
            """.formatted(TASK_CLASS, SCORE_INTERFACE, THRESHOLD);

    @TempDir
    Path folder;

    private Path classes;
    private TaskProject project;

    @BeforeEach
    void writeTheTaskProject() throws IOException {
        classes = folder.resolve("classes");
        write(classes.resolve("META-INF/brokoli/tasks/" + TASK_CLASS + ".json"), TASK_FILE);
        write(classes.resolve(TASK_CLASS.replace('.', '/') + ".class"), "class");
        write(classes.resolve(TASK_CLASS.replace('.', '/') + "_BrokoliTasks.class"), "entry");
        project = TaskProject.of(classes, List.of());
    }

    @Test
    void addsATaskNodeThatReferencesItsBundleByDigestWithItsInterfaceAndTheTasksParameters() {
        Pipeline p = Pipeline.builder("Scores").project(project).build();
        DatasetRef rows = p.sourceFile("Read", SourceFile.options().path("/data/rows.json").format("json"));

        NodeRef score = rows.then(p.task(TaskNodeTest.class, "score"));

        JsonNode ir = JSON.readTree(p.toJson());
        String digest = p.taskBundles().get(0).digest();
        assertThat(score.nodeId()).isEqualTo("score_1");
        assertThat(ir.path("ir_version").stringValue()).isEqualTo("2.2");
        assertThat(ir.path("nodes").get(1)).isEqualTo(JSON.readTree("""
                {"id":"score_1","type":"task","name":"Score",\
                "config":{"task_bundle":{"digest":"%s","format":"brokoli.task-bundle/v2"}},\
                "capabilities":["compute"],"interface":%s}""".formatted(digest, SCORE_INTERFACE)));
        assertThat(ir.path("edges")).isEqualTo(JSON.readTree("""
                [{"from":"read_1","to":"score_1"}]"""));
        assertThat(ir.path("parameters")).isEqualTo(JSON.readTree(THRESHOLD));
        assertThat(CoreSchema.pipelineErrors(p.toJson())).isEmpty();
    }

    @Test
    void packagesTheTaskAsTheMavenPluginOfTheSameVersionDoes() {
        Pipeline p = Pipeline.builder("Scores").project(project).build();

        p.task(TaskNodeTest.class, "score");

        TaskBundle packaged = TaskBundles.of(classes, List.of(), "brokoli-jvm/" + System.getProperty("brokoli.version"))
                .get(0);
        assertThat(p.taskBundles()).singleElement().satisfies(bundle -> {
            assertThat(bundle.method()).isEqualTo("score");
            assertThat(bundle.digest()).isEqualTo(packaged.digest());
        });
    }

    @Test
    void leavesOutTheInterfaceOfATaskWithoutRowTypesAndStaysOnIr20WithoutParameters() {
        Pipeline p = Pipeline.builder("Noop").project(project).build();

        p.task(TaskNodeTest.class, "noop");

        JsonNode ir = JSON.readTree(p.toJson());
        assertThat(ir.path("ir_version").stringValue()).isEqualTo("2.0");
        assertThat(ir.path("nodes").get(0).has("interface")).isFalse();
        assertThat(ir.has("parameters")).isFalse();
    }

    @Test
    void referencesOneBundleFromTwoNodesOfTheSameTask() {
        Pipeline p = Pipeline.builder("Scores").project(project).build();

        NodeRef first = p.task(TaskNodeTest.class, "score");
        NodeRef second = p.task(TaskNodeTest.class, "score");

        assertThat(List.of(first.nodeId(), second.nodeId())).containsExactly("score_1", "score_2");
        assertThat(p.taskBundles()).hasSize(1);
    }

    @Test
    void drawsOneEdgeFromAnInputGivenTwiceAsThePythonSdkDoes() {
        Pipeline p = Pipeline.builder("Scores").project(project).build();
        DatasetRef rows = p.sourceFile("Read", SourceFile.options().path("/data/rows.json").format("json"));

        p.task(TaskNodeTest.class, "score", rows, rows);

        assertThat(JSON.readTree(p.toJson()).path("edges")).isEqualTo(JSON.readTree("""
                [{"from":"read_1","to":"score_1"}]"""));
    }

    @Test
    void refusesAMethodTheTaskProjectHasNoTaskFor() {
        Pipeline p = Pipeline.builder("Scores").project(project).build();

        assertThatThrownBy(() -> p.task(TaskNodeTest.class, "missing"))
                .isInstanceOf(PipelineException.class)
                .hasMessage(TASK_CLASS + " has no @Task method missing");
    }

    @Test
    void refusesATaskWhenThePipelineHasNoTaskProject() {
        Pipeline p = Pipeline.builder("Scores").build();

        assertThatThrownBy(() -> p.task(TaskNodeTest.class, "score"))
                .isInstanceOf(PipelineException.class)
                .hasMessage("pipeline Scores has no task project to package " + TASK_CLASS + ".score from; give it"
                        + " one with Pipeline.builder(name).project(TaskProject.of(classesDirectory, jars))");
    }

    @Test
    void refusesAParameterThatAnotherTaskDeclaresDifferentlyAndAddsNoNode() {
        Pipeline p = Pipeline.builder("Scores").project(project).build();
        p.task(TaskNodeTest.class, "score");

        assertThatThrownBy(() -> p.task(TaskNodeTest.class, "rank"))
                .isInstanceOf(PipelineException.class)
                .hasMessage("parameter threshold of " + TASK_CLASS + ".rank is declared as"
                        + " {\"type\":{\"kind\":\"int64\"},\"required\":true}, but another task of pipeline Scores"
                        + " declares it as {\"type\":{\"kind\":\"float64\"},\"required\":false,\"default\":0.5};"
                        + " give the parameters different names");
        assertThat(JSON.readTree(p.toJson()).path("nodes")).hasSize(1);
    }

    @Test
    void refusesAnInputFromAnotherPipeline() {
        Pipeline other = Pipeline.builder("Other").build();
        DatasetRef foreign = other.sourceFile("Read", SourceFile.options().path("/data/rows.json"));
        Pipeline p = Pipeline.builder("Scores").project(project).build();

        assertThatThrownBy(() -> p.task(TaskNodeTest.class, "score", foreign))
                .isInstanceOf(PipelineException.class)
                .hasMessage("nodes from different pipelines cannot be connected: read_1 is not a node of pipeline"
                        + " Scores");
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }
}
