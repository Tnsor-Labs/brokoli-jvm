package dev.brokoli.task.processor;

import javax.lang.model.element.Element;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

final class DefaultReader {

    private static final Pattern INT64 = Pattern.compile("0|-?[1-9][0-9]*");
    private static final Pattern NUMBER = Pattern.compile("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?");
    private static final Pattern DECIMAL = Pattern.compile("-?(0|[1-9][0-9]*)(\\.[0-9]+)?");
    private static final Pattern DATE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
    private static final Pattern FOUR_DIGIT_YEAR = Pattern.compile("[0-9]{4}-.*");
    private static final String TRUE = "true";
    private static final String FALSE = "false";
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final String BOXED_INT = "java.lang.Integer";
    private static final String BOXED_FLOAT = "java.lang.Float";

    private DefaultReader() {
    }

    static Object read(String text, Map<String, Object> type, TypeMirror javaType, String context, Element at) {
        String kind = (String) type.get(Bptd.KIND);
        return switch (kind) {
            case Bptd.INT64 -> readInt64(text, javaType, context, at);
            case Bptd.FLOAT64 -> readFloat64(text, javaType, context, at);
            case Bptd.BOOLEAN -> readBoolean(text, context, at);
            case Bptd.STRING -> text;
            case Bptd.ENUM -> readEnum(text, type, context, at);
            case Bptd.DECIMAL -> tagged(Bptd.DECIMAL, readDecimal(text, context, at));
            case Bptd.DATE -> readDate(text, context, at);
            case Bptd.TIMESTAMP -> tagged(Bptd.TIMESTAMP, readTimestamp(text, context, at));
            case Bptd.DURATION -> readDuration(text, context, at);
            case Bptd.BYTES -> tagged(Bptd.BYTES, readBytes(text, context, at));
            default -> throw new InvalidTaskException("@Default is not supported on " + context
                    + ", whose type is " + kind, at);
        };
    }

    private static Long readInt64(String text, TypeMirror javaType, String context, Element at) {
        if (!INT64.matcher(text).matches()) {
            throw refused(text, context, "a whole number such as 42", at);
        }
        long value;
        try {
            value = Long.parseLong(text);
        } catch (NumberFormatException tooLarge) {
            throw refused(text, context, "a whole number that fits in 64 bits", at);
        }
        if (isInt(javaType) && (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)) {
            throw refused(text, context, "a whole number that fits in an int", at);
        }
        return value;
    }

    private static Double readFloat64(String text, TypeMirror javaType, String context, Element at) {
        if (!NUMBER.matcher(text).matches()) {
            throw refused(text, context, "a number such as 0.5", at);
        }
        double value = Double.parseDouble(text);
        boolean finite = isFloat(javaType) ? Float.isFinite((float) value) : Double.isFinite(value);
        if (!finite) {
            throw refused(text, context, "a finite number", at);
        }
        return value == 0 ? 0.0 : value;
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

    private static String readDecimal(String text, String context, Element at) {
        if (!DECIMAL.matcher(text).matches()) {
            throw refused(text, context, "a decimal such as 12.50, with no exponent", at);
        }
        boolean negativeZero = text.startsWith("-") && new BigDecimal(text).signum() == 0;
        return negativeZero ? text.substring(1) : text;
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

    private static String readTimestamp(String text, String context, Element at) {
        String canonical;
        try {
            canonical = Instant.parse(text).toString();
        } catch (DateTimeException notATimestamp) {
            throw refused(text, context, "a UTC timestamp such as 2026-01-31T12:00:00Z", at);
        }
        if (!FOUR_DIGIT_YEAR.matcher(canonical).matches()) {
            throw refused(text, context, "a timestamp with a four-digit year", at);
        }
        return canonical;
    }

    private static Map<String, Object> readDuration(String text, String context, Element at) {
        Duration duration;
        try {
            duration = Duration.parse(text);
        } catch (DateTimeException notADuration) {
            throw refused(text, context, "an ISO-8601 duration such as PT1H30M", at);
        }
        long seconds = duration.getSeconds();
        long nanos = duration.getNano();
        if (seconds < 0 && nanos > 0) {
            seconds += 1;
            nanos -= NANOS_PER_SECOND;
        }
        Map<String, Object> value = new LinkedHashMap<>();
        value.put(Bptd.TAG, Bptd.DURATION);
        value.put(Bptd.SECONDS, Long.toString(seconds));
        value.put(Bptd.NANOS, nanos);
        return value;
    }

    private static String readBytes(String text, String context, Element at) {
        if (isPaddedBase64(text)) {
            return text;
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

    private static Map<String, Object> tagged(String kind, String value) {
        Map<String, Object> tagged = new LinkedHashMap<>();
        tagged.put(Bptd.TAG, kind);
        tagged.put(Bptd.VALUE, value);
        return tagged;
    }

    private static boolean isInt(TypeMirror type) {
        return type.getKind() == TypeKind.INT || TypeMapper.isDeclared(type, BOXED_INT);
    }

    private static boolean isFloat(TypeMirror type) {
        return type.getKind() == TypeKind.FLOAT || TypeMapper.isDeclared(type, BOXED_FLOAT);
    }

    private static InvalidTaskException refused(String text, String context, String expected, Element at) {
        return new InvalidTaskException("@Default(\"" + text + "\") on " + context + " is not " + expected, at);
    }
}
