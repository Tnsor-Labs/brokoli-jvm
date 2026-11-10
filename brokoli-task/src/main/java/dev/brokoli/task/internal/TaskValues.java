package dev.brokoli.task.internal;

import dev.brokoli.task.Rows;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Converts between the values the harness passes to a task, decoded from JSON, and the Java types a
 * {@code @Task} method declares, in both directions. Used by the code the task processor generates;
 * not part of the task API, and it may change in any release.
 *
 * <p>Values follow core's task interface: a {@code decimal}, {@code timestamp}, {@code duration} or
 * {@code bytes} value is tagged, as in {@code {"$bptd": "decimal", "value": "12.50"}}, a
 * {@code date} is {@code YYYY-MM-DD} text, and an {@code int64} is a whole number or a tagged one.
 * Every read refuses a value of another form with a {@link TaskValueException} that names where the
 * value is, such as {@code parameter threshold} or {@code input[3].amount}. Every write turns null
 * into null, so a null value reaches core's own validation of the declared type.
 */
public final class TaskValues {

    private static final String INPUT = "input";
    private static final String PARAMETER = "parameter ";
    private static final String RESULT = "result";
    private static final String TAG = "$bptd";
    private static final String VALUE = "value";
    private static final String SECONDS = "seconds";
    private static final String NANOS = "nanos";
    private static final String INT64 = "int64";
    private static final String DECIMAL = "decimal";
    private static final String TIMESTAMP = "timestamp";
    private static final String DURATION = "duration";
    private static final String BYTES = "bytes";

    private static final String MISSING = "missing";
    private static final String NULL = "null";
    private static final String A_VALUE = "a value";
    private static final String A_RUN_PARAMETER = "a value in the run's parameters";
    private static final String WHOLE_NUMBER = "a whole number";
    private static final String INT_RANGE = "a whole number from -2147483648 to 2147483647";
    private static final String NUMBER = "a number";
    private static final String FLOAT_RANGE = "a number from -3.4028234663852886E38 to 3.4028234663852886E38";
    private static final String INEXACT_LONG = "a Long that a double cannot hold exactly";
    private static final String BOOLEAN = "a boolean";
    private static final String STRING = "a string";
    private static final String DECIMAL_VALUE =
            "a tagged decimal, such as {\"$bptd\": \"decimal\", \"value\": \"12.50\"}";
    private static final String DATE_VALUE = "a date such as 2026-01-31";
    private static final String TIMESTAMP_VALUE =
            "a tagged UTC timestamp, such as {\"$bptd\": \"timestamp\", \"value\": \"2026-01-31T12:00:00Z\"}";
    private static final String DURATION_VALUE =
            "a tagged duration, such as {\"$bptd\": \"duration\", \"seconds\": \"90\", \"nanos\": 0}";
    private static final String BYTES_VALUE = "tagged bytes, such as {\"$bptd\": \"bytes\", \"value\": \"SGVsbG8=\"}";
    private static final String FOUR_DIGIT_YEAR = "a value with a year from 0000 to 9999";
    private static final String OBJECT = "an object";
    private static final String LIST = "a list";
    private static final String ROW = "a row object";

    private static final Pattern CANONICAL_INT64 = Pattern.compile("0|-?[1-9][0-9]*");
    private static final Pattern CANONICAL_DECIMAL = Pattern.compile("-?(0|[1-9][0-9]*)(\\.[0-9]+)?");
    private static final Pattern DATE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
    private static final Pattern STARTS_WITH_FOUR_DIGIT_YEAR = Pattern.compile("[0-9]{4}-.*");
    private static final String UTC = "Z";
    private static final double FIRST_DOUBLE_BEYOND_LONG = 0x1p63;
    private static final long MAX_NANOS = 999_999_999L;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private TaskValues() {
    }

    /**
     * Reads the task's input rows, which the harness passes under {@code input}. When the node
     * received no rows, the harness passes none, and they are read as an empty list.
     *
     * @param kwargs the map the harness passes to the entry method
     * @param reader reads the rows
     * @param <T> the type the rows are read as
     * @return the rows
     * @throws TaskValueException if the rows do not have the form the reader needs
     */
    public static <T> T input(Map<String, Object> kwargs, ValueReader<T> reader) {
        Object rows = kwargs.get(INPUT);
        try {
            return reader.read(rows == null ? List.of() : rows);
        } catch (TaskValueException refused) {
            throw refused.under(INPUT);
        }
    }

    /**
     * Reads one of the task's parameters from the run's parameters.
     *
     * @param kwargs the map the harness passes to the entry method
     * @param name the parameter name
     * @param reader reads the value
     * @param <T> the parameter's type
     * @return the value
     * @throws TaskValueException if the run did not pass the parameter, or its value does not have
     *         the form the reader needs
     */
    public static <T> T parameter(Map<String, Object> kwargs, String name, ValueReader<T> reader) {
        if (!kwargs.containsKey(name)) {
            throw new TaskValueException(PARAMETER + name, A_RUN_PARAMETER, MISSING);
        }
        try {
            return reader.read(kwargs.get(name));
        } catch (TaskValueException refused) {
            throw refused.under(PARAMETER + name);
        }
    }

    /**
     * Reads a record's fields.
     *
     * @param value the decoded value
     * @return the fields, by name
     * @throws TaskValueException if the value is not an object
     */
    public static Map<?, ?> readRecord(Object value) {
        if (value instanceof Map<?, ?> fields) {
            return fields;
        }
        throw refused(value, OBJECT);
    }

    /**
     * Reads one field of a record.
     *
     * @param fields the record's fields, from {@link #readRecord(Object)}
     * @param name the field name
     * @param reader reads the field's value
     * @param <T> the field's type
     * @return the value
     * @throws TaskValueException if the field is missing, or its value does not have the form the
     *         reader needs
     */
    public static <T> T field(Map<?, ?> fields, String name, ValueReader<T> reader) {
        if (!fields.containsKey(name)) {
            throw new TaskValueException("." + name, A_VALUE, MISSING);
        }
        try {
            return reader.read(fields.get(name));
        } catch (TaskValueException refused) {
            throw refused.under("." + name);
        }
    }

    /**
     * Reads a list, item by item.
     *
     * @param value the decoded value
     * @param reader reads each item
     * @param <T> the item type
     * @return an unmodifiable list of the items
     * @throws TaskValueException if the value is not a list, or an item does not have the form the
     *         reader needs
     */
    public static <T> List<T> readList(Object value, ValueReader<T> reader) {
        if (!(value instanceof List<?> items)) {
            throw refused(value, LIST);
        }
        List<T> read = new ArrayList<>(items.size());
        int index = 0;
        for (Object item : items) {
            try {
                read.add(reader.read(item));
            } catch (TaskValueException refused) {
                throw refused.under("[" + index + "]");
            }
            index++;
        }
        return Collections.unmodifiableList(read);
    }

    /**
     * Reads a map with text keys, value by value, keeping the order of its keys.
     *
     * @param value the decoded value
     * @param reader reads each value
     * @param <T> the value type
     * @return an unmodifiable map of the values
     * @throws TaskValueException if the value is not an object, or one of its values does not have
     *         the form the reader needs
     */
    public static <T> Map<String, T> readMap(Object value, ValueReader<T> reader) {
        if (!(value instanceof Map<?, ?> entries)) {
            throw refused(value, OBJECT);
        }
        Map<String, T> read = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : entries.entrySet()) {
            String key = String.valueOf(entry.getKey());
            try {
                read.put(key, reader.read(entry.getValue()));
            } catch (TaskValueException refused) {
                throw refused.under("[\"" + key + "\"]");
            }
        }
        return Collections.unmodifiableMap(read);
    }

    /**
     * Reads a list of rows whose schema the task does not declare.
     *
     * @param value the decoded value
     * @return the rows
     * @throws TaskValueException if the value is not a list of objects
     */
    public static Rows readRows(Object value) {
        if (!(value instanceof List<?> items)) {
            throw refused(value, LIST);
        }
        List<Map<String, Object>> rows = new ArrayList<>(items.size());
        int index = 0;
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> row)) {
                throw refused(item, ROW).under("[" + index + "]");
            }
            Map<String, Object> columns = new LinkedHashMap<>();
            row.forEach((column, columnValue) -> columns.put(String.valueOf(column), columnValue));
            rows.add(columns);
            index++;
        }
        return Rows.fromMaps(rows);
    }

    /**
     * Reads a value that may be null, with the reader its type needs when it is not.
     *
     * @param value the decoded value
     * @param reader reads a value that is not null
     * @param <T> the type read
     * @return the value, or null
     * @throws TaskValueException if the value is not null and does not have the form the reader
     *         needs
     */
    public static <T> T nullable(Object value, ValueReader<T> reader) {
        return value == null ? null : reader.read(value);
    }

    /**
     * Reads an {@code int64}: a whole number, or a tagged one such as
     * {@code {"$bptd": "int64", "value": "9223372036854775807"}}.
     *
     * @param value the decoded value
     * @return the number
     * @throws TaskValueException if the value is null, not a whole number, or beyond 64 bits
     */
    public static Long readLong(Object value) {
        if (value instanceof Long whole) {
            return whole;
        }
        if (value instanceof Integer small) {
            return small.longValue();
        }
        if (value instanceof Double number && isWhole(number)) {
            return number.longValue();
        }
        String text = tagged(value, INT64, VALUE);
        if (text != null && CANONICAL_INT64.matcher(text).matches()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException beyondLong) {
                throw new TaskValueException("", WHOLE_NUMBER, "the tagged int64 " + text + ", beyond 64 bits");
            }
        }
        throw refused(value, WHOLE_NUMBER);
    }

    /**
     * Reads an {@code int64} that must fit in an {@code int}.
     *
     * @param value the decoded value
     * @return the number
     * @throws TaskValueException if the value is not a whole number, or does not fit in an
     *         {@code int}
     */
    public static Integer readInt(Object value) {
        long whole = readLong(value);
        if (whole < Integer.MIN_VALUE || whole > Integer.MAX_VALUE) {
            throw new TaskValueException("", INT_RANGE, Long.toString(whole));
        }
        return (int) whole;
    }

    /**
     * Reads a {@code float64}. A whole number is accepted, because JSON decodes {@code 5} as a
     * {@code Long}, unless a {@code double} cannot hold it exactly.
     *
     * @param value the decoded value
     * @return the number
     * @throws TaskValueException if the value is null, not a number, or a {@code Long} a
     *         {@code double} cannot hold exactly
     */
    public static Double readDouble(Object value) {
        if (value instanceof Double number) {
            return number;
        }
        if (value instanceof Long whole) {
            double number = whole;
            if (number < FIRST_DOUBLE_BEYOND_LONG && (long) number == whole) {
                return number;
            }
            throw new TaskValueException("", NUMBER, INEXACT_LONG);
        }
        if (value instanceof Integer || value instanceof Float) {
            return ((Number) value).doubleValue();
        }
        throw refused(value, NUMBER);
    }

    /**
     * Reads a {@code float64} that must fit in a {@code float}.
     *
     * @param value the decoded value
     * @return the number, rounded to the nearest {@code float}
     * @throws TaskValueException if the value is not a number, or is beyond the range of a
     *         {@code float}
     */
    public static Float readFloat(Object value) {
        double number = readDouble(value);
        if (Math.abs(number) > Float.MAX_VALUE) {
            throw new TaskValueException("", FLOAT_RANGE, Double.toString(number));
        }
        return (float) number;
    }

    /**
     * Reads a {@code boolean}.
     *
     * @param value the decoded value
     * @return the flag
     * @throws TaskValueException if the value is null or not a boolean
     */
    public static Boolean readBoolean(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        throw refused(value, BOOLEAN);
    }

    /**
     * Reads a {@code string}.
     *
     * @param value the decoded value
     * @return the text
     * @throws TaskValueException if the value is null or not text
     */
    public static String readString(Object value) {
        if (value instanceof String text) {
            return text;
        }
        throw refused(value, STRING);
    }

    /**
     * Reads a tagged {@code decimal}, keeping its scale: {@code "12.50"} is read with two digits
     * after the point.
     *
     * @param value the decoded value
     * @return the number
     * @throws TaskValueException if the value is not a tagged decimal in canonical form
     */
    public static BigDecimal readDecimal(Object value) {
        String text = tagged(value, DECIMAL, VALUE);
        if (text == null) {
            throw refused(value, DECIMAL_VALUE);
        }
        if (!CANONICAL_DECIMAL.matcher(text).matches()) {
            throw new TaskValueException("", DECIMAL_VALUE, text);
        }
        return new BigDecimal(text);
    }

    /**
     * Reads a {@code date}, written as {@code YYYY-MM-DD}.
     *
     * @param value the decoded value
     * @return the date
     * @throws TaskValueException if the value is not a valid date in that form
     */
    public static LocalDate readDate(Object value) {
        if (value instanceof String text && DATE.matcher(text).matches()) {
            try {
                return LocalDate.parse(text);
            } catch (DateTimeException notADate) {
                throw new TaskValueException("", DATE_VALUE, text);
            }
        }
        throw refused(value, DATE_VALUE);
    }

    /**
     * Reads a tagged {@code timestamp} in UTC.
     *
     * @param value the decoded value
     * @return the instant
     * @throws TaskValueException if the value is not a tagged timestamp ending in {@code Z}
     */
    public static Instant readTimestamp(Object value) {
        String text = tagged(value, TIMESTAMP, VALUE);
        if (text == null) {
            throw refused(value, TIMESTAMP_VALUE);
        }
        if (!text.endsWith(UTC)) {
            throw new TaskValueException("", TIMESTAMP_VALUE, text);
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeException notATimestamp) {
            throw new TaskValueException("", TIMESTAMP_VALUE, text);
        }
    }

    /**
     * Reads a tagged {@code duration}, a count of seconds as text and of nanoseconds as a number.
     *
     * @param value the decoded value
     * @return the duration
     * @throws TaskValueException if the value is not a tagged duration, or its parts are out of
     *         range
     */
    public static Duration readDuration(Object value) {
        String seconds = tagged(value, DURATION, SECONDS);
        if (seconds == null) {
            throw refused(value, DURATION_VALUE);
        }
        Object nanos = ((Map<?, ?>) value).get(NANOS);
        boolean validParts = CANONICAL_INT64.matcher(seconds).matches()
                && (nanos instanceof Long || nanos instanceof Integer)
                && Math.abs(((Number) nanos).longValue()) <= MAX_NANOS;
        if (!validParts) {
            throw new TaskValueException("", DURATION_VALUE, "seconds " + seconds + " and nanos " + nanos);
        }
        try {
            return Duration.ofSeconds(Long.parseLong(seconds), ((Number) nanos).longValue());
        } catch (ArithmeticException | NumberFormatException beyondRange) {
            throw new TaskValueException("", DURATION_VALUE, "seconds " + seconds + ", beyond the range of Duration");
        }
    }

    /**
     * Reads tagged {@code bytes}, written as padded standard base64.
     *
     * @param value the decoded value
     * @return the bytes
     * @throws TaskValueException if the value is not tagged bytes in valid base64
     */
    public static byte[] readBytes(Object value) {
        String text = tagged(value, BYTES, VALUE);
        if (text != null) {
            try {
                return Base64.getDecoder().decode(text);
            } catch (IllegalArgumentException notBase64) {
                throw new TaskValueException("", BYTES_VALUE, "not valid base64");
            }
        }
        throw refused(value, BYTES_VALUE);
    }

    /**
     * Reads an {@code enum} value, written as the name of one of its constants.
     *
     * @param value the decoded value
     * @param constants the enum's constants
     * @param <E> the enum type
     * @return the constant with that name
     * @throws TaskValueException if the value is not the name of one of the constants
     */
    public static <E extends Enum<E>> E readEnum(Object value, E[] constants) {
        String expected = "one of " + Arrays.toString(constants);
        if (!(value instanceof String name)) {
            throw refused(value, expected);
        }
        for (E constant : constants) {
            if (constant.name().equals(name)) {
                return constant;
            }
        }
        throw new TaskValueException("", expected, name);
    }

    /**
     * Writes what the task returned.
     *
     * @param value what the task returned
     * @param writer writes it
     * @param <T> the return type
     * @return the JSON value for the harness
     * @throws TaskValueException if part of the value has no form core accepts
     */
    public static <T> Object result(T value, ValueWriter<? super T> writer) {
        try {
            return writer.write(value);
        } catch (TaskValueException refused) {
            throw refused.under(RESULT);
        }
    }

    /**
     * Writes one field of a record into its map of fields.
     *
     * @param fields the record's fields, in declaration order
     * @param name the field name
     * @param value the field's value
     * @param writer writes the value
     * @param <T> the field's type
     * @throws TaskValueException if the value has no form core accepts
     */
    public static <T> void put(Map<String, Object> fields, String name, T value, ValueWriter<? super T> writer) {
        try {
            fields.put(name, writer.write(value));
        } catch (TaskValueException refused) {
            throw refused.under("." + name);
        }
    }

    /**
     * Writes a list, item by item.
     *
     * @param values the list, which may be null
     * @param writer writes each item
     * @param <T> the item type
     * @return the list of JSON values, or null
     * @throws TaskValueException if an item has no form core accepts
     */
    public static <T> List<Object> writeList(List<T> values, ValueWriter<? super T> writer) {
        if (values == null) {
            return null;
        }
        List<Object> written = new ArrayList<>(values.size());
        int index = 0;
        for (T value : values) {
            try {
                written.add(writer.write(value));
            } catch (TaskValueException refused) {
                throw refused.under("[" + index + "]");
            }
            index++;
        }
        return written;
    }

    /**
     * Writes a map with text keys, value by value, keeping the order of its keys.
     *
     * @param values the map, which may be null
     * @param writer writes each value
     * @param <T> the value type
     * @return the map of JSON values, or null
     * @throws TaskValueException if a value has no form core accepts
     */
    public static <T> Map<String, Object> writeMap(Map<String, T> values, ValueWriter<? super T> writer) {
        if (values == null) {
            return null;
        }
        Map<String, Object> written = new LinkedHashMap<>();
        for (Map.Entry<String, T> entry : values.entrySet()) {
            try {
                written.put(entry.getKey(), writer.write(entry.getValue()));
            } catch (TaskValueException refused) {
                throw refused.under("[\"" + entry.getKey() + "\"]");
            }
        }
        return written;
    }

    /**
     * Writes rows whose schema the task does not declare.
     *
     * @param rows the rows, which may be null
     * @return the rows as maps, or null
     */
    public static List<Map<String, Object>> writeRows(Rows rows) {
        return rows == null ? null : rows.toMaps();
    }

    /**
     * Writes a value JSON carries as it is: a {@code String}, {@code Boolean}, {@code Long},
     * {@code Double} or {@code Float}.
     *
     * @param value the value
     * @return the same value
     */
    public static Object plain(Object value) {
        return value;
    }

    /**
     * Writes an {@code int} as the {@code Long} the harness writes for every whole number.
     *
     * @param value the number, which may be null
     * @return the number as a {@code Long}, or null
     */
    public static Object writeInt(Integer value) {
        return value == null ? null : value.longValue();
    }

    /**
     * Writes a {@code decimal} as a tagged value, keeping its scale and with no exponent.
     *
     * @param value the number, which may be null
     * @return the tagged value, or null
     */
    public static Object writeDecimal(BigDecimal value) {
        return value == null ? null : tagged(DECIMAL, value.toPlainString());
    }

    /**
     * Writes a {@code date} as {@code YYYY-MM-DD}.
     *
     * @param value the date, which may be null
     * @return the text, or null
     * @throws TaskValueException if the year is outside 0000 to 9999
     */
    public static Object writeDate(LocalDate value) {
        return value == null ? null : fourDigitYear(value.toString());
    }

    /**
     * Writes a {@code timestamp} as a tagged value in UTC, with zero, three, six or nine digits of
     * fraction.
     *
     * @param value the instant, which may be null
     * @return the tagged value, or null
     * @throws TaskValueException if the year is outside 0000 to 9999
     */
    public static Object writeTimestamp(Instant value) {
        return value == null ? null : tagged(TIMESTAMP, fourDigitYear(value.toString()));
    }

    /**
     * Writes a {@code duration} as a tagged count of seconds and nanoseconds, both with the
     * duration's sign.
     *
     * @param value the duration, which may be null
     * @return the tagged value, or null
     */
    public static Object writeDuration(Duration value) {
        if (value == null) {
            return null;
        }
        long seconds = value.getSeconds();
        long nanos = value.getNano();
        if (seconds < 0 && nanos > 0) {
            seconds += 1;
            nanos -= NANOS_PER_SECOND;
        }
        Map<String, Object> duration = new LinkedHashMap<>();
        duration.put(TAG, DURATION);
        duration.put(SECONDS, Long.toString(seconds));
        duration.put(NANOS, nanos);
        return duration;
    }

    /**
     * Writes {@code bytes} as a tagged value in padded standard base64.
     *
     * @param value the bytes, which may be null
     * @return the tagged value, or null
     */
    public static Object writeBytes(byte[] value) {
        return value == null ? null : tagged(BYTES, Base64.getEncoder().encodeToString(value));
    }

    /**
     * Writes an {@code enum} value as the name of its constant.
     *
     * @param value the constant, which may be null
     * @return the name, or null
     */
    public static Object writeEnum(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static boolean isWhole(double number) {
        return Math.abs(number) < FIRST_DOUBLE_BEYOND_LONG && number == Math.rint(number);
    }

    private static String tagged(Object value, String kind, String part) {
        if (value instanceof Map<?, ?> map && kind.equals(map.get(TAG)) && map.get(part) instanceof String text) {
            return text;
        }
        return null;
    }

    private static Map<String, Object> tagged(String kind, String text) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put(TAG, kind);
        value.put(VALUE, text);
        return value;
    }

    private static String fourDigitYear(String text) {
        if (STARTS_WITH_FOUR_DIGIT_YEAR.matcher(text).matches()) {
            return text;
        }
        throw new TaskValueException("", FOUR_DIGIT_YEAR, text);
    }

    private static TaskValueException refused(Object value, String expected) {
        String found = value == null ? NULL : "of type " + value.getClass().getSimpleName();
        return new TaskValueException("", expected, found);
    }
}
