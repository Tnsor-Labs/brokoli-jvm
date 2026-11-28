package dev.brokoli.pipeline;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The type of a dataset column or of a record field, written as a descriptor of core's portable type system. Each
 * kind has its factory:
 *
 * <pre>{@code
 * DataType.int64()
 * DataType.string().nullable()
 * DataType.decimal(18, 2)
 * DataType.enumOf("new", "paid")
 * DataType.array(DataType.string())
 * DataType.map(DataType.float64())
 * DataType.record(RecordField.of("city", DataType.string()), RecordField.of("zip", DataType.string()).optional())
 * }</pre>
 *
 * <p>A type does not take a null value unless {@link #nullable()} makes it so. A union has no factory, because core
 * refuses it in a dataset schema. Two types are equal when they write the same descriptor.
 */
public final class DataType {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private static final String BOOLEAN = "boolean";
    private static final String INT64 = "int64";
    private static final String FLOAT64 = "float64";
    private static final String DECIMAL = "decimal";
    private static final String STRING = "string";
    private static final String BYTES = "bytes";
    private static final String DATE = "date";
    private static final String TIMESTAMP = "timestamp";
    private static final String DURATION = "duration";
    private static final String JSON_KIND = "json";
    private static final String UNKNOWN = "unknown";
    private static final String ENUM = "enum";
    private static final String ARRAY = "array";
    private static final String MAP = "map";
    private static final String RECORD = "record";

    private final ObjectNode descriptor;

    private DataType(ObjectNode descriptor) {
        this.descriptor = descriptor;
    }

    /**
     * A {@code boolean}.
     *
     * @return the type
     */
    public static DataType bool() {
        return of(BOOLEAN);
    }

    /**
     * An {@code int64}, a 64-bit integer.
     *
     * @return the type
     */
    public static DataType int64() {
        return of(INT64);
    }

    /**
     * A {@code float64}, a 64-bit floating-point number.
     *
     * @return the type
     */
    public static DataType float64() {
        return of(FLOAT64);
    }

    /**
     * A {@code decimal} with no precision or scale.
     *
     * @return the type
     */
    public static DataType decimal() {
        return of(DECIMAL);
    }

    /**
     * A {@code decimal} with a precision and a scale.
     *
     * @param precision the number of digits, at least 1, such as {@code 18}
     * @param scale the number of digits after the point, from 0 to the precision, such as {@code 2}
     * @return the type
     * @throws IllegalArgumentException if the precision is under 1, or the scale is under 0 or over the precision
     */
    public static DataType decimal(int precision, int scale) {
        if (precision < 1) {
            throw new IllegalArgumentException("the precision of a decimal is at least 1, but it is " + precision);
        }
        if (scale < 0) {
            throw new IllegalArgumentException("the scale of a decimal is at least 0, but it is " + scale);
        }
        if (scale > precision) {
            throw new IllegalArgumentException("the scale " + scale + " of a decimal is over its precision "
                    + precision);
        }
        ObjectNode descriptor = kind(DECIMAL);
        descriptor.put(IrFields.PRECISION, precision);
        descriptor.put(IrFields.SCALE, scale);
        return new DataType(descriptor);
    }

    /**
     * A {@code string}.
     *
     * @return the type
     */
    public static DataType string() {
        return of(STRING);
    }

    /**
     * A {@code bytes}, a byte string.
     *
     * @return the type
     */
    public static DataType bytes() {
        return of(BYTES);
    }

    /**
     * A {@code date}, with no time of day.
     *
     * @return the type
     */
    public static DataType date() {
        return of(DATE);
    }

    /**
     * A {@code timestamp}, an instant.
     *
     * @return the type
     */
    public static DataType timestamp() {
        return of(TIMESTAMP);
    }

    /**
     * A {@code duration}, an amount of time.
     *
     * @return the type
     */
    public static DataType duration() {
        return of(DURATION);
    }

    /**
     * A {@code json}, any JSON value.
     *
     * @return the type
     */
    public static DataType json() {
        return of(JSON_KIND);
    }

    /**
     * An {@code unknown}, a value whose type is not declared.
     *
     * @return the type
     */
    public static DataType unknown() {
        return of(UNKNOWN);
    }

    /**
     * An {@code enum}, one string of a fixed set.
     *
     * @param values the strings, in their order, such as {@code "new", "paid"}
     * @return the type
     * @throws IllegalArgumentException if there is no value
     * @throws NullPointerException if a value is {@code null}
     */
    public static DataType enumOf(String... values) {
        List<String> strings = List.of(values);
        if (strings.isEmpty()) {
            throw new IllegalArgumentException("an enum needs at least one value");
        }
        ObjectNode descriptor = kind(ENUM);
        ArrayNode list = descriptor.putArray(IrFields.VALUES);
        strings.forEach(list::add);
        return new DataType(descriptor);
    }

    /**
     * An {@code array} of values of one type.
     *
     * @param items the type of each value, such as {@code DataType.string()}
     * @return the type
     * @throws NullPointerException if the type is {@code null}
     */
    public static DataType array(DataType items) {
        ObjectNode descriptor = kind(ARRAY);
        descriptor.set(IrFields.ITEMS, Objects.requireNonNull(items, "items").toJson());
        return new DataType(descriptor);
    }

    /**
     * A {@code map} from string keys to values of one type.
     *
     * @param values the type of each value, such as {@code DataType.float64()}
     * @return the type
     * @throws NullPointerException if the type is {@code null}
     */
    public static DataType map(DataType values) {
        ObjectNode descriptor = kind(MAP);
        descriptor.put(IrFields.KEYS, STRING);
        descriptor.set(IrFields.VALUES, Objects.requireNonNull(values, "values").toJson());
        return new DataType(descriptor);
    }

    /**
     * A {@code record} with named fields, in their order, and no other field.
     *
     * @param fields the fields, such as {@code RecordField.of("city", DataType.string())}
     * @return the type
     * @throws IllegalArgumentException if two fields have the same name
     * @throws NullPointerException if a field is {@code null}
     */
    public static DataType record(RecordField... fields) {
        List<RecordField> list = List.of(fields);
        Set<String> names = new HashSet<>();
        ObjectNode descriptor = kind(RECORD);
        ArrayNode fieldList = descriptor.putArray(IrFields.FIELDS);
        for (RecordField field : list) {
            if (!names.add(field.name())) {
                throw new IllegalArgumentException("the record has two fields named " + field.name());
            }
            ObjectNode written = fieldList.addObject();
            written.put(IrFields.NAME, field.name());
            written.set(IrFields.TYPE, field.type().toJson());
            written.put(IrFields.REQUIRED, field.required());
        }
        descriptor.put(IrFields.ADDITIONAL_FIELDS, false);
        return new DataType(descriptor);
    }

    /**
     * This type, taking a null value too.
     *
     * @return the nullable type
     */
    public DataType nullable() {
        ObjectNode copy = descriptor.deepCopy();
        copy.put(IrFields.NULLABLE, true);
        return new DataType(copy);
    }

    /**
     * Whether this type and another write the same descriptor.
     *
     * @param other the other object
     * @return {@code true} if the other object is a type with the same descriptor
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof DataType type && descriptor.equals(type.descriptor);
    }

    /**
     * A hash of the descriptor.
     *
     * @return the hash
     */
    @Override
    public int hashCode() {
        return descriptor.hashCode();
    }

    /**
     * The descriptor as JSON.
     *
     * @return the descriptor, such as {@code {"kind":"string","nullable":true}}
     */
    @Override
    public String toString() {
        return descriptor.toString();
    }

    String kind() {
        return descriptor.get(IrFields.KIND).stringValue();
    }

    ObjectNode toJson() {
        return descriptor.deepCopy();
    }

    private static DataType of(String kind) {
        return new DataType(kind(kind));
    }

    private static ObjectNode kind(String kind) {
        ObjectNode descriptor = JSON.objectNode();
        descriptor.put(IrFields.KIND, kind);
        return descriptor;
    }
}
