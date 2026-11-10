package dev.brokoli.task.exception;

/**
 * Thrown when a column of a {@link dev.brokoli.task.Row} is read as a type its value does not
 * have, including when the column is missing or its value is null.
 *
 * @serial exclude
 */
public class RowValueException extends RuntimeException {

    private final String column;
    private final String expected;
    private final String found;

    /**
     * Creates the exception for one column read.
     *
     * @param column the column that was read
     * @param expected what the read expected, such as {@code "a number"}
     * @param found what the column held, such as {@code "missing"}, {@code "null"} or
     *        {@code "of type String"}
     */
    public RowValueException(String column, String expected, String found) {
        super(describe(column, expected, found));
        this.column = column;
        this.expected = expected;
        this.found = found;
    }

    /**
     * The column that was read.
     *
     * @return the column name
     */
    public String getColumn() {
        return column;
    }

    /**
     * What the read expected.
     *
     * @return the expected value, such as {@code "a number"}
     */
    public String getExpected() {
        return expected;
    }

    /**
     * What the column held.
     *
     * @return {@code "missing"}, {@code "null"}, or the type of the value, such as
     *         {@code "of type String"}
     */
    public String getFound() {
        return found;
    }

    private static String describe(String column, String expected, String found) {
        return "column '" + column + "' is " + found + ", expected " + expected;
    }
}
