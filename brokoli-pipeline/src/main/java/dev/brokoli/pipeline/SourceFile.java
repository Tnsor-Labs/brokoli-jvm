package dev.brokoli.pipeline;

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
 * @param schema the columns of the rows the file holds, if declared
 * @param node the options every node has
 */
public record SourceFile(String path, String format, Optional<String> connId, Optional<DatasetSchema> schema,
        NodeOptions node) implements WithPool<SourceFile> {

    private static final String CSV = "csv";

    /**
     * Creates the options.
     *
     * @param path the file to read
     * @param format the file's format
     * @param connId the connection, if set
     * @param schema the columns of the rows, if declared
     * @param node the options every node has
     * @throws NullPointerException if a value is {@code null}
     */
    public SourceFile {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(connId, "connId");
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(node, "node");
    }

    /**
     * The default options: no path, the {@code "csv"} format, no connection or schema, and
     * {@link NodeOptions#defaults()}.
     *
     * @return the default options
     */
    public static SourceFile options() {
        return new SourceFile("", CSV, Optional.empty(), Optional.empty(), NodeOptions.defaults());
    }

    /**
     * These options with another file to read.
     *
     * @param path the file to read, such as {@code "/data/sales.csv"}
     * @return the new options
     */
    public SourceFile path(String path) {
        return new SourceFile(path, format, connId, schema, node);
    }

    /**
     * These options with another format.
     *
     * @param format the file's format, such as {@code "json"}
     * @return the new options
     */
    public SourceFile format(String format) {
        return new SourceFile(path, format, connId, schema, node);
    }

    /**
     * These options with the connection the file is read through.
     *
     * @param connId the connection's id, such as {@code "lake"}
     * @return the new options
     */
    public SourceFile connId(String connId) {
        return new SourceFile(path, format, Optional.of(Objects.requireNonNull(connId, "connId")), schema, node);
    }

    /**
     * These options with the columns of the rows the file holds. A {@code join} of two nodes that declare a
     * schema writes the schema of its output.
     *
     * @param schema the columns, such as {@code DatasetSchema.of(Column.of("id", DataType.int64())).closed()}
     * @return the new options
     */
    public SourceFile schema(DatasetSchema schema) {
        return new SourceFile(path, format, connId, Optional.of(Objects.requireNonNull(schema, "schema")), node);
    }

    /**
     * These options with other options every node has.
     *
     * @param node the options every node has
     * @return the new options
     */
    @Override
    public SourceFile node(NodeOptions node) {
        return new SourceFile(path, format, connId, schema, node);
    }
}
