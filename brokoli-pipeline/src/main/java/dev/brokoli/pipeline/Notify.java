package dev.brokoli.pipeline;

import java.util.Objects;
import java.util.Optional;

/**
 * The options of a {@code notify} node, which sends a message to a webhook or to Slack and passes its input through
 * unchanged. Start from {@link #webhook(String)} or {@link #slack(String)} and change what differs:
 *
 * <pre>{@code
 * Notify.slack("https://hooks.slack.com/services/T0/B0/x")
 *         .message("Pipeline {{pipeline}} loaded {{rows}} rows")
 *         .channel("#data-alerts")
 * }</pre>
 *
 * <p>A {@code notify} node runs in no pool, as in the Python SDK.
 *
 * @param type where the message goes
 * @param webhookUrl the URL the server posts the message to
 * @param message the message, in which the server replaces {@code {{pipeline}}}, {@code {{run_id}}} and
 *        {@code {{rows}}}, {@code "Pipeline <name> completed"} on the server unless set
 * @param channel the Slack channel the message goes to, which only {@link NotifyType#SLACK} has
 * @param node the options every node has, with no pool
 */
public record Notify(NotifyType type, String webhookUrl, Optional<String> message, Optional<String> channel,
        NodeOptions node) implements WithNodeOptions<Notify> {

    /**
     * Creates the options.
     *
     * @param type where the message goes
     * @param webhookUrl the URL
     * @param message the message, if set
     * @param channel the Slack channel, if set
     * @param node the options every node has
     * @throws IllegalArgumentException if the URL is empty, a webhook has a channel, or the node options have a pool
     * @throws NullPointerException if a value is {@code null}
     */
    public Notify {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(webhookUrl, "webhookUrl");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(node, "node");
        if (webhookUrl.isEmpty()) {
            throw new IllegalArgumentException("a " + type.irValue() + " notify node needs a url");
        }
        if (type != NotifyType.SLACK && channel.isPresent()) {
            throw new IllegalArgumentException("the channel " + channel.get() + " is only sent to Slack, but the"
                    + " notify type is " + type.irValue());
        }
        node.pool().ifPresent(pool -> {
            throw new IllegalArgumentException("a notify node runs in no pool, but its options have the pool "
                    + pool);
        });
    }

    /**
     * The options of a message posted as JSON to a webhook, with {@link NodeOptions#defaults()}.
     *
     * @param webhookUrl the URL, such as {@code "https://hooks.example.com/notify"}
     * @return the options
     * @throws IllegalArgumentException if the URL is empty
     * @throws NullPointerException if the URL is {@code null}
     */
    public static Notify webhook(String webhookUrl) {
        return new Notify(NotifyType.WEBHOOK, webhookUrl, Optional.empty(), Optional.empty(), NodeOptions.defaults());
    }

    /**
     * The options of a message posted to a Slack incoming webhook, with {@link NodeOptions#defaults()}.
     *
     * @param webhookUrl the incoming webhook's URL, such as {@code "https://hooks.slack.com/services/T0/B0/x"}
     * @return the options
     * @throws IllegalArgumentException if the URL is empty
     * @throws NullPointerException if the URL is {@code null}
     */
    public static Notify slack(String webhookUrl) {
        return new Notify(NotifyType.SLACK, webhookUrl, Optional.empty(), Optional.empty(), NodeOptions.defaults());
    }

    /**
     * These options with another message.
     *
     * @param message the message, such as {@code "Pipeline {{pipeline}} loaded {{rows}} rows"}
     * @return the new options
     */
    public Notify message(String message) {
        return new Notify(type, webhookUrl, Optional.of(Objects.requireNonNull(message, "message")), channel, node);
    }

    /**
     * These options with the Slack channel the message goes to.
     *
     * @param channel the channel, such as {@code "#data-alerts"}
     * @return the new options
     * @throws IllegalArgumentException if the message goes to a webhook
     */
    public Notify channel(String channel) {
        return new Notify(type, webhookUrl, message, Optional.of(Objects.requireNonNull(channel, "channel")), node);
    }

    /**
     * These options with other options every node has.
     *
     * @param node the options every node has, with no pool
     * @return the new options
     * @throws IllegalArgumentException if the node options have a pool
     */
    @Override
    public Notify node(NodeOptions node) {
        return new Notify(type, webhookUrl, message, channel, node);
    }
}
