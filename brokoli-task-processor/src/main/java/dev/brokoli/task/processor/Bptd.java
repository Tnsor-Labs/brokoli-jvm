package dev.brokoli.task.processor;

final class Bptd {

    static final String CONTRACT = "contract";
    static final String TASK_INTERFACE = "brokoli.task-interface/v1";
    static final String INPUTS = "inputs";
    static final String OUTPUTS = "outputs";
    static final String INPUT_PORT = "input";
    static final String RESULT_PORT = "result";
    static final String VALUE = "value";
    static final String ROW = "row";
    static final String DATASET = "dataset";

    static final String KIND = "kind";
    static final String NULLABLE = "nullable";
    static final String MINIMUM = "minimum";
    static final String MAXIMUM = "maximum";
    static final String ITEMS = "items";
    static final String KEYS = "keys";
    static final String VALUES = "values";
    static final String FIELDS = "fields";
    static final String ADDITIONAL_FIELDS = "additional_fields";
    static final String NAME = "name";
    static final String TYPE = "type";
    static final String REQUIRED = "required";
    static final String DEFAULT = "default";

    static final String INT64 = "int64";
    static final String FLOAT64 = "float64";
    static final String BOOLEAN = "boolean";
    static final String STRING = "string";
    static final String DECIMAL = "decimal";
    static final String DATE = "date";
    static final String TIMESTAMP = "timestamp";
    static final String DURATION = "duration";
    static final String BYTES = "bytes";
    static final String ENUM = "enum";
    static final String ARRAY = "array";
    static final String MAP = "map";
    static final String RECORD = "record";
    static final String UNKNOWN = "unknown";

    static final String IDENTIFIER = "[A-Za-z_][A-Za-z0-9_]{0,127}";

    private Bptd() {
    }
}
