package dev.brokoli.pipeline;

/**
 * Whether a dataset may have columns its {@link DatasetSchema} does not declare.
 */
public enum AdditionalColumns {

    /**
     * The dataset has the declared columns and no other.
     */
    CLOSED("closed"),

    /**
     * The dataset has the declared columns and may have others.
     */
    OPEN("open"),

    /**
     * The declared columns may not be all the dataset has. The default, as in the Python SDK.
     */
    UNKNOWN("unknown");

    private final String irValue;

    AdditionalColumns(String irValue) {
        this.irValue = irValue;
    }

    String irValue() {
        return irValue;
    }
}
