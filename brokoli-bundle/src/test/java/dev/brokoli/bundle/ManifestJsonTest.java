package dev.brokoli.bundle;

import dev.brokoli.bundle.Manifest.FileEntry;
import dev.brokoli.bundle.Manifest.Payload;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ManifestJsonTest {

    @Test
    void writesCoresMinimalManifestInStructOrderWithoutTheEmptyBuild() {
        Manifest manifest = new Manifest("n", "i", "s", null,
                List.of(new Payload("p", "jvm", "any", "any", "M", "s", "pure", "d")), List.of());

        assertThat(text(manifest)).isEqualTo("{\"format\":\"brokoli.task-bundle/v2\",\"name\":\"n\","
                + "\"interface_digest\":\"i\",\"source_digest\":\"s\",\"payloads\":[{\"id\":\"p\",\"runtime\":\"jvm\","
                + "\"os\":\"any\",\"arch\":\"any\",\"entrypoint\":{\"module\":\"M\",\"symbol\":\"s\"},"
                + "\"effects\":\"pure\",\"payload_digest\":\"d\"}],\"files\":[]}");
    }

    @Test
    void writesTheBuilderAfterTheSourceDigestAndTheFilesLast() {
        Manifest manifest = new Manifest("Rollup", "sha256:i", "sha256:s", "brokoli-jvm/0.1.0",
                List.of(Payload.jvm("com.example.T_BrokoliTasks", "run", "sha256:s")),
                List.of(new FileEntry("com/example/T.class", 3, "abc")));

        assertThat(text(manifest)).isEqualTo("{\"format\":\"brokoli.task-bundle/v2\",\"name\":\"Rollup\","
                + "\"interface_digest\":\"sha256:i\",\"source_digest\":\"sha256:s\","
                + "\"build\":{\"builder\":\"brokoli-jvm/0.1.0\"},\"payloads\":[{\"id\":\"jvm-any\",\"runtime\":\"jvm\","
                + "\"os\":\"any\",\"arch\":\"any\",\"entrypoint\":{\"module\":\"com.example.T_BrokoliTasks\","
                + "\"symbol\":\"run\"},\"effects\":\"pure\",\"payload_digest\":\"sha256:s\"}],"
                + "\"files\":[{\"path\":\"com/example/T.class\",\"size\":3,\"sha256\":\"abc\"}]}");
    }

    @Test
    void escapesTextAsGo126EncodingJsonDoes() {
        Manifest manifest = new Manifest("a\bb\fc\u0001d\u007fe\u2028<>&\u00e9 \"\\\n\r\t\u2029", "i", "s", null,
                List.of(), List.of());

        assertThat(text(manifest)).contains(
                "\"name\":\"a\\bb\\fc\\u0001d\u007fe\\u2028\\u003c\\u003e\\u0026\u00e9 \\\"\\\\\\n\\r\\t\\u2029\"");
    }

    private static String text(Manifest manifest) {
        return new String(ManifestJson.write(manifest), StandardCharsets.UTF_8);
    }
}
