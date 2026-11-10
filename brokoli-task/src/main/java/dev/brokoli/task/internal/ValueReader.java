package dev.brokoli.task.internal;

/**
 * Reads one value, as the harness decoded it from JSON, into a Java type. Used by the code the task
 * processor generates; not part of the task API.
 *
 * @param <T> the Java type read
 */
@FunctionalInterface
public interface ValueReader<T> {

    /**
     * Reads the value.
     *
     * @param value the decoded JSON value, which may be null
     * @return the Java value
     * @throws TaskValueException if the value does not have the form the type needs
     */
    T read(Object value);
}
