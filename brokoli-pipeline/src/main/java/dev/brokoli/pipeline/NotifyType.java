package dev.brokoli.pipeline;

/**
 * Where a {@code notify} node sends its message.
 */
public enum NotifyType {

    /**
     * A webhook: the server posts the pipeline's name, the run's id, the message, the input's row count and the
     * time as JSON to the URL.
     */
    WEBHOOK("webhook"),

    /**
     * A Slack incoming webhook: the server posts the message, and the channel if set, to the URL.
     */
    SLACK("slack");

    private final String irValue;

    NotifyType(String irValue) {
        this.irValue = irValue;
    }

    String irValue() {
        return irValue;
    }
}
