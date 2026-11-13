package dev.brokoli.benchmarks;

import java.util.Arrays;

final class Percentiles {

    private static final int HUNDRED = 100;

    private Percentiles() {
    }

    static double nearestRank(double[] samples, int percentile) {
        if (samples.length == 0) {
            throw new IllegalArgumentException("no samples for the " + percentile + "th percentile");
        }
        if (percentile < 1 || percentile > HUNDRED) {
            throw new IllegalArgumentException("percentile " + percentile + " is not from 1 to 100");
        }
        double[] sorted = samples.clone();
        Arrays.sort(sorted);
        int rank = (int) Math.ceil(percentile / (double) HUNDRED * sorted.length);
        return sorted[rank - 1];
    }

    static double max(double[] samples) {
        return nearestRank(samples, HUNDRED);
    }
}
