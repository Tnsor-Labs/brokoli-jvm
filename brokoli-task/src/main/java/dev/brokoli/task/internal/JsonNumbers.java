package dev.brokoli.task.internal;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Writes a {@code double} as JSON text the same way on every JDK: the shortest decimal that reads
 * back as the same {@code double}, laid out by ECMAScript's Number-to-string rule, as RFC 8785
 * section 3.2.2.3 requires. This is the text JavaScript's {@code JSON.stringify} and Go's
 * {@code encoding/json} write: {@code 1e+23}, {@code 0.000001}, {@code 1e-7}, {@code 5e-324}. Used
 * by the task processor and the bundle tooling; not part of the task API.
 *
 * <p>From JDK 19 on, the digits come from {@link Double#toString(double)}, which is shortest there.
 * On JDK 17 and 18, which do not always give the shortest digits, they are found exactly with
 * {@link BigDecimal}.
 */
public final class JsonNumbers {

    private static final int FIRST_JDK_WITH_SHORTEST_TO_STRING = 19;
    private static final boolean SHORTEST_TO_STRING =
            Runtime.version().feature() >= FIRST_JDK_WITH_SHORTEST_TO_STRING;
    private static final int MOST_DIGITS_A_DOUBLE_NEEDS = 17;
    private static final int LARGEST_PLAIN_POINT = 21;
    private static final int SMALLEST_PLAIN_POINT = -6;
    private static final char EXPONENT = 'E';
    private static final char POINT = '.';
    private static final String ZERO = "0";

    private JsonNumbers() {
    }

    /**
     * Writes the number as JSON text.
     *
     * @param value the number
     * @return its JSON text; {@code -0.0} is written as {@code 0}
     * @throws IllegalArgumentException if the number is NaN or infinite, which JSON cannot hold
     */
    public static String format(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("JSON has no form for " + value);
        }
        if (value == 0) {
            return ZERO;
        }
        if (value < 0) {
            return "-" + format(-value);
        }
        return layout(SHORTEST_TO_STRING ? fromToString(value) : shortestExactly(value));
    }

    static Decimal fromToString(double value) {
        Decimal jdk = parse(Double.toString(value));
        if (jdk.digits().length() == 2) {
            Decimal oneDigit = closestWithDigits(new BigDecimal(value), value, 1);
            if (oneDigit != null) {
                return oneDigit;
            }
        }
        return jdk;
    }

    static Decimal shortestExactly(double value) {
        BigDecimal exact = new BigDecimal(value);
        for (int digits = 1; digits <= MOST_DIGITS_A_DOUBLE_NEEDS; digits++) {
            Decimal closest = closestWithDigits(exact, value, digits);
            if (closest != null) {
                return closest;
            }
        }
        throw new IllegalStateException(value + " has no decimal of up to 17 digits that reads back as itself");
    }

    private static Decimal closestWithDigits(BigDecimal exact, double value, int digits) {
        BigDecimal down = exact.round(new MathContext(digits, RoundingMode.DOWN));
        BigDecimal up = exact.round(new MathContext(digits, RoundingMode.UP));
        boolean downReadsBack = readsBack(down, value);
        boolean upReadsBack = readsBack(up, value);
        if (downReadsBack && upReadsBack) {
            int closer = exact.subtract(down).compareTo(up.subtract(exact));
            if (closer != 0) {
                return Decimal.of(closer < 0 ? down : up);
            }
            return Decimal.of(down.unscaledValue().testBit(0) ? up : down);
        }
        if (downReadsBack) {
            return Decimal.of(down);
        }
        return upReadsBack ? Decimal.of(up) : null;
    }

    private static boolean readsBack(BigDecimal decimal, double value) {
        return Double.parseDouble(decimal.toString()) == value;
    }

    private static Decimal parse(String text) {
        int exponentAt = text.indexOf(EXPONENT);
        String mantissa = exponentAt < 0 ? text : text.substring(0, exponentAt);
        int exponent = exponentAt < 0 ? 0 : Integer.parseInt(text.substring(exponentAt + 1));
        int pointAt = mantissa.indexOf(POINT);
        String digits = mantissa.substring(0, pointAt) + mantissa.substring(pointAt + 1);
        return Decimal.of(new BigDecimal(digits).scaleByPowerOfTen(exponent - (mantissa.length() - pointAt - 1)));
    }

    private static String layout(Decimal decimal) {
        String digits = decimal.digits();
        int count = digits.length();
        int point = decimal.point();
        if (count <= point && point <= LARGEST_PLAIN_POINT) {
            return digits + ZERO.repeat(point - count);
        }
        if (0 < point && point <= LARGEST_PLAIN_POINT) {
            return digits.substring(0, point) + POINT + digits.substring(point);
        }
        if (SMALLEST_PLAIN_POINT < point && point <= 0) {
            return ZERO + POINT + ZERO.repeat(-point) + digits;
        }
        int exponent = point - 1;
        String mantissa = count == 1 ? digits : digits.charAt(0) + "." + digits.substring(1);
        return mantissa + "e" + (exponent < 0 ? "-" : "+") + Math.abs(exponent);
    }

    record Decimal(String digits, int point) {

        static Decimal of(BigDecimal value) {
            BigDecimal stripped = value.stripTrailingZeros();
            String digits = stripped.unscaledValue().toString();
            return new Decimal(digits, digits.length() - stripped.scale());
        }
    }
}
