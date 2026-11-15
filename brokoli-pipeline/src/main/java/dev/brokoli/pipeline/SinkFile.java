package dev.brokoli.pipeline;

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
        NodeOptions node) implements WithPool<SinkFile> {

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
     * These options with other options every node has.
     *
     * @param node the options every node has
     * @return the new options
     */
    @Override
    public SinkFile node(NodeOptions node) {
        return new SinkFile(path, format, compress, connId, node);
    }
}
