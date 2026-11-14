package dev.brokoli.bundle;

import dev.brokoli.bundle.internal.CanonicalJson;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CanonicalJsonTest {

    private static final String VECTORS = "/schema/fixtures/ir-canonicalization/rendering.json";

    @TestFactory
    Stream<DynamicTest> rendersEveryCoreRenderingVector() {
        JsonNode vectors = TaskFile.JSON.readTree(CoreContract.resource(VECTORS));
        assertThat(vectors.path("cases").size()).isEqualTo(35);
        return vectors.path("cases").values().stream().map(vector -> DynamicTest.dynamicTest(
                vector.path("name").asString(),
                () -> assertThat(CanonicalJson.render(TaskFile.JSON.readTree(vector.path("input").asString())))
                        .isEqualTo(vector.path("canonical").asString())));
    }

    @Test
    void writesControlCharactersAsPythonAndJavaScriptDo() {
        JsonNode text = JsonNodeFactory.instance.stringNode("a\bb\fc\u0001d\u007fe\u2028<>&");

        assertThat(CanonicalJson.render(text)).isEqualTo("\"a\\bb\\fc\\u0001d\u007fe\u2028<>&\"\n");
    }

    @Test
    void refusesAnUnpairedSurrogate() {
        JsonNode text = JsonNodeFactory.instance.stringNode("broken \ud800");

        assertThatThrownBy(() -> CanonicalJson.render(text))
                .isInstanceOf(BundleException.class)
                .hasMessage("text is not valid Unicode, it has an unpaired surrogate: broken \\ud800");
    }
}
