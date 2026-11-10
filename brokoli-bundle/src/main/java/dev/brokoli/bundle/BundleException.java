package dev.brokoli.bundle;

/**
 * Thrown when a project's tasks cannot be packaged, with a message naming the file, task or value that is the
 * reason: a task file that is not valid, two files that would have the same path in the bundle, a file that
 * cannot be read, or a path or name that is not valid Unicode.
 *
 * @serial exclude
 */
public class BundleException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message what could not be packaged, and why
     */
    public BundleException(String message) {
        super(message);
    }

    /**
     * Creates the exception for a failure with an underlying cause, such as a file that cannot be read.
     *
     * @param message what could not be packaged, and why
     * @param cause the underlying failure
     */
    public BundleException(String message, Throwable cause) {
        super(message, cause);
    }
}
