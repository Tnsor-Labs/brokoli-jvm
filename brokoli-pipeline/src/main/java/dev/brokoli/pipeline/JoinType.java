package dev.brokoli.pipeline;

/**
 * Which rows a {@code join} node keeps.
 */
public enum JoinType {

    /**
     * The rows whose key is on both sides. The default, as in the Python SDK.
     */
    INNER("inner"),

    /**
     * Every left row, with the right columns empty where no right row has its key.
     */
    LEFT("left"),

    /**
     * Every right row, with the left columns empty where no left row has its key.
     */
    RIGHT("right"),

    /**
     * Every row of both sides, with the other side's columns empty where it has no row with the key.
     */
    FULL("full");

    private final String irValue;

    JoinType(String irValue) {
        this.irValue = irValue;
    }

    String irValue() {
        return irValue;
    }
}
