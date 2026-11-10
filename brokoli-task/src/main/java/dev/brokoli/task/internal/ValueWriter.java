package dev.brokoli.task.internal;

/**
 * Writes one Java value into the JSON value the harness writes out. Used by the code the task
 * processor generates; not part of the task API.
 *
 * @param <T> the Java type written
 */
@FunctionalInterface
public interface ValueWriter<T> {

    /**
     * Writes the value.
     *
     * @param value the Java value, which may be null
     * @return the JSON value, or null for a null value
     * @throws TaskValueException if the value has no form core accepts
     */
    Object write(T value);
}
