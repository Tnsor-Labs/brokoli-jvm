package dev.brokoli.task.internal;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class JsonNumbersTest {

    private static final int RANDOM_DOUBLES = 50_000;
    private static final long SEED = 20261110L;

    @Test
    void writesRfc8785AppendixBSamples() {
        Map<String, String> samples = Map.ofEntries(
                Map.entry("0000000000000000", "0"),
                Map.entry("8000000000000000", "0"),
                Map.entry("0000000000000001", "5e-324"),
                Map.entry("8000000000000001", "-5e-324"),
                Map.entry("7fefffffffffffff", "1.7976931348623157e+308"),
                Map.entry("ffefffffffffffff", "-1.7976931348623157e+308"),
                Map.entry("4340000000000000", "9007199254740992"),
                Map.entry("c340000000000000", "-9007199254740992"),
                Map.entry("4430000000000000", "295147905179352830000"),
                Map.entry("44b52d02c7e14af5", "9.999999999999997e+22"),
                Map.entry("44b52d02c7e14af6", "1e+23"),
                Map.entry("44b52d02c7e14af7", "1.0000000000000001e+23"),
                Map.entry("444b1ae4d6e2ef4e", "999999999999999700000"),
                Map.entry("444b1ae4d6e2ef4f", "999999999999999900000"),
                Map.entry("444b1ae4d6e2ef50", "1e+21"),
                Map.entry("3eb0c6f7a0b5ed8c", "9.999999999999997e-7"),
                Map.entry("3eb0c6f7a0b5ed8d", "0.000001"),
                Map.entry("41b3de4355555553", "333333333.3333332"),
                Map.entry("41b3de4355555554", "333333333.33333325"),
                Map.entry("41b3de4355555555", "333333333.3333333"),
                Map.entry("41b3de4355555556", "333333333.3333334"),
                Map.entry("41b3de4355555557", "333333333.33333343"),
                Map.entry("becbf647612f3696", "-0.0000033333333333333333"),
                Map.entry("43143ff3c1cb0959", "1424953923781206.2"));

        samples.forEach((bits, expected) -> assertThat(JsonNumbers.format(fromHex(bits)))
                .as("IEEE 754 %s", bits)
                .isEqualTo(expected));
    }

    @Test
    void writesWhatGoAndJavaScriptWrite() {
        assertThat(JsonNumbers.format(1e-5)).isEqualTo("0.00001");
        assertThat(JsonNumbers.format(1e-7)).isEqualTo("1e-7");
        assertThat(JsonNumbers.format(0.5)).isEqualTo("0.5");
        assertThat(JsonNumbers.format(100.0)).isEqualTo("100");
        assertThat(JsonNumbers.format(3.4028234663852886e38)).isEqualTo("3.4028234663852886e+38");
        assertThat(JsonNumbers.format(1.2345678901234568e20)).isEqualTo("123456789012345680000");
    }

    @Test
    void refusesNaNAndInfinity() {
        assertThatThrownBy(() -> JsonNumbers.format(Double.NaN)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("JSON has no form for NaN");
        assertThatThrownBy(() -> JsonNumbers.format(Double.NEGATIVE_INFINITY))
                .hasMessage("JSON has no form for -Infinity");
    }

    @Test
    void everyNumberWrittenReadsBackAsTheSameDouble() {
        SplittableRandom random = new SplittableRandom(SEED);
        for (int i = 0; i < RANDOM_DOUBLES; i++) {
            double value = randomFinite(random);
            assertThat(Double.parseDouble(JsonNumbers.format(value))).as("%s", value)
                    .isEqualTo(value == 0 ? 0.0 : value);
        }
    }

    @Test
    void theJdkDigitsAndTheExactSearchAgreeOnRandomDoubles() {
        assumeTrue(Runtime.version().feature() >= 19, "Double.toString is shortest from JDK 19 on");
        SplittableRandom random = new SplittableRandom(SEED);
        for (int i = 0; i < RANDOM_DOUBLES; i++) {
            double value = Math.abs(randomFinite(random));
            if (value != 0) {
                assertThat(JsonNumbers.fromToString(value)).as("%s", value)
                        .isEqualTo(JsonNumbers.shortestExactly(value));
            }
        }
    }

    private static double randomFinite(SplittableRandom random) {
        double value;
        do {
            value = Double.longBitsToDouble(random.nextLong());
        } while (!Double.isFinite(value));
        return value;
    }

    private static double fromHex(String bits) {
        return Double.longBitsToDouble(Long.parseUnsignedLong(bits, 16));
    }
}
