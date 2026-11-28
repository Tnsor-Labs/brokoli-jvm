package dev.brokoli.pipeline;

import java.util.Objects;

/**
 * A column of a {@link DatasetSchema}:
 *
 * <pre>{@code
 * Column.of("id", DataType.int64())
 * Column.of("name", DataType.string().nullable())
 * }</pre>
 *
 * @param name the column's name
 * @param type the column's type
 */
public record Column(String name, DataType type) {

    /**
     * Creates the column.
     *
     * @param name the column's name
     * @param type the column's type
     * @throws IllegalArgumentException if the name is empty
     * @throws NullPointerException if a value is {@code null}
     */
    public Column {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        if (name.isEmpty()) {
            throw new IllegalArgumentException("a dataset column needs a name");
        }
    }

    /**
     * A column.
     *
     * @param name the column's name, such as {@code "id"}
     * @param type the column's type, such as {@code DataType.int64()}
     * @return the column
     * @throws IllegalArgumentException if the name is empty
     */
    public static Column of(String name, DataType type) {
        return new Column(name, type);
    }
}
