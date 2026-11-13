package dev.brokoli.benchmarks;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final String COLD_START = """
            {
              "java_version": "17.0.20",
              "first_attempt_ms": 1840.0,
              "cold_start_ms": {"p50": 81.25, "p95": 92.0, "samples": [81.25, 92.0]},
              "peak_rss_mib": {"p50": 60.75, "max": 61.2, "samples": [60.75, 61.2]},
              "node_duration_ms": {"p50": 120.0, "p95": 131.0, "samples": [120.0, 131.0]}
            }
            """;

    private static final String JMH = """
            [
              {"benchmark": "dev.brokoli.benchmarks.RowsBenchmark.rawMap", "params": {"rows": "100000"},
               "primaryMetric": {"score": 2.0, "scoreError": 0.1, "scoreUnit": "ms/op"}},
              {"benchmark": "dev.brokoli.benchmarks.RowsBenchmark.decodedMap", "params": {"rows": "100000"},
               "primaryMetric": {"score": 4.0, "scoreError": 0.1, "scoreUnit": "ms/op"}},
              {"benchmark": "dev.brokoli.benchmarks.RowsBenchmark.records", "params": {"rows": "100000"},
               "primaryMetric": {"score": 4.2, "scoreError": 0.05, "scoreUnit": "ms/op"}},
              {"benchmark": "dev.brokoli.benchmarks.RowsBenchmark.rows", "params": {"rows": "100000"},
               "primaryMetric": {"score": 3.0, "scoreError": 0.2, "scoreUnit": "ms/op"}}
            ]
            """;

    @Test
    void writesEveryMeasureNextToItsTargetAndEachSdkScoreAgainstTheMapTaskDoingTheSameWork() {
        assertThat(Report.markdown(json(COLD_START), json(JMH))).isEqualTo("""
                ### Benchmarks

                Reporting only, nothing here fails the build. The task JVM ran on Java 17.0.20.

                | Measure | Result | Target |
                |---|---|---|
                | Cold start of a no-op task, p50 of 30 | 81.3 ms | under 100 ms |
                | Cold start of a no-op task, p95 of 30 | 92.0 ms | under 150 ms |
                | First attempt, with the harness compiled | 1840.0 ms | under 2500 ms |
                | Peak RSS of a no-op task, highest of 30 | 61.2 MiB (p50 60.8 MiB) | under 96 MiB |
                | Task node duration in core, p50 and p95 | 120.0 ms and 131.0 ms |  |
                | JMH, 100000 rows through a raw `Map`, reading `paid` only | 2.00 ± 0.10 ms/op |  |
                | JMH, 100000 rows through a raw `Map`, decoding all six columns by hand | 4.00 ± 0.10 ms/op |  |
                | JMH, 100000 rows through SDK records, `List<Order>` | 4.20 ± 0.05 ms/op, +5.0% over the \
                hand-decoded `Map` | at most 5% over the hand-decoded `Map` |
                | JMH, 100000 rows through SDK rows, `Rows` | 3.00 ± 0.20 ms/op, +50.0% over the raw `Map` \
                | at most 5% over the raw `Map` |
                """);
    }

    @Test
    void writesTheJmhScoreAloneWhenItsBaselineIsMissing() {
        String withoutRawMap = """
                [{"benchmark": "dev.brokoli.benchmarks.RowsBenchmark.rows", "params": {"rows": "10"},
                  "primaryMetric": {"score": 3.0, "scoreError": 0.2, "scoreUnit": "ms/op"}}]
                """;

        assertThat(Report.markdown(json(COLD_START), json(withoutRawMap)))
                .endsWith("| JMH, 10 rows through SDK rows, `Rows` | 3.00 ± 0.20 ms/op |  |\n");
    }

    @Test
    void leavesOutTheErrorWhenJmhHadTooFewIterationsToEstimateIt() {
        String withoutError = """
                [{"benchmark": "dev.brokoli.benchmarks.RowsBenchmark.rawMap", "params": {"rows": "10"},
                  "primaryMetric": {"score": 2.0, "scoreError": "NaN", "scoreUnit": "ms/op"}}]
                """;

        assertThat(Report.markdown(json(COLD_START), json(withoutError)))
                .endsWith("| JMH, 10 rows through a raw `Map`, reading `paid` only | 2.00 ms/op |  |\n");
    }

    private static JsonNode json(String text) {
        return JSON.readTree(text);
    }
}
