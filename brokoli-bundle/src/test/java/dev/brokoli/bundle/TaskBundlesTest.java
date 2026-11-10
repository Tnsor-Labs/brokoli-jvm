package dev.brokoli.bundle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TaskBundlesTest {

    private static final String TASK_FILE = """
            {"format":"brokoli.jvm-tasks/v1","class":"com.example.Transforms","tasks":[\
            {"method":"dailyRollup","entrypoint":"com.example.Transforms_BrokoliTasks#dailyRollup",\
            "name":"Daily Rollup","interface":null,"parameters":null},\
            {"method":"score","entrypoint":"com.example.Transforms_BrokoliTasks#score","name":"Score",\
            "interface":{"contract":"brokoli.task-interface/v1","inputs":{"input":{"value":{"kind":"dataset",\
            "row":{"kind":"record","fields":[{"name":"id","type":{"kind":"int64"},"required":true}],\
            "additional_fields":false}}}},"outputs":{"result":{"value":{"kind":"dataset","row":{"kind":"unknown"}}}}},\
            "parameters":null}]}
            """;

    @TempDir
    Path project;

    @Test
    void preparesOneBundlePerTaskNamedByItsDisplayNameAndCallingItsEntryMethod() throws IOException {
        Path classes = classes();

        List<TaskBundle> bundles = TaskBundles.of(classes, List.of(), null);

        assertThat(bundles).extracting(TaskBundle::name, TaskBundle::className, TaskBundle::method,
                TaskBundle::entrypoint).containsExactly(
                        tuple("Daily Rollup", "com.example.Transforms", "dailyRollup",
                                "com.example.Transforms_BrokoliTasks#dailyRollup"),
                        tuple("Score", "com.example.Transforms", "score",
                                "com.example.Transforms_BrokoliTasks#score"));
        assertThat(bundles.get(0).interfaceDigest()).isEqualTo(TaskInterfaces.interfaceDigest(null));
        assertThat(bundles.get(1).interfaceDigest()).isNotEqualTo(bundles.get(0).interfaceDigest());
        assertThat(bundles.get(1).sourceDigest()).isEqualTo(bundles.get(0).sourceDigest());
    }

    @Test
    void packagesClassesResourcesAndJarsButNotTheTaskFiles() throws IOException {
        Path classes = classes();
        Path jar = write(project.resolve("repository/dep-1.0.jar"), "jar");

        Map<String, byte[]> files = filesOf(TaskBundles.of(classes, List.of(jar), null).get(0));

        assertThat(files.keySet()).containsExactly("manifest.json", "application.properties",
                "com/example/Transforms.class", "com/example/Transforms_BrokoliTasks.class", "lib/dep-1.0.jar");
    }

    @Test
    void writesTheManifestOfTheTaskWithTheBuilder() throws IOException {
        TaskBundle bundle = TaskBundles.of(classes(), List.of(), "brokoli-jvm/0.1.0").get(1);

        String manifest = new String(filesOf(bundle).get("manifest.json"), StandardCharsets.UTF_8);

        assertThat(manifest).startsWith("{\"format\":\"brokoli.task-bundle/v2\",\"name\":\"Score\","
                + "\"interface_digest\":\"" + bundle.interfaceDigest() + "\",\"source_digest\":\""
                + bundle.sourceDigest() + "\",\"build\":{\"builder\":\"brokoli-jvm/0.1.0\"},"
                + "\"payloads\":[{\"id\":\"jvm-any\",\"runtime\":\"jvm\",\"os\":\"any\",\"arch\":\"any\","
                + "\"entrypoint\":{\"module\":\"com.example.Transforms_BrokoliTasks\",\"symbol\":\"score\"},"
                + "\"effects\":\"pure\",\"payload_digest\":\"" + bundle.sourceDigest() + "\"}],"
                + "\"files\":[{\"path\":\"application.properties\",");
    }

    @Test
    void writesTheSameBytesEveryTimeAndReturnsTheirDigest() throws IOException {
        TaskBundle bundle = TaskBundles.of(classes(), List.of(), null).get(0);
        ByteArrayOutputStream first = new ByteArrayOutputStream();
        ByteArrayOutputStream second = new ByteArrayOutputStream();

        String digest = bundle.writeTo(first);
        bundle.writeTo(second);

        assertThat(first.toByteArray()).isEqualTo(second.toByteArray());
        assertThat(digest).isEqualTo(Digests.digest(first.toByteArray()));
    }

    @Test
    void refusesAProjectWithoutTaskFiles() throws IOException {
        Path classes = write(project.resolve("classes/com/example/Plain.class"), "class").getParent().getParent()
                .getParent();

        assertThatThrownBy(() -> TaskBundles.of(classes, List.of(), null))
                .isInstanceOf(BundleException.class)
                .hasMessageStartingWith("no task file under ")
                .hasMessageEndingWith("; the project has no @Task method, or brokoli-task-processor is not in the"
                        + " compiler's annotationProcessorPaths");
    }

    @Test
    void refusesTwoJarsWithTheSameFileName() throws IOException {
        Path classes = classes();
        Path first = write(project.resolve("a/dep.jar"), "a");
        Path second = write(project.resolve("b/dep.jar"), "b");

        assertThatThrownBy(() -> TaskBundles.of(classes, List.of(first, second), null))
                .isInstanceOf(BundleException.class)
                .hasMessage(first + " and " + second + " would both be lib/dep.jar in the task bundle");
    }

    @Test
    void refusesADependencyThatIsNotAFile() throws IOException {
        Path classes = classes();
        Path directory = Files.createDirectories(project.resolve("other/target/classes"));

        assertThatThrownBy(() -> TaskBundles.of(classes, List.of(directory), null))
                .isInstanceOf(BundleException.class)
                .hasMessage("dependency " + directory + " is not a file; package it before bundling");
    }

    @Test
    void refusesATaskFileOfAnotherFormat() throws IOException {
        Path classes = project.resolve("classes");
        write(classes.resolve("META-INF/brokoli/tasks/com.example.T.json"), "{\"format\":\"brokoli.jvm-tasks/v2\"}");

        assertThatThrownBy(() -> TaskBundles.of(classes, List.of(), null))
                .isInstanceOf(BundleException.class)
                .hasMessage("task file META-INF/brokoli/tasks/com.example.T.json has format \"brokoli.jvm-tasks/v2\","
                        + " expected \"brokoli.jvm-tasks/v1\"; rebuild with the brokoli-task-processor of this"
                        + " version");
    }

    @Test
    void refusesATaskFileWithADuplicateKey() throws IOException {
        Path classes = project.resolve("classes");
        write(classes.resolve("META-INF/brokoli/tasks/com.example.T.json"),
                "{\"format\":\"brokoli.jvm-tasks/v1\",\"format\":\"brokoli.jvm-tasks/v1\"}");

        assertThatThrownBy(() -> TaskBundles.of(classes, List.of(), null))
                .isInstanceOf(BundleException.class)
                .hasMessageStartingWith("task file META-INF/brokoli/tasks/com.example.T.json is not valid JSON: ")
                .hasMessageContaining("format");
    }

    @Test
    void refusesAnEntrypointThatIsNotClassHashMethod() throws IOException {
        Path classes = project.resolve("classes");
        write(classes.resolve("META-INF/brokoli/tasks/com.example.T.json"), TASK_FILE.replace(
                "com.example.Transforms_BrokoliTasks#dailyRollup", "com.example.Transforms_BrokoliTasks.dailyRollup"));

        assertThatThrownBy(() -> TaskBundles.of(classes, List.of(), null))
                .isInstanceOf(BundleException.class)
                .hasMessage("task com.example.Transforms.dailyRollup in META-INF/brokoli/tasks/com.example.T.json has"
                        + " entrypoint \"com.example.Transforms_BrokoliTasks.dailyRollup\", expected Class#method");
    }

    @Test
    void refusesASymbolicLink() throws IOException {
        Path classes = classes();
        Path link = classes.resolve("linked.properties");
        try {
            Files.createSymbolicLink(link, classes.resolve("application.properties"));
        } catch (IOException | UnsupportedOperationException notAllowed) {
            assumeTrue(false, "this file system does not let the test create a symbolic link");
        }

        assertThatThrownBy(() -> TaskBundles.of(classes, List.of(), null))
                .isInstanceOf(BundleException.class)
                .hasMessage(link + " is a symbolic link; a task bundle holds only regular files");
    }

    private Path classes() throws IOException {
        Path classes = project.resolve("classes");
        write(classes.resolve("META-INF/brokoli/tasks/com.example.Transforms.json"), TASK_FILE);
        write(classes.resolve("META-INF/brokoli/other.txt"), "plugin only");
        write(classes.resolve("com/example/Transforms.class"), "class");
        write(classes.resolve("com/example/Transforms_BrokoliTasks.class"), "entry");
        write(classes.resolve("application.properties"), "region=eu");
        return classes;
    }

    private static Path write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        return Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private static Map<String, byte[]> filesOf(TaskBundle bundle) throws IOException {
        ByteArrayOutputStream archive = new ByteArrayOutputStream();
        bundle.writeTo(archive);
        return TarEntries.files(CoreContract.gunzip(archive.toByteArray()));
    }
}
