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
              "jvm": {
                "first_attempt_ms": 1840.0,
                "wall_ms": {"p50": 81.25, "p95": 92.0, "max": 95.0, "samples": [81.25, 92.0]},
                "rss_mib": {"p50": 60.75, "p95": 61.0, "max": 61.2, "samples": [60.75, 61.2]},
                "node_ms": {"p50": 120.0, "p95": 131.0, "max": 140.0, "samples": [120.0, 131.0]}
              },
              "python": {
                "first_attempt_ms": 95.0,
                "wall_ms": {"p50": 50.0, "p95": 60.0, "max": 61.0, "samples": [50.0, 60.0]},
                "rss_mib": {"p50": 10.0, "p95": 10.5, "max": 10.6, "samples": [10.0, 10.6]},
                "node_ms": {"p50": 90.0, "p95": 99.0, "max": 100.0, "samples": [90.0, 99.0]}
              }
            }
            """;

    private static final String LARGE_INPUT = """
            {
              "rows": 1000000,
              "sdk": {"node_ms": {"p50": 4000.0, "p95": 4100.0, "max": 4100.0, "samples": []},
                      "rss_mib": {"p50": 540.0, "p95": 550.0, "max": 550.0, "samples": []}},
              "raw_map": {"node_ms": {"p50": 3500.0, "p95": 3600.0, "max": 3600.0, "samples": []},
                          "rss_mib": {"p50": 490.0, "p95": 500.0, "max": 500.0, "samples": []}},
              "python": {"node_ms": {"p50": 5000.0, "p95": 5100.0, "max": 5100.0, "samples": []},
                         "rss_mib": {"p50": 790.0, "p95": 800.0, "max": 800.0, "samples": []}}
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
    void writesEveryMeasureNextToItsTargetWithTheJvmAgainstPythonAndEachSdkPathAgainstItsBaseline() {
        assertThat(Report.markdown(json(COLD_START), json(LARGE_INPUT), json(JMH))).isEqualTo("""
                ### Benchmarks

                Reporting only, nothing here fails the build. The task JVM ran on Java 17.0.20.

                | Measure | Result | Target |
                |---|---|---|
                | JVM cold start of a no-op task, p50 of 30 | 81.3 ms | under 100 ms |
                | JVM cold start of a no-op task, p95 of 30 | 92.0 ms | under 150 ms |
                | Python cold start of a no-op task, p50 of 30 | 50.0 ms |  |
                | JVM cold start less Python's, p50 | +31.3 ms | under 50 ms |
                | JVM first attempt, with the harness compiled | 1840.0 ms | under 2500 ms |
                | Python first attempt | 95.0 ms |  |
                | JVM peak RSS of a no-op task, highest of 30 | 61.2 MiB (p50 60.8 MiB) | under 96 MiB |
                | Python peak RSS of a no-op task, highest of 30 | 10.6 MiB (p50 10.0 MiB) |  |
                | Task node duration in core, p50, JVM and Python | 120.0 ms and 90.0 ms |  |
                | 1000000 rows, JVM SDK task, p50 of 3 | 4000 ms, 250000 rows/s, Python 5000 ms, 200000 rows/s \
                | at least the Python task |
                | 1000000 rows, raw `Map` JVM task, p50 of 3 | 3500 ms, 285714 rows/s |  |
                | 1000000 rows, Python task, p50 of 3 | 5000 ms, 200000 rows/s |  |
                | 1000000 rows, peak RSS of the JVM SDK task, highest of 3 | 550.0 MiB, +10.0% over the raw `Map` \
                task | at most 10% over the raw `Map` task |
                | 1000000 rows, peak RSS of the raw `Map` JVM task, highest of 3 | 500.0 MiB |  |
                | 1000000 rows, peak RSS of the Python task, highest of 3 | 800.0 MiB |  |
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

        assertThat(Report.markdown(json(COLD_START), json(LARGE_INPUT), json(withoutRawMap)))
                .endsWith("| JMH, 10 rows through SDK rows, `Rows` | 3.00 ± 0.20 ms/op |  |\n");
    }

    @Test
    void leavesOutTheErrorWhenJmhHadTooFewIterationsToEstimateIt() {
        String withoutError = """
                [{"benchmark": "dev.brokoli.benchmarks.RowsBenchmark.rawMap", "params": {"rows": "10"},
                  "primaryMetric": {"score": 2.0, "scoreError": "NaN", "scoreUnit": "ms/op"}}]
                """;

        assertThat(Report.markdown(json(COLD_START), json(LARGE_INPUT), json(withoutError)))
                .endsWith("| JMH, 10 rows through a raw `Map`, reading `paid` only | 2.00 ms/op |  |\n");
    }

    private static JsonNode json(String text) {
        return JSON.readTree(text);
    }
}
