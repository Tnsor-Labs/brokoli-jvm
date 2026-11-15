package dev.brokoli.pipeline;

/**
 * How the server spaces a node's retries, each starting from the node's retry delay.
 */
public enum Backoff {

    /**
     * Doubles the delay after each attempt. The default, as in the Python SDK.
     */
    EXPONENTIAL("exponential"),

    /**
     * Grows the delay by the retry delay after each attempt.
     */
    LINEAR("linear"),

    /**
     * Waits the retry delay before every attempt.
     */
    FIXED("fixed");

    private final String irValue;

    Backoff(String irValue) {
        this.irValue = irValue;
    }

    String irValue() {
        return irValue;
    }
}
