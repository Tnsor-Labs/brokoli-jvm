package dev.brokoli.pipeline;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The columns of a dataset, which a source node declares with its {@code schema} option and a {@code join} derives
 * from its inputs' schemas. It is written as core's {@code brokoli.dataset-schema/v1}:
 *
 * <pre>{@code
 * DatasetSchema.of(Column.of("id", DataType.int64()), Column.of("name", DataType.string().nullable())).closed()
 * }</pre>
 *
 * @param columns the columns, in the dataset's order
 * @param additionalColumns whether the dataset may have other columns, {@link AdditionalColumns#UNKNOWN} unless set
 */
public record DatasetSchema(List<Column> columns, AdditionalColumns additionalColumns) {

    /**
     * Creates the schema, with a copy of the columns.
     *
     * @param columns the columns
     * @param additionalColumns whether the dataset may have other columns
     * @throws IllegalArgumentException if two columns have the same name
     * @throws NullPointerException if a value or a column is {@code null}
     */
    public DatasetSchema {
        columns = List.copyOf(columns);
        Objects.requireNonNull(additionalColumns, "additionalColumns");
        Set<String> names = new HashSet<>();
        for (Column column : columns) {
            if (!names.add(column.name())) {
                throw new IllegalArgumentException("the dataset schema has two columns named " + column.name());
            }
        }
    }

    /**
     * A schema with these columns that may not be all the dataset has: {@link AdditionalColumns#UNKNOWN}.
     *
     * @param columns the columns, in the dataset's order
     * @return the schema
     * @throws IllegalArgumentException if two columns have the same name
     */
    public static DatasetSchema of(Column... columns) {
        return new DatasetSchema(List.of(columns), AdditionalColumns.UNKNOWN);
    }

    /**
     * This schema, with no column other than its own: {@link AdditionalColumns#CLOSED}.
     *
     * @return the new schema
     */
    public DatasetSchema closed() {
        return new DatasetSchema(columns, AdditionalColumns.CLOSED);
    }

    /**
     * This schema, with other columns allowed: {@link AdditionalColumns#OPEN}.
     *
     * @return the new schema
     */
    public DatasetSchema open() {
        return new DatasetSchema(columns, AdditionalColumns.OPEN);
    }
}
