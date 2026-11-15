package dev.brokoli.pipeline;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * The options of a {@code source_file} node, which reads a CSV, JSON, Excel or XML file. Start from
 * {@link #options()} and change what differs:
 *
 * <pre>{@code
 * SourceFile.options().path("/data/sales.csv").format("csv").retries(3).timeout(Duration.ofMinutes(1))
 * }</pre>
 *
 * @param path the file to read, empty unless set; the server refuses a {@code source_file} without one
 * @param format the file's format, such as {@code "csv"} or {@code "json"}, {@code "csv"} unless set
 * @param connId the connection the file is read through, such as an object store, if set
 * @param node the options every node has
 */
public record SourceFile(String path, String format, Optional<String> connId, NodeOptions node) {

    private static final String CSV = "csv";

    /**
     * Creates the options.
     *
     * @param path the file to read
     * @param format the file's format
     * @param connId the connection, if set
     * @param node the options every node has
     * @throws NullPointerException if a value is {@code null}
     */
    public SourceFile {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(connId, "connId");
        Objects.requireNonNull(node, "node");
    }

    /**
     * The default options: no path, the {@code "csv"} format, no connection, and {@link NodeOptions#defaults()}.
     *
     * @return the default options
     */
    public static SourceFile options() {
        return new SourceFile("", CSV, Optional.empty(), NodeOptions.defaults());
    }

    /**
     * These options with another file to read.
     *
     * @param path the file to read, such as {@code "/data/sales.csv"}
     * @return the new options
     */
    public SourceFile path(String path) {
        return new SourceFile(path, format, connId, node);
    }

    /**
     * These options with another format.
     *
     * @param format the file's format, such as {@code "json"}
     * @return the new options
     */
    public SourceFile format(String format) {
        return new SourceFile(path, format, connId, node);
    }

    /**
     * These options with the connection the file is read through.
     *
     * @param connId the connection's id, such as {@code "lake"}
     * @return the new options
     */
    public SourceFile connId(String connId) {
        return new SourceFile(path, format, Optional.of(Objects.requireNonNull(connId, "connId")), node);
    }

    /**
     * These options with a node key, see {@link NodeOptions#nodeKey(String)}.
     *
     * @param nodeKey the key, such as {@code "read-sales"}
     * @return the new options
     * @throws IllegalArgumentException if the key does not match {@code [a-z][a-z0-9_-]{0,63}}
     */
    public SourceFile nodeKey(String nodeKey) {
        return new SourceFile(path, format, connId, node.nodeKey(nodeKey));
    }

    /**
     * These options with retries spaced by {@link Backoff#EXPONENTIAL}.
     *
     * @param retries how many times the server retries the node after it fails
     * @return the new options
     * @throws IllegalArgumentException if the retries are negative
     */
    public SourceFile retries(int retries) {
        return retries(retries, Backoff.EXPONENTIAL);
    }

    /**
     * These options with retries spaced by {@code backoff}.
     *
     * @param retries how many times the server retries the node after it fails
     * @param backoff how the server spaces the retries
     * @return the new options
     * @throws IllegalArgumentException if the retries are negative
     */
    public SourceFile retries(int retries, Backoff backoff) {
        return new SourceFile(path, format, connId, node.retries(retries, backoff));
    }

    /**
     * These options with a delay before the first retry.
     *
     * @param retryDelay the delay, a whole number of milliseconds
     * @return the new options
     * @throws IllegalArgumentException if the delay is negative or not a whole number of milliseconds
     */
    public SourceFile retryDelay(Duration retryDelay) {
        return new SourceFile(path, format, connId, node.retryDelay(retryDelay));
    }

    /**
     * These options with a timeout for one attempt.
     *
     * @param timeout the timeout, a whole number of seconds
     * @return the new options
     * @throws IllegalArgumentException if the timeout is negative or not a whole number of seconds
     */
    public SourceFile timeout(Duration timeout) {
        return new SourceFile(path, format, connId, node.timeout(timeout));
    }

    /**
     * These options with the pool the node runs in.
     *
     * @param pool the pool, such as {@code "files"}
     * @return the new options
     */
    public SourceFile pool(String pool) {
        return new SourceFile(path, format, connId, node.pool(pool));
    }
}
