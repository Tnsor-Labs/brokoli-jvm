package dev.brokoli.task.processor;

import dev.brokoli.task.internal.TaskValueException;
import dev.brokoli.task.internal.TaskValues;

import javax.lang.model.element.Element;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.regex.Pattern;

final class DefaultReader {

    private static final Pattern INT64 = Pattern.compile("0|-?[1-9][0-9]*");
    private static final Pattern NUMBER = Pattern.compile("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?");
    private static final Pattern DECIMAL = Pattern.compile("-?(0|[1-9][0-9]*)(\\.[0-9]+)?");
    private static final Pattern DATE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
    private static final String TRUE = "true";
    private static final String FALSE = "false";

    private DefaultReader() {
    }

    static Object read(String text, Map<String, Object> type, String context, Element at) {
        OptionalInt surrogate = UnicodeText.unpairedSurrogate(text);
        if (surrogate.isPresent()) {
            throw new InvalidTaskException("@Default on " + context + " has an unpaired surrogate at index "
                    + surrogate.getAsInt() + "; Brokoli text must be valid Unicode", at);
        }
        String kind = (String) type.get(Bptd.KIND);
        return switch (kind) {
            case Bptd.INT64 -> readInt64(text, type, context, at);
            case Bptd.FLOAT64 -> readFloat64(text, type, context, at);
            case Bptd.BOOLEAN -> readBoolean(text, context, at);
            case Bptd.STRING -> text;
            case Bptd.ENUM -> readEnum(text, type, context, at);
            case Bptd.DECIMAL -> readDecimal(text, context, at);
            case Bptd.DATE -> readDate(text, context, at);
            case Bptd.TIMESTAMP -> readTimestamp(text, context, at);
            case Bptd.DURATION -> readDuration(text, context, at);
            case Bptd.BYTES -> readBytes(text, context, at);
            default -> throw new InvalidTaskException("@Default is not supported on " + context
                    + ", whose type is " + kind, at);
        };
    }

    private static Long readInt64(String text, Map<String, Object> type, String context, Element at) {
        if (!INT64.matcher(text).matches()) {
            throw refused(text, context, "a whole number such as 42", at);
        }
        long value;
        try {
            value = Long.parseLong(text);
        } catch (NumberFormatException tooLarge) {
            throw refused(text, context, "a whole number that fits in 64 bits", at);
        }
        String minimum = (String) type.get(Bptd.MINIMUM);
        String maximum = (String) type.get(Bptd.MAXIMUM);
        boolean below = minimum != null && value < Long.parseLong(minimum);
        boolean above = maximum != null && value > Long.parseLong(maximum);
        if (below || above) {
            throw refused(text, context, range("a whole number", minimum, maximum), at);
        }
        return value;
    }

    private static Double readFloat64(String text, Map<String, Object> type, String context, Element at) {
        if (!NUMBER.matcher(text).matches()) {
            throw refused(text, context, "a number such as 0.5", at);
        }
        double value = Double.parseDouble(text);
        if (!Double.isFinite(value)) {
            throw refused(text, context, "a finite number", at);
        }
        Double minimum = (Double) type.get(Bptd.MINIMUM);
        Double maximum = (Double) type.get(Bptd.MAXIMUM);
        boolean below = minimum != null && value < minimum;
        boolean above = maximum != null && value > maximum;
        if (below || above) {
            throw refused(text, context, range("a number", minimum, maximum), at);
        }
        return value == 0 ? 0.0 : value;
    }

    private static String range(String what, Object minimum, Object maximum) {
        return what + " from " + minimum + " to " + maximum;
    }

    private static Boolean readBoolean(String text, String context, Element at) {
        if (text.equals(TRUE)) {
            return Boolean.TRUE;
        }
        if (text.equals(FALSE)) {
            return Boolean.FALSE;
        }
        throw refused(text, context, "true or false", at);
    }

    private static String readEnum(String text, Map<String, Object> type, String context, Element at) {
        List<?> values = (List<?>) type.get(Bptd.VALUES);
        if (values.contains(text)) {
            return text;
        }
        throw refused(text, context, "one of " + values, at);
    }

    private static Object readDecimal(String text, String context, Element at) {
        if (!DECIMAL.matcher(text).matches()) {
            throw refused(text, context, "a decimal such as 12.50, with no exponent", at);
        }
        return TaskValues.writeDecimal(new BigDecimal(text));
    }

    private static String readDate(String text, String context, Element at) {
        if (isDate(text)) {
            return text;
        }
        throw refused(text, context, "a date such as 2026-01-31", at);
    }

    private static boolean isDate(String text) {
        if (!DATE.matcher(text).matches()) {
            return false;
        }
        try {
            LocalDate.parse(text);
            return true;
        } catch (DateTimeException notADate) {
            return false;
        }
    }

    private static Object readTimestamp(String text, String context, Element at) {
        Instant instant;
        try {
            instant = Instant.parse(text);
        } catch (DateTimeException notATimestamp) {
            throw refused(text, context, "a UTC timestamp such as 2026-01-31T12:00:00Z", at);
        }
        try {
            return TaskValues.writeTimestamp(instant);
        } catch (TaskValueException notFourDigits) {
            throw refused(text, context, "a timestamp with a four-digit year", at);
        }
    }

    private static Object readDuration(String text, String context, Element at) {
        try {
            return TaskValues.writeDuration(Duration.parse(text));
        } catch (DateTimeException notADuration) {
            throw refused(text, context, "an ISO-8601 duration such as PT1H30M", at);
        }
    }

    private static Object readBytes(String text, String context, Element at) {
        if (isPaddedBase64(text)) {
            return TaskValues.writeBytes(Base64.getDecoder().decode(text));
        }
        throw refused(text, context, "padded standard base64", at);
    }

    private static boolean isPaddedBase64(String text) {
        try {
            return Base64.getEncoder().encodeToString(Base64.getDecoder().decode(text)).equals(text);
        } catch (IllegalArgumentException notBase64) {
            return false;
        }
    }

    private static InvalidTaskException refused(String text, String context, String expected, Element at) {
        return new InvalidTaskException("@Default(\"" + text + "\") on " + context + " is not " + expected, at);
    }
}
