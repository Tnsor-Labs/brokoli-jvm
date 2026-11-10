package dev.brokoli.bundle;

import dev.brokoli.bundle.Manifest.FileEntry;
import dev.brokoli.bundle.Manifest.Payload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ReferenceBundlesTest {

    private static final byte[] GZIP_HEADER = {0x1f, (byte) 0x8b, 0x08, 0x00, 0, 0, 0, 0, 0x00, (byte) 0xff};

    @ParameterizedTest
    @ValueSource(strings = {"classes-only", "classes-and-jar", "long-paths", "non-ascii-paths"})
    void writesTheSameTarAsCoresBrokoliBundleJvm(String name) throws IOException {
        List<String> arguments = CoreContract.arguments(name);
        List<BundleFile> files = filesTheCliPackages(arguments);
        String sourceDigest = Digests.sourceDigest(files);
        String[] entrypoint = CoreContract.values(arguments, "--entrypoint").get(0).split("#");
        Manifest manifest = new Manifest(CoreContract.values(arguments, "--name").get(0), sourceDigest, sourceDigest,
                null, List.of(Payload.jvm(entrypoint[0], entrypoint[1], sourceDigest)),
                files.stream().map(FileEntry::of).toList());

        ByteArrayOutputStream tar = new ByteArrayOutputStream();
        BundleArchive.writeTar(files, manifest, tar);

        assertThat(tar.toByteArray()).isEqualTo(CoreContract.referenceTar(name));
    }

    @Test
    void compressesWithTheGzipHeaderTheSpecSays() throws IOException {
        ByteArrayOutputStream archive = new ByteArrayOutputStream();
        BundleArchive.write(List.of(), new Manifest("n", "i", "s", null, List.of(), List.of()), archive);

        assertThat(Arrays.copyOf(archive.toByteArray(), GZIP_HEADER.length)).isEqualTo(GZIP_HEADER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"classes-only", "classes-and-jar"})
    void packagesTheSameFilesAsCoreFromTheProcessorsOutput(String name) {
        List<String> arguments = CoreContract.arguments(name);
        List<Path> jars = CoreContract.values(arguments, "--jar").stream().map(CoreContract.BUNDLES::resolve).toList();
        Path classes = CoreContract.BUNDLES.resolve(CoreContract.values(arguments, "--classes").get(0));

        TaskBundle bundle = TaskBundles.of(classes, jars, null).get(0);

        String reference = new String(TarEntries.files(CoreContract.referenceTar(name)).get(BundleArchive.MANIFEST),
                StandardCharsets.UTF_8);
        String files = reference.substring(reference.indexOf("\"files\":"));
        assertThat(manifestOf(bundle)).endsWith(files).contains("\"source_digest\":\"" + bundle.sourceDigest() + "\"");
        assertThat(reference).contains("\"source_digest\":\"" + bundle.sourceDigest() + "\"");
    }

    private static String manifestOf(TaskBundle bundle) {
        ByteArrayOutputStream archive = new ByteArrayOutputStream();
        try {
            bundle.writeTo(archive);
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
        byte[] manifest = TarEntries.files(CoreContract.gunzip(archive.toByteArray())).get(BundleArchive.MANIFEST);
        return new String(manifest, StandardCharsets.UTF_8);
    }

    private static List<BundleFile> filesTheCliPackages(List<String> arguments) throws IOException {
        List<BundleFile> files = new ArrayList<>();
        for (String directory : CoreContract.values(arguments, "--classes")) {
            Path root = CoreContract.BUNDLES.resolve(directory);
            try (Stream<Path> tree = Files.walk(root)) {
                for (Path file : tree.filter(Files::isRegularFile).toList()) {
                    if (file.getFileName().toString().endsWith(".class")) {
                        files.add(new BundleFile(ProjectFiles.slashPath(root, file), Files.readAllBytes(file)));
                    }
                }
            }
        }
        for (String jar : CoreContract.values(arguments, "--jar")) {
            Path file = CoreContract.BUNDLES.resolve(jar);
            files.add(new BundleFile(ProjectFiles.LIBRARIES + file.getFileName(), Files.readAllBytes(file)));
        }
        files.sort((left, right) -> Utf8.ORDER.compare(left.path(), right.path()));
        return files;
    }
}
