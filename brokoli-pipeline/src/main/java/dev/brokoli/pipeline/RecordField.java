package dev.brokoli.pipeline;

import java.util.Objects;

/**
 * A field of a {@link DataType#record(RecordField...) record} type. A field is required unless
 * {@link #optional()} makes it optional:
 *
 * <pre>{@code
 * RecordField.of("city", DataType.string())
 * RecordField.of("zip", DataType.string()).optional()
 * }</pre>
 *
 * <p>A required field is always in the record and an optional one may be left out. Whether its value can be null
 * is up to its type: {@link DataType#nullable()}.
 *
 * @param name the field's name
 * @param type the field's type
 * @param required whether the field is always in the record
 */
public record RecordField(String name, DataType type, boolean required) {

    /**
     * Creates the field.
     *
     * @param name the field's name
     * @param type the field's type
     * @param required whether the field is always in the record
     * @throws IllegalArgumentException if the name is empty
     * @throws NullPointerException if a value is {@code null}
     */
    public RecordField {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        if (name.isEmpty()) {
            throw new IllegalArgumentException("a record field needs a name");
        }
    }

    /**
     * A required field.
     *
     * @param name the field's name, such as {@code "city"}
     * @param type the field's type, such as {@code DataType.string()}
     * @return the field
     * @throws IllegalArgumentException if the name is empty
     */
    public static RecordField of(String name, DataType type) {
        return new RecordField(name, type, true);
    }

    /**
     * This field, which the record may leave out.
     *
     * @return the optional field
     */
    public RecordField optional() {
        return new RecordField(name, type, false);
    }
}
