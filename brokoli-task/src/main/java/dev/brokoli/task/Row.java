package dev.brokoli.task;

import dev.brokoli.task.exception.RowValueException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One row of a dataset, as a map from column name to value, in column order.
 *
 * <p>A row cannot be changed: {@link #with(String, Object)} returns a changed copy. Values are what
 * the row's JSON holds: {@code String}, {@code Boolean}, {@code Long} for a number without a
 * fraction, {@code Double} for one with a fraction, {@code List}, {@code Map}, or {@code null}.
 * Lists and maps nested inside a value are not copied.
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
     */
    public static Row of(Map<String, ?> values) {
        Objects.requireNonNull(values, "values");
        Map<String, Object> copy = new LinkedHashMap<>();
        values.forEach((column, value) -> copy.put(Objects.requireNonNull(column, "column"), value));
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
     * @throws RowValueException if the column is missing, null, or not a whole number; a number
     *         with a fraction is refused rather than truncated
     */
    public long getLong(String column) {
        Object value = present(column, WHOLE_NUMBER);
        if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return ((Number) value).longValue();
        }
        throw wrongType(column, WHOLE_NUMBER, value);
    }

    /**
     * The column's value as a {@code double}. Any number is accepted, because a value such as
     * {@code 5} in a column of fractional numbers arrives as a {@code Long}.
     *
     * @param column the column name
     * @return the value
     * @throws RowValueException if the column is missing, null, or not a number
     */
    public double getDouble(String column) {
        Object value = present(column, NUMBER);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw wrongType(column, NUMBER, value);
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
     */
    public Row with(String column, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(values);
        copy.put(Objects.requireNonNull(column, "column"), value);
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
     * The row's columns and values, for logs and test failures.
     *
     * @return the text form of the row
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

    private static RowValueException wrongType(String column, String expected, Object value) {
        return new RowValueException(column, expected, "of type " + value.getClass().getSimpleName());
    }
}
