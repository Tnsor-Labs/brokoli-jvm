package dev.brokoli.pipeline;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BashTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void addsABashNodeWithOnlyItsCommandAndTheComputeCapability() {
        Pipeline p = Pipeline.builder("Jobs").build();

        NodeRef refresh = p.bash("Refresh", Bash.options().command("./refresh.sh"));

        assertThat(refresh.nodeId()).isEqualTo("refresh_1");
        assertThat(JSON.readTree(p.toJson()).path("nodes").get(0)).isEqualTo(JSON.readTree("""
                {"id":"refresh_1","type":"bash","name":"Refresh","config":{"command":"./refresh.sh"},\
                "capabilities":["compute"]}"""));
        assertThat(CoreSchema.pipelineErrors(p.toJson())).isEmpty();
    }

    @Test
    void writesItsOptionsInThePythonSdksOrderWithTheEnvironmentSortedByName() {
        Pipeline p = Pipeline.builder("Jobs").build();
        Map<String, String> env = new LinkedHashMap<>();
        env.put("UNTIL", "${param.until}");
        env.put("SINCE", "${param.since}");

        p.bash("Refresh", Bash.options()
                .pool("jobs")
                .maxCpuTime(Duration.ofMinutes(5))
                .retries(2)
                .maxMemoryMb(512)
                .env(env)
                .workingDir("/data/jobs")
                .command("./refresh.sh"));

        assertThat(config(p).toString()).isEqualTo("""
                {"command":"./refresh.sh","working_dir":"/data/jobs","env":{"SINCE":"${param.since}",\
                "UNTIL":"${param.until}"},"max_memory_mb":512,"max_cpu_seconds":300,"max_retries":2,\
                "retry_backoff":"exponential","pool":"jobs"}""");
    }

    @Test
    void runsAfterItsInput() {
        Pipeline p = Pipeline.builder("Jobs").build();
        DatasetRef rows = p.sourceFile("Read", SourceFile.options().path("/in.csv"));

        p.bash("Refresh", rows, Bash.options().command("./refresh.sh"));

        assertThat(JSON.readTree(p.toJson()).path("edges")).isEqualTo(JSON.readTree("""
                [{"from":"read_1","to":"refresh_1"}]"""));
    }

    @Test
    void takesAPositiveMemoryLimitAndAWholeNumberOfCpuSeconds() {
        Bash limited = Bash.options().maxMemoryMb(1).maxCpuTime(Duration.ZERO);

        assertThat(limited.maxMemoryMb()).hasValue(1);
        assertThat(limited.maxCpuTime()).contains(Duration.ZERO);
    }

    @Test
    void refusesAMemoryLimitThatIsNotPositive() {
        assertThatThrownBy(() -> Bash.options().maxMemoryMb(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("memory limit 0 MB is not positive");
    }

    @Test
    void refusesACpuTimeLimitFinerThanASecondOrNegative() {
        assertThatThrownBy(() -> Bash.options().maxCpuTime(Duration.ofMillis(500)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CPU time limit PT0.5S is not a whole number of seconds, which the server takes");
        assertThatThrownBy(() -> Bash.options().maxCpuTime(Duration.ofSeconds(-5)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CPU time limit PT-5S is negative");
    }

    private static JsonNode config(Pipeline p) {
        return JSON.readTree(p.toJson()).path("nodes").get(0).path("config");
    }
}
