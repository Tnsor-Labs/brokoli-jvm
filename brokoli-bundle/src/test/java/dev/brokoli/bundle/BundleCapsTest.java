package dev.brokoli.bundle;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BundleCapsTest {

    private static final long CAP_BYTES = 64L * 1024 * 1024;
    private static final int CAP_ENTRIES = 10_000;
    private static final long MANIFEST_BYTES = 100;
    private static final int PROTO_DIRECTORIES = 3;
    private static final int UNDER_THE_TOTAL_CAP = 2048;
    private static final long SEED = 27;
    private static final String TASK_FILE = """
            {"format":"brokoli.jvm-tasks/v1","class":"com.example.Transforms","tasks":[\
            {"method":"dailyRollup","entrypoint":"com.example.Transforms_BrokoliTasks#dailyRollup",\
            "name":"Daily Rollup","interface":null,"parameters":null}]}
            """;

    @TempDir
    Path project;

    @Test
    void theCapsAreTheOnesCoreAppliesOnUpload() {
        JsonNode limits = TaskFile.JSON.readTree(CoreContract.read(CoreContract.BUNDLES.resolve("limits.json")));

        assertThat(BundleCaps.MAX_ARCHIVE_BYTES).isEqualTo(limits.get("max_archive_bytes").asLong());
        assertThat(BundleCaps.MAX_TOTAL_BYTES).isEqualTo(limits.get("max_total_bytes").asLong());
        assertThat(BundleCaps.MAX_ENTRIES).isEqualTo(limits.get("max_entries").asInt());
    }

    @Test
    void countsTheEntriesTheWrittenTarHoldsWithDirectoriesAndTheManifestButNoPaxHeaders() throws IOException {
        write(project.resolve("classes/META-INF/brokoli/tasks/com.example.Transforms.json"), TASK_FILE);
        write(project.resolve("classes/com/example/Transforms.class"), "class");
        write(project.resolve("classes/com/example/" + "a".repeat(120) + ".class"), "long");
        write(project.resolve("classes/application.properties"), "region=eu");
        TaskBundle bundle = TaskBundles.of(project.resolve("classes"), List.of(), null).get(0);
        ByteArrayOutputStream archive = new ByteArrayOutputStream();
        bundle.writeTo(archive);

        List<BundleFile> files = ProjectFiles.collect(project.resolve("classes"), List.of());

        assertThat(BundleCaps.entries(files)).isEqualTo(6)
                .isEqualTo(TarEntries.read(CoreContract.gunzip(archive.toByteArray())).size());
    }

    @Test
    void acceptsBundlesWithAsManyEntriesAsTheCapAndRefusesOneMoreNamingWhereTheyAre() {
        List<BundleFile> atCap = protoClasses(CAP_ENTRIES - PROTO_DIRECTORIES - 1);
        List<BundleFile> overCap = protoClasses(CAP_ENTRIES - PROTO_DIRECTORIES);

        assertThat(BundleCaps.entries(atCap)).isEqualTo(CAP_ENTRIES);
        assertThatCode(() -> BundleCaps.requireWithinFileCaps(atCap, MANIFEST_BYTES)).doesNotThrowAnyException();
        assertThatThrownBy(() -> BundleCaps.requireWithinFileCaps(overCap, MANIFEST_BYTES))
                .isInstanceOf(BundleException.class)
                .hasMessage("the task bundles would have 10001 entries, over core's cap of 10000; the most are in"
                        + " com/example/proto/ (9997), the top level (2), com/ (1), com/example/ (1)");
    }

    @Test
    void acceptsBundlesHoldingAsManyBytesOfFilesAsTheCapAndRefusesOneMoreNamingTheLargest() {
        byte[] jar = new byte[(int) (CAP_BYTES - MANIFEST_BYTES - 10)];
        List<BundleFile> atCap = List.of(new BundleFile("app.properties", new byte[10]),
                new BundleFile("lib/big.jar", jar));
        List<BundleFile> overCap = List.of(new BundleFile("app.properties", new byte[11]),
                new BundleFile("lib/big.jar", jar));

        assertThatCode(() -> BundleCaps.requireWithinFileCaps(atCap, MANIFEST_BYTES)).doesNotThrowAnyException();
        assertThatThrownBy(() -> BundleCaps.requireWithinFileCaps(overCap, MANIFEST_BYTES))
                .isInstanceOf(BundleException.class)
                .hasMessage("the task bundles would hold 67108865 bytes of files (64.0 MiB), over core's cap of"
                        + " 67108864 bytes (64 MiB); the largest are lib/big.jar (67108754 bytes), manifest.json"
                        + " (100 bytes), app.properties (11 bytes); leave out what the tasks do not need, or mark it"
                        + " provided");
    }

    @Test
    void namesTheFiveLargestFiles() {
        List<BundleFile> files = new ArrayList<>();
        files.add(new BundleFile("lib/big.jar", new byte[(int) CAP_BYTES]));
        for (int i = 1; i <= 6; i++) {
            files.add(new BundleFile("lib/dep-" + i + ".jar", new byte[i * 1000]));
        }

        assertThatThrownBy(() -> BundleCaps.requireWithinFileCaps(files, MANIFEST_BYTES))
                .hasMessageContaining("; the largest are lib/big.jar (67108864 bytes), lib/dep-6.jar (6000 bytes),"
                        + " lib/dep-5.jar (5000 bytes), lib/dep-4.jar (4000 bytes), lib/dep-3.jar (3000 bytes);");
    }

    @Test
    void acceptsAnArchiveAsLargeAsTheCapAndRefusesOneByteMoreNamingTheTask() {
        List<BundleFile> files = List.of(new BundleFile("lib/big.jar", new byte[10]));

        assertThatCode(() -> BundleCaps.requireWithinArchiveCap("Daily Rollup", CAP_BYTES, files, MANIFEST_BYTES))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> BundleCaps.requireWithinArchiveCap("Daily Rollup", CAP_BYTES + 1, files,
                MANIFEST_BYTES))
                .isInstanceOf(BundleException.class)
                .hasMessage("the task bundle of Daily Rollup would be 67108865 bytes (64.0 MiB) compressed, over"
                        + " core's cap of 67108864 bytes (64 MiB); the largest files are manifest.json (100 bytes),"
                        + " lib/big.jar (10 bytes); leave out what the tasks do not need, or mark it provided");
    }

    @Test
    void refusesAProjectWithMoreBytesOfFilesThanTheCapBeforeWritingABundle() throws IOException {
        write(project.resolve("classes/META-INF/brokoli/tasks/com.example.Transforms.json"), TASK_FILE);
        Files.createDirectories(project.resolve("classes/data"));
        Files.write(project.resolve("classes/data/zeros.bin"), new byte[(int) CAP_BYTES]);

        assertThatThrownBy(() -> TaskBundles.of(project.resolve("classes"), List.of(), null))
                .isInstanceOf(BundleException.class)
                .hasMessageStartingWith("the task bundles would hold ")
                .hasMessageContaining("; the largest are data/zeros.bin (67108864 bytes), manifest.json (");
    }

    @Test
    void refusesAnArchiveOverTheCapWhoseFilesAreUnderItWhenItIsWritten() throws IOException {
        write(project.resolve("classes/META-INF/brokoli/tasks/com.example.Transforms.json"), TASK_FILE);
        byte[] incompressible = new byte[(int) CAP_BYTES - UNDER_THE_TOTAL_CAP];
        new Random(SEED).nextBytes(incompressible);
        Files.createDirectories(project.resolve("classes/data"));
        Files.write(project.resolve("classes/data/random.bin"), incompressible);
        TaskBundle bundle = TaskBundles.of(project.resolve("classes"), List.of(), null).get(0);

        assertThatThrownBy(() -> bundle.writeTo(OutputStream.nullOutputStream()))
                .isInstanceOf(BundleException.class)
                .hasMessageStartingWith("the task bundle of Daily Rollup would be ")
                .hasMessageContaining(" compressed, over core's cap of 67108864 bytes (64 MiB); the largest files are"
                        + " data/random.bin (67106816 bytes), manifest.json (");
        assertThatThrownBy(bundle::digest).isInstanceOf(BundleException.class);
    }

    private static List<BundleFile> protoClasses(int count) {
        List<BundleFile> files = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            files.add(new BundleFile("com/example/proto/Message" + i + ".class", new byte[0]));
        }
        return files;
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }
}
