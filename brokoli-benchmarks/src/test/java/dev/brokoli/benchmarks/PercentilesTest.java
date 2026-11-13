package dev.brokoli.benchmarks;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PercentilesTest {

    private static final double[] ONE_TO_THIRTY = IntStream.rangeClosed(1, 30).asDoubleStream().toArray();

    @Test
    void theMedianOfThirtySamplesIsTheFifteenthSmallest() {
        assertThat(Percentiles.nearestRank(ONE_TO_THIRTY, 50)).isEqualTo(15);
    }

    @Test
    void theNinetyFifthPercentileOfThirtySamplesIsTheTwentyNinthSmallest() {
        assertThat(Percentiles.nearestRank(ONE_TO_THIRTY, 95)).isEqualTo(29);
    }

    @Test
    void theSamplesNeedNotBeSortedAndAreLeftAsTheyWere() {
        double[] samples = {30, 10, 20};

        assertThat(Percentiles.nearestRank(samples, 50)).isEqualTo(20);
        assertThat(samples).containsExactly(30, 10, 20);
    }

    @Test
    void theMaximumIsTheLargestSample() {
        assertThat(Percentiles.max(new double[] {3, 9, 4})).isEqualTo(9);
    }

    @Test
    void refusesNoSamples() {
        assertThatThrownBy(() -> Percentiles.nearestRank(new double[0], 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("no samples for the 50th percentile");
    }

    @Test
    void refusesAPercentileOutsideOneToAHundred() {
        assertThatThrownBy(() -> Percentiles.nearestRank(ONE_TO_THIRTY, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("percentile 0 is not from 1 to 100");
        assertThat(Percentiles.nearestRank(ONE_TO_THIRTY, 1)).isEqualTo(1);
    }
}
