package dev.brokoli.pipeline;

import java.util.Objects;
import java.util.Optional;

/**
 * The options of a {@code source_db} node, which reads the rows a query returns from a database such as Postgres,
 * MySQL or SQLite. Start from {@link #options()} and change what differs:
 *
 * <pre>{@code
 * SourceDb.options().query("SELECT * FROM orders").connId("warehouse").retries(3)
 * }</pre>
 *
 * @param query the query, empty unless set; the server refuses a {@code source_db} without one
 * @param connId the connection the query runs on, if set
 * @param uri the database's URI, in place of a connection, if set
 * @param schema the columns of the rows the query returns, if declared
 * @param node the options every node has
 */
public record SourceDb(String query, Optional<String> connId, Optional<String> uri, Optional<DatasetSchema> schema,
        NodeOptions node) implements WithPool<SourceDb> {

    /**
     * Creates the options.
     *
     * @param query the query
     * @param connId the connection, if set
     * @param uri the database's URI, if set
     * @param schema the columns of the rows, if declared
     * @param node the options every node has
     * @throws NullPointerException if a value is {@code null}
     */
    public SourceDb {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(connId, "connId");
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(node, "node");
    }

    /**
     * The default options: no query, connection, URI or schema, and {@link NodeOptions#defaults()}.
     *
     * @return the default options
     */
    public static SourceDb options() {
        return new SourceDb("", Optional.empty(), Optional.empty(), Optional.empty(), NodeOptions.defaults());
    }

    /**
     * These options with another query.
     *
     * @param query the query, such as {@code "SELECT * FROM orders"}
     * @return the new options
     */
    public SourceDb query(String query) {
        return new SourceDb(query, connId, uri, schema, node);
    }

    /**
     * These options with the connection the query runs on.
     *
     * @param connId the connection's id, such as {@code "warehouse"}
     * @return the new options
     */
    public SourceDb connId(String connId) {
        return new SourceDb(query, Optional.of(Objects.requireNonNull(connId, "connId")), uri, schema, node);
    }

    /**
     * These options with the database's URI, in place of a connection.
     *
     * @param uri the URI, such as {@code "postgres://db:5432/shop"}
     * @return the new options
     */
    public SourceDb uri(String uri) {
        return new SourceDb(query, connId, Optional.of(Objects.requireNonNull(uri, "uri")), schema, node);
    }

    /**
     * These options with the columns of the rows the query returns. A {@code join} of two nodes that declare a
     * schema writes the schema of its output.
     *
     * @param schema the columns, such as {@code DatasetSchema.of(Column.of("id", DataType.int64())).closed()}
     * @return the new options
     */
    public SourceDb schema(DatasetSchema schema) {
        return new SourceDb(query, connId, uri, Optional.of(Objects.requireNonNull(schema, "schema")), node);
    }

    /**
     * These options with other options every node has.
     *
     * @param node the options every node has
     * @return the new options
     */
    @Override
    public SourceDb node(NodeOptions node) {
        return new SourceDb(query, connId, uri, schema, node);
    }
}
