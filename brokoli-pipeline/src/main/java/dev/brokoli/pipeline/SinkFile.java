package dev.brokoli.pipeline;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * The options of a {@code sink_file} node, which writes its input to a CSV, JSON or Parquet file. Start from
 * {@link #options()} and change what differs:
 *
 * <pre>{@code
 * SinkFile.options().path("/data/out.parquet").format("parquet").compress("snappy")
 * }</pre>
 *
 * @param path the file to write, empty unless set; the server refuses a {@code sink_file} without one
 * @param format the file's format, such as {@code "csv"} or {@code "parquet"}, {@code "csv"} unless set
 * @param compress the file's compression, such as {@code "snappy"}, if set
 * @param connId the connection the file is written through, such as an object store, if set
 * @param node the options every node has
 */
public record SinkFile(String path, String format, Optional<String> compress, Optional<String> connId,
        NodeOptions node) {

    private static final String CSV = "csv";

    /**
     * Creates the options.
     *
     * @param path the file to write
     * @param format the file's format
     * @param compress the file's compression, if set
     * @param connId the connection, if set
     * @param node the options every node has
     * @throws NullPointerException if a value is {@code null}
     */
    public SinkFile {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(compress, "compress");
        Objects.requireNonNull(connId, "connId");
        Objects.requireNonNull(node, "node");
    }

    /**
     * The default options: no path, the {@code "csv"} format, no compression, no connection, and
     * {@link NodeOptions#defaults()}.
     *
     * @return the default options
     */
    public static SinkFile options() {
        return new SinkFile("", CSV, Optional.empty(), Optional.empty(), NodeOptions.defaults());
    }

    /**
     * These options with another file to write.
     *
     * @param path the file to write, such as {@code "/data/out.csv"}
     * @return the new options
     */
    public SinkFile path(String path) {
        return new SinkFile(path, format, compress, connId, node);
    }

    /**
     * These options with another format.
     *
     * @param format the file's format, such as {@code "parquet"}
     * @return the new options
     */
    public SinkFile format(String format) {
        return new SinkFile(path, format, compress, connId, node);
    }

    /**
     * These options with the file's compression.
     *
     * @param compress the compression, such as {@code "snappy"} or {@code "gzip"}
     * @return the new options
     */
    public SinkFile compress(String compress) {
        return new SinkFile(path, format, Optional.of(Objects.requireNonNull(compress, "compress")), connId, node);
    }

    /**
     * These options with the connection the file is written through.
     *
     * @param connId the connection's id, such as {@code "lake"}
     * @return the new options
     */
    public SinkFile connId(String connId) {
        return new SinkFile(path, format, compress, Optional.of(Objects.requireNonNull(connId, "connId")), node);
    }

    /**
     * These options with a node key, see {@link NodeOptions#nodeKey(String)}.
     *
     * @param nodeKey the key, such as {@code "write-report"}
     * @return the new options
     * @throws IllegalArgumentException if the key does not match {@code [a-z][a-z0-9_-]{0,63}}
     */
    public SinkFile nodeKey(String nodeKey) {
        return new SinkFile(path, format, compress, connId, node.nodeKey(nodeKey));
    }

    /**
     * These options with retries spaced by {@link Backoff#EXPONENTIAL}.
     *
     * @param retries how many times the server retries the node after it fails
     * @return the new options
     * @throws IllegalArgumentException if the retries are negative
     */
    public SinkFile retries(int retries) {
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
    public SinkFile retries(int retries, Backoff backoff) {
        return new SinkFile(path, format, compress, connId, node.retries(retries, backoff));
    }

    /**
     * These options with a delay before the first retry.
     *
     * @param retryDelay the delay, a whole number of milliseconds
     * @return the new options
     * @throws IllegalArgumentException if the delay is negative or not a whole number of milliseconds
     */
    public SinkFile retryDelay(Duration retryDelay) {
        return new SinkFile(path, format, compress, connId, node.retryDelay(retryDelay));
    }

    /**
     * These options with a timeout for one attempt.
     *
     * @param timeout the timeout, a whole number of seconds
     * @return the new options
     * @throws IllegalArgumentException if the timeout is negative or not a whole number of seconds
     */
    public SinkFile timeout(Duration timeout) {
        return new SinkFile(path, format, compress, connId, node.timeout(timeout));
    }

    /**
     * These options with the pool the node runs in.
     *
     * @param pool the pool, such as {@code "files"}
     * @return the new options
     */
    public SinkFile pool(String pool) {
        return new SinkFile(path, format, compress, connId, node.pool(pool));
    }
}
