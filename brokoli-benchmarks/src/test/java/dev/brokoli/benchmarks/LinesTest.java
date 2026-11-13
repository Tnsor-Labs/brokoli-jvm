package dev.brokoli.benchmarks;

import dev.brokoli.bundle.TaskBundles;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class LinesTest {

    private static final Map<String, Object> KWARGS = Map.of(Lines.INPUT, Lines.generate(10));
    private static final Path CORE_CONTRACT = Path.of(System.getProperty("brokoli.core-contract"));
    private static final Pattern INTERFACE_DIGEST = Pattern.compile("\"interface_digest\":\"(sha256:[0-9a-f]{64})\"");

    @Test
    void theRawMapTaskReturnsWhatTheEntryMethodForRecordsReturns() {
        assertThat(Lines.paidLineMaps(KWARGS)).isEqualTo(Lines_BrokoliTasks.paidLines(KWARGS));
    }

    @Test
    void theRawMapTaskKeepsOnlyThePaidLines() {
        assertThat((List<?>) Lines.paidLineMaps(KWARGS)).hasSize(5)
                .allSatisfy(line -> assertThat(((Map<?, ?>) line).get(Lines.PAID)).isEqualTo(true));
    }

    @Test
    void theRawMapTaskReadsAnAbsentInputAsNoRows() {
        assertThat((List<?>) Lines.paidLineMaps(Map.of())).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"noop, python-noop", "paidLines, python-paid"})
    void eachJvmTaskDeclaresTheInterfaceOfThePythonTaskItIsComparedWith(String method, String pythonBundle)
            throws IOException {
        String jvmDigest = TaskBundles.of(Path.of("target/classes"), List.of(), ColdStart.BUILDER).stream()
                .filter(bundle -> bundle.method().equals(method))
                .findFirst()
                .orElseThrow()
                .interfaceDigest();

        assertThat(jvmDigest).isEqualTo(interfaceDigest(CORE_CONTRACT.resolve("bundles/" + pythonBundle + ".tar.gz")));
    }

    private static String interfaceDigest(Path bundle) throws IOException {
        try (InputStream tar = new GZIPInputStream(Files.newInputStream(bundle))) {
            Matcher digest = INTERFACE_DIGEST.matcher(new String(tar.readAllBytes(), StandardCharsets.ISO_8859_1));
            assertThat(digest.find()).as("%s has an interface_digest", bundle).isTrue();
            return digest.group(1);
        }
    }
}
