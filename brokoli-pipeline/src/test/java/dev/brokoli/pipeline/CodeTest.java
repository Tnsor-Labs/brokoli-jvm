package dev.brokoli.pipeline;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodeTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SCRIPT = "output_data = {'columns': columns, 'rows': rows}";

    @Test
    void writesAPythonScriptInThePythonSdksOrderWithTheInterpreterBeforeTheRetries() {
        Pipeline p = Pipeline.builder("Shape").build();

        p.code("Shape", Code.python(SCRIPT)
                .pool("compute")
                .maxCpuTime(Duration.ofSeconds(60))
                .maxMemoryMb(512)
                .timeout(Duration.ofMinutes(2))
                .retries(1)
                .pythonPath("/opt/venv/bin/python"));

        assertThat(config(p).toString()).isEqualTo("""
                {"language":"python","script":"output_data = {'columns': columns, 'rows': rows}",\
                "python_path":"/opt/venv/bin/python","max_retries":1,"retry_backoff":"exponential","timeout":120,\
                "max_memory_mb":512,"max_cpu_seconds":60,"pool":"compute"}""");
        assertThat(CoreSchema.pipelineErrors(p.toJson())).isEmpty();
    }

    @Test
    void writesATypeScriptScriptWithTheNodeExecutableAfterTheRetries() {
        Pipeline p = Pipeline.builder("Shape").build();

        p.code("Shape", Code.typescript("output_data = { columns, rows };")
                .nodePath("/usr/bin/node")
                .retryDelay(Duration.ofSeconds(2))
                .retries(1));

        assertThat(config(p).toString()).isEqualTo("""
                {"language":"typescript","script":"output_data = { columns, rows };","max_retries":1,\
                "retry_backoff":"exponential","retry_delay":2000,"node_path":"/usr/bin/node"}""");
    }

    @Test
    void runsOnItsInputAsAComputeNode() {
        Pipeline p = Pipeline.builder("Shape").build();
        DatasetRef rows = p.sourceDb("Orders", SourceDb.options().query("SELECT 1"));

        p.code("Shape", rows, Code.python(SCRIPT));

        JsonNode ir = JSON.readTree(p.toJson());
        assertThat(ir.path("nodes").get(1).path("capabilities")).isEqualTo(JSON.readTree("""
                ["compute"]"""));
        assertThat(ir.path("edges")).isEqualTo(JSON.readTree("""
                [{"from":"orders_1","to":"shape_1"}]"""));
    }

    @Test
    void refusesARuntimeOfTheOtherLanguage() {
        assertThatThrownBy(() -> Code.typescript("output_data = { columns, rows };").pythonPath("/usr/bin/python3"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("the Python interpreter /usr/bin/python3 only runs a Python script, but the script is"
                        + " typescript");
        assertThatThrownBy(() -> Code.python(SCRIPT).nodePath("/usr/bin/node"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("the Node executable /usr/bin/node only runs a TypeScript script, but the script is"
                        + " python");
        assertThatThrownBy(() -> new Code(CodeLanguage.TYPESCRIPT, SCRIPT, Optional.of("/usr/bin/python3"),
                Optional.empty(), OptionalInt.empty(), Optional.empty(), NodeOptions.defaults()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesLimitsTheServerCannotTakeAsBashDoes() {
        assertThat(Code.python(SCRIPT).maxMemoryMb(1).maxCpuTime(Duration.ZERO).maxMemoryMb()).hasValue(1);
        assertThatThrownBy(() -> Code.python(SCRIPT).maxMemoryMb(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("memory limit 0 MB is not positive");
        assertThatThrownBy(() -> Code.python(SCRIPT).maxCpuTime(Duration.ofMillis(1500)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CPU time limit PT1.5S is not a whole number of seconds, which the server takes");
    }

    private static JsonNode config(Pipeline p) {
        return JSON.readTree(p.toJson()).path("nodes").get(0).path("config");
    }
}
