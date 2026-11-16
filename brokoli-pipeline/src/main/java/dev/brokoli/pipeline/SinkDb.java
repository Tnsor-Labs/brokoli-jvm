package dev.brokoli.pipeline;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The options of a {@code sink_db} node, which writes its input to a database table. The server writes the SQL
 * from the input's rows. Start from {@link #options()} and change what differs:
 *
 * <pre>{@code
 * SinkDb.options().table("orders_enriched").connId("warehouse").upsert("id")
 * }</pre>
 *
 * @param table the table to write, empty unless set; the server refuses a {@code sink_db} without one
 * @param mode how the rows are written, {@link WriteMode#APPEND} unless set
 * @param keyColumns the columns an upsert matches rows on, which only an upsert has
 * @param connId the connection the table is written through, if set
 * @param uri the database's URI, in place of a connection, if set
 * @param truncate whether an overwrite clears the table with {@code TRUNCATE} instead of {@code DELETE}, if set
 * @param createTable whether the server creates the table from the input's columns when it does not exist, if set
 * @param tableEngine the engine of a table the server creates on ClickHouse, if set
 * @param node the options every node has
 */
public record SinkDb(String table, WriteMode mode, List<String> keyColumns, Optional<String> connId,
        Optional<String> uri, Optional<Boolean> truncate, Optional<Boolean> createTable, Optional<String> tableEngine,
        NodeOptions node) implements WithPool<SinkDb> {

    /**
     * Creates the options, with a copy of the key columns.
     *
     * @param table the table
     * @param mode how the rows are written
     * @param keyColumns the key columns of an upsert
     * @param connId the connection, if set
     * @param uri the database's URI, if set
     * @param truncate whether an overwrite truncates, if set
     * @param createTable whether the server creates the table, if set
     * @param tableEngine the engine of a created ClickHouse table, if set
     * @param node the options every node has
     * @throws IllegalArgumentException if the mode is an upsert with no key columns, or another mode with key
     *         columns
     * @throws NullPointerException if a value, or a key column, is {@code null}
     */
    public SinkDb {
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(mode, "mode");
        keyColumns = List.copyOf(keyColumns);
        Objects.requireNonNull(connId, "connId");
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(truncate, "truncate");
        Objects.requireNonNull(createTable, "createTable");
        Objects.requireNonNull(tableEngine, "tableEngine");
        Objects.requireNonNull(node, "node");
        if (mode == WriteMode.UPSERT && keyColumns.isEmpty()) {
            throw new IllegalArgumentException("an upsert needs the key columns it matches rows on; give them with"
                    + " upsert(\"id\")");
        }
        if (mode != WriteMode.UPSERT && !keyColumns.isEmpty()) {
            throw new IllegalArgumentException("the key columns " + keyColumns + " are only written for an upsert,"
                    + " but the mode is " + mode.irValue());
        }
    }

    /**
     * The default options: no table, {@link WriteMode#APPEND}, no connection or URI, and
     * {@link NodeOptions#defaults()}.
     *
     * @return the default options
     */
    public static SinkDb options() {
        return new SinkDb("", WriteMode.APPEND, List.of(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), NodeOptions.defaults());
    }

    /**
     * These options with another table.
     *
     * @param table the table, such as {@code "public.orders"}
     * @return the new options
     */
    public SinkDb table(String table) {
        return new SinkDb(table, mode, keyColumns, connId, uri, truncate, createTable, tableEngine, node);
    }

    /**
     * These options with the rows appended or the table overwritten. An upsert is set by {@link #upsert(String...)},
     * which gives its key columns with it.
     *
     * @param mode {@link WriteMode#APPEND} or {@link WriteMode#OVERWRITE}
     * @return the new options, with no key columns
     * @throws IllegalArgumentException if the mode is {@link WriteMode#UPSERT}
     */
    public SinkDb mode(WriteMode mode) {
        if (mode == WriteMode.UPSERT) {
            throw new IllegalArgumentException("an upsert needs the key columns it matches rows on; give them with"
                    + " upsert(\"id\") instead of mode(WriteMode.UPSERT)");
        }
        return new SinkDb(table, mode, List.of(), connId, uri, truncate, createTable, tableEngine, node);
    }

    /**
     * These options with the rows upserted: inserted, and updated where their key columns match a row of the
     * table.
     *
     * @param keyColumns the columns rows are matched on, such as {@code "id"}
     * @return the new options
     * @throws IllegalArgumentException if no key column is given
     * @throws NullPointerException if a key column is {@code null}
     */
    public SinkDb upsert(String... keyColumns) {
        return new SinkDb(table, WriteMode.UPSERT, List.of(keyColumns), connId, uri, truncate, createTable,
                tableEngine, node);
    }

    /**
     * These options with the connection the table is written through.
     *
     * @param connId the connection's id, such as {@code "warehouse"}
     * @return the new options
     */
    public SinkDb connId(String connId) {
        return new SinkDb(table, mode, keyColumns, Optional.of(Objects.requireNonNull(connId, "connId")), uri,
                truncate, createTable, tableEngine, node);
    }

    /**
     * These options with the database's URI, in place of a connection.
     *
     * @param uri the URI, such as {@code "postgres://db:5432/shop"}
     * @return the new options
     */
    public SinkDb uri(String uri) {
        return new SinkDb(table, mode, keyColumns, connId, Optional.of(Objects.requireNonNull(uri, "uri")),
                truncate, createTable, tableEngine, node);
    }

    /**
     * These options with whether an overwrite clears the table with {@code TRUNCATE}, which leaves no dead rows but
     * blocks readers of the table for the whole load, instead of {@code DELETE}.
     *
     * @param truncate whether to truncate
     * @return the new options
     */
    public SinkDb truncate(boolean truncate) {
        return new SinkDb(table, mode, keyColumns, connId, uri, Optional.of(truncate), createTable, tableEngine,
                node);
    }

    /**
     * These options with whether the server creates the table from the input's columns when it does not exist.
     *
     * @param createTable whether to create the table
     * @return the new options
     */
    public SinkDb createTable(boolean createTable) {
        return new SinkDb(table, mode, keyColumns, connId, uri, truncate, Optional.of(createTable), tableEngine,
                node);
    }

    /**
     * These options with the engine of a table the server creates on ClickHouse, {@code MergeTree} unless set.
     *
     * @param tableEngine the engine, such as {@code "ReplacingMergeTree"}
     * @return the new options
     */
    public SinkDb tableEngine(String tableEngine) {
        return new SinkDb(table, mode, keyColumns, connId, uri, truncate, createTable,
                Optional.of(Objects.requireNonNull(tableEngine, "tableEngine")), node);
    }

    /**
     * These options with other options every node has.
     *
     * @param node the options every node has
     * @return the new options
     */
    @Override
    public SinkDb node(NodeOptions node) {
        return new SinkDb(table, mode, keyColumns, connId, uri, truncate, createTable, tableEngine, node);
    }
}
