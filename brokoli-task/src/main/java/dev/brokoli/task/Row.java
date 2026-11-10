package dev.brokoli.task;

import dev.brokoli.task.internal.TaskValueException;
import dev.brokoli.task.internal.TaskValues;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One row of a dataset, as a map from column name to value, in column order.
 *
 * <p>A row holds only JSON values, because that is what the harness writes out: {@code null}, a
 * {@code String}, a {@code Boolean}, a {@code Long} or {@code Integer}, a finite {@code Double} or
 * {@code Float}, an {@code Iterable} of JSON values such as a {@code List}, or a {@code Map} from
 * {@code String} to JSON values. {@link #of(Map)} and {@link #with(String, Object)} refuse any
 * other value with a {@link RowValueException} that names the column, and the path inside it for a
 * nested value, such as {@code attrs.size} or {@code tags[2]}. A number read from JSON is a
 * {@code Long} when it has no fraction and a {@code Double} when it has one.
 *
 * <p>A row cannot be changed: {@link #with(String, Object)} returns a changed copy. Lists and maps
 * nested inside a value are checked but not copied.
 *
 * <p>The typed getters throw {@link RowValueException}, naming the column, when it is missing,
 * when its value is null, or when the value has another type. {@link #isNull(String)} tells a
 * missing or null column apart from one that has a value.
 */
public final class Row {

    private static final String MISSING = "missing";
    private static final String NULL = "null";
    private static final String STRING = "a string";
    private static final String NUMBER = "a number";
    private static final String WHOLE_NUMBER = "a whole number";
    private static final String BOOLEAN = "a boolean";
    private static final String DECIMAL = "a decimal: a number or a tagged decimal";
    private static final String NOT_A_TAGGED_DECIMAL = "a map that is not a tagged decimal";
    private static final String JSON_VALUE =
            "a JSON value (null, String, Boolean, Long, Integer, finite Double or Float, Iterable or Map)";
    private static final String NOT_FINITE = "a number that is not finite";
    private static final String NULL_KEY = "a map with a null key";
    private static final String INEXACT_LONG = "a Long that a double cannot hold exactly";
    private static final double FIRST_DOUBLE_BEYOND_LONG = 0x1p63;

    private final Map<String, Object> values;

    private Row(Map<String, Object> values) {
        this.values = values;
    }

    /**
     * Creates a row holding a copy of the given columns, in their iteration order.
     *
     * @param values the columns, by name; values may be null
     * @return the row
     * @throws NullPointerException if {@code values} or one of its column names is null
     * @throws RowValueException if a value, or a value nested inside one, is not a JSON value
     */
    public static Row of(Map<String, ?> values) {
        Objects.requireNonNull(values, "values");
        Map<String, Object> copy = new LinkedHashMap<>();
        values.forEach((column, value) -> copy.put(Objects.requireNonNull(column, "column"), checked(column, value)));
        return new Row(Collections.unmodifiableMap(copy));
    }

    /**
     * The row's column names, in column order.
     *
     * @return the column names
     */
    public List<String> columns() {
        return List.copyOf(values.keySet());
    }

    /**
     * Whether the row has the column, even if its value is null.
     *
     * @param column the column name
     * @return {@code true} if the column is present
     */
    public boolean has(String column) {
        return values.containsKey(column);
    }

    /**
     * Whether the column is missing or its value is null.
     *
     * @param column the column name
     * @return {@code true} if the column has no value
     */
    public boolean isNull(String column) {
        return values.get(column) == null;
    }

    /**
     * The column's value as the row holds it, without conversion.
     *
     * @param column the column name
     * @return the value, or {@code null} if the column is missing or null
     */
    public Object get(String column) {
        return values.get(column);
    }

    /**
     * The column's value as text.
     *
     * @param column the column name
     * @return the value
     * @throws RowValueException if the column is missing, null, or not a {@code String}
     */
    public String getString(String column) {
        Object value = present(column, STRING);
        if (value instanceof String text) {
            return text;
        }
        throw wrongType(column, STRING, value);
    }

    /**
     * The column's value as a {@code long}.
     *
     * @param column the column name
     * @return the value
     * @throws RowValueException if the column is missing, null, or not a {@code Long} or
     *         {@code Integer}; a number with a fraction is refused rather than truncated
     */
    public long getLong(String column) {
        Object value = present(column, WHOLE_NUMBER);
        if (value instanceof Long || value instanceof Integer) {
            return ((Number) value).longValue();
        }
        throw wrongType(column, WHOLE_NUMBER, value);
    }

    /**
     * The column's value as a {@code double}. A whole number is accepted, because a value such as
     * {@code 5} in a column of fractional numbers arrives as a {@code Long}; a {@code Long} that a
     * {@code double} cannot hold exactly, such as a 64-bit id above 2<sup>53</sup>, is refused
     * rather than rounded.
     *
     * @param column the column name
     * @return the value
     * @throws RowValueException if the column is missing, null, not a number, or a {@code Long}
     *         that a {@code double} cannot hold exactly
     */
    public double getDouble(String column) {
        Object value = present(column, NUMBER);
        if (value instanceof Long whole) {
            return exactly(column, whole);
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw wrongType(column, NUMBER, value);
    }

    /**
     * The column's value as a {@code BigDecimal}. A tagged decimal, such as
     * {@code {"$bptd": "decimal", "value": "12.50"}}, is read exactly, with its scale. A whole
     * number is read exactly too. A number with a fraction has already been decoded as a
     * {@code Double}, so it is read through {@link BigDecimal#valueOf(double)}: {@code 12.50}
     * written as a plain JSON number comes back as {@code 12.5}.
     *
     * @param column the column name
     * @return the value
     * @throws RowValueException if the column is missing, null, or neither a number nor a tagged
     *         decimal
     */
    public BigDecimal getDecimal(String column) {
        Object value = present(column, DECIMAL);
        if (value instanceof Long || value instanceof Integer || value instanceof Double || value instanceof Float) {
            return new BigDecimal(value.toString());
        }
        if (value instanceof Map) {
            try {
                return TaskValues.readDecimal(value);
            } catch (TaskValueException notADecimal) {
                throw new RowValueException(column, DECIMAL, NOT_A_TAGGED_DECIMAL);
            }
        }
        throw wrongType(column, DECIMAL, value);
    }

    /**
     * The column's value as a {@code boolean}.
     *
     * @param column the column name
     * @return the value
     * @throws RowValueException if the column is missing, null, or not a {@code Boolean}
     */
    public boolean getBoolean(String column) {
        Object value = present(column, BOOLEAN);
        if (value instanceof Boolean flag) {
            return flag;
        }
        throw wrongType(column, BOOLEAN, value);
    }

    /**
     * A copy of this row with the column set to the value. An existing column keeps its position;
     * a new one is added last.
     *
     * @param column the column name
     * @param value the value, which may be null
     * @return the changed copy; this row is unchanged
     * @throws NullPointerException if {@code column} is null
     * @throws RowValueException if the value, or a value nested inside it, is not a JSON value
     */
    public Row with(String column, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(values);
        copy.put(Objects.requireNonNull(column, "column"), checked(column, value));
        return new Row(Collections.unmodifiableMap(copy));
    }

    /**
     * The row as a map from column name to value, in column order.
     *
     * @return an unmodifiable view of the row's columns
     */
    public Map<String, Object> toMap() {
        return values;
    }

    /**
     * Two rows are equal when they hold the same columns with equal values, in any order.
     *
     * @param other the object to compare with
     * @return {@code true} if {@code other} is an equal row
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof Row row && values.equals(row.values);
    }

    /**
     * A hash code consistent with {@link #equals(Object)}.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return values.hashCode();
    }

    /**
     * The row's columns and values, for debugging and test failures. It includes every value, so
     * a row logged this way copies its data into the log.
     *
     * @return the text form of the row, with its values
     */
    @Override
    public String toString() {
        return "Row" + values;
    }

    private Object present(String column, String expected) {
        Object value = values.get(column);
        if (value != null) {
            return value;
        }
        throw new RowValueException(column, expected, values.containsKey(column) ? NULL : MISSING);
    }

    private static double exactly(String column, long whole) {
        double number = whole;
        if (number < FIRST_DOUBLE_BEYOND_LONG && (long) number == whole) {
            return number;
        }
        throw new RowValueException(column, NUMBER, INEXACT_LONG);
    }

    private static Object checked(String column, Object value) {
        Refusal refusal = refusal(value);
        if (refusal != null) {
            throw new RowValueException(column + refusal.path(), JSON_VALUE, refusal.found());
        }
        return value;
    }

    private static Refusal refusal(Object value) {
        if (value == null || value instanceof String || value instanceof Boolean
                || value instanceof Long || value instanceof Integer) {
            return null;
        }
        if (value instanceof Double || value instanceof Float) {
            return Double.isFinite(((Number) value).doubleValue()) ? null : new Refusal("", NOT_FINITE);
        }
        if (value instanceof Map<?, ?> map) {
            return refusalInMap(map);
        }
        if (value instanceof Iterable<?> items) {
            return refusalInItems(items);
        }
        return new Refusal("", typeOf(value));
    }

    private static Refusal refusalInMap(Map<?, ?> map) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            if (key == null) {
                return new Refusal("", NULL_KEY);
            }
            if (!(key instanceof String name)) {
                return new Refusal("", "a map with a key " + typeOf(key));
            }
            Refusal nested = refusal(entry.getValue());
            if (nested != null) {
                return nested.under("." + name);
            }
        }
        return null;
    }

    private static Refusal refusalInItems(Iterable<?> items) {
        int index = 0;
        for (Object item : items) {
            Refusal nested = refusal(item);
            if (nested != null) {
                return nested.under("[" + index + "]");
            }
            index++;
        }
        return null;
    }

    private static RowValueException wrongType(String column, String expected, Object value) {
        return new RowValueException(column, expected, typeOf(value));
    }

    private static String typeOf(Object value) {
        return "of type " + value.getClass().getSimpleName();
    }

    private record Refusal(String path, String found) {

        Refusal under(String segment) {
            return new Refusal(segment + path, found);
        }
    }
}
