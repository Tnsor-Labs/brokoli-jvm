package dev.brokoli.task.internal;

/**
 * Thrown by {@link TaskValues} when a value a task receives or returns does not have the form its
 * declared type needs. Used by the code the task processor generates; not part of the task API.
 *
 * @serial exclude
 */
public class TaskValueException extends IllegalArgumentException {

    private final String path;
    private final String expected;
    private final String found;

    /**
     * Creates the exception for one value.
     *
     * @param path where the value is, such as {@code parameter threshold} or {@code input[3].amount}
     * @param expected what the value should have been, such as {@code "a number"}
     * @param found what it was, such as {@code "missing"}, {@code "null"} or {@code "of type String"}
     */
    public TaskValueException(String path, String expected, String found) {
        super(describe(path, expected, found));
        this.path = path;
        this.expected = expected;
        this.found = found;
    }

    /**
     * Where the value is.
     *
     * @return the path, such as {@code input[3].amount}
     */
    public String getPath() {
        return path;
    }

    /**
     * What the value should have been.
     *
     * @return the expected value, such as {@code "a number"}
     */
    public String getExpected() {
        return expected;
    }

    /**
     * What the value was.
     *
     * @return {@code "missing"}, {@code "null"}, or a description of the value
     */
    public String getFound() {
        return found;
    }

    /**
     * The same failure, one level further out: {@code under("input")} turns {@code [3].amount} into
     * {@code input[3].amount}.
     *
     * @param prefix the path of the enclosing value
     * @return a new exception with the longer path
     */
    public TaskValueException under(String prefix) {
        return new TaskValueException(prefix + path, expected, found);
    }

    private static String describe(String path, String expected, String found) {
        return path + " is " + found + ", expected " + expected;
    }
}
