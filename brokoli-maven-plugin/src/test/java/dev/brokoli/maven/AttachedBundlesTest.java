package dev.brokoli.maven;

import dev.brokoli.bundle.BundleException;
import dev.brokoli.bundle.TaskBundles;
import dev.brokoli.maven.AttachedBundles.Written;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class AttachedBundlesTest {

    @TempDir
    Path project;

    @Test
    void writesOneArchivePerTaskNamedByTheFinalNameAndTheMethod() throws IOException, NoSuchAlgorithmException {
        Path classes = taskFile("com.example.Transforms", "dailyRollup", "score");

        List<Written> written = AttachedBundles.write(TaskBundles.of(classes, List.of(), null),
                project.resolve("target"), "tasks-1.0");

        assertThat(written).extracting(Written::classifier, Written::file).containsExactly(
                tuple("dailyRollup", project.resolve("target/tasks-1.0-dailyRollup.tar.gz")),
                tuple("score", project.resolve("target/tasks-1.0-score.tar.gz")));
        byte[] archive = Files.readAllBytes(written.get(0).file());
        assertThat(written.get(0).digest()).isEqualTo(
                "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(archive)));
    }

    @Test
    void refusesTwoTasksThatWouldHaveTheSameClassifier() throws IOException {
        Path classes = taskFile("com.example.Daily", "rollup");
        taskFile("com.example.Weekly", "rollup");

        assertThatThrownBy(() -> AttachedBundles.write(TaskBundles.of(classes, List.of(), null),
                project.resolve("target"), "tasks-1.0"))
                .isInstanceOf(BundleException.class)
                .hasMessage("tasks com.example.Daily.rollup and com.example.Weekly.rollup would both be attached with"
                        + " classifier rollup; rename one of the methods");
        assertThat(project.resolve("target")).doesNotExist();
    }

    private Path taskFile(String owner, String... methods) throws IOException {
        StringBuilder tasks = new StringBuilder();
        for (String method : methods) {
            if (!tasks.isEmpty()) {
                tasks.append(',');
            }
            tasks.append("{\"method\":\"").append(method).append("\",\"entrypoint\":\"").append(owner)
                    .append("_BrokoliTasks#").append(method).append("\",\"name\":\"").append(method)
                    .append("\",\"interface\":null,\"parameters\":null}");
        }
        Path classes = project.resolve("classes");
        Path file = classes.resolve("META-INF/brokoli/tasks/" + owner + ".json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "{\"format\":\"brokoli.jvm-tasks/v1\",\"class\":\"" + owner + "\",\"tasks\":["
                + tasks + "]}", StandardCharsets.UTF_8);
        return classes;
    }
}
