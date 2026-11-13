package dev.brokoli.benchmarks;

import java.util.LinkedHashMap;
import java.util.Map;

final class Summary {

    static final String P50 = "p50";
    static final String P95 = "p95";
    static final String MAX = "max";
    static final String SAMPLES = "samples";

    private static final int PERCENTILE_50 = 50;
    private static final int PERCENTILE_95 = 95;

    private Summary() {
    }

    static Map<String, Object> of(double[] samples) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put(P50, Percentiles.nearestRank(samples, PERCENTILE_50));
        summary.put(P95, Percentiles.nearestRank(samples, PERCENTILE_95));
        summary.put(MAX, Percentiles.max(samples));
        summary.put(SAMPLES, samples);
        return summary;
    }
}
