package dev.brokoli.pipeline;

/**
 * How a {@code sink_db} node writes its input to its table.
 */
public enum WriteMode {

    /**
     * Adds the rows to what the table already holds. The default, as in the Python SDK.
     */
    APPEND("append"),

    /**
     * Clears the table, then adds the rows.
     */
    OVERWRITE("overwrite"),

    /**
     * Inserts the rows, updating those whose key columns match a row of the table. Set by
     * {@link SinkDb#upsert(String...)}, which gives the key columns with it.
     */
    UPSERT("upsert");

    private final String irValue;

    WriteMode(String irValue) {
        this.irValue = irValue;
    }

    String irValue() {
        return irValue;
    }
}
