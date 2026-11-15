package dev.brokoli.pipeline;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A call the server makes when a run of the pipeline starts, succeeds or fails: it posts the run's event as JSON
 * to the hook's URL. A hook is given to {@link Pipeline.Builder#onStart(Hook)},
 * {@link Pipeline.Builder#onSuccess(Hook)} or {@link Pipeline.Builder#onFailure(Hook)}:
 *
 * <pre>{@code
 * Hook.slack("https://hooks.slack.com/services/T0/B0/x").extra(Map.of("channel", "#etl"))
 * }</pre>
 *
 * @param type {@code "webhook"}, {@code "slack"} or {@code "email"}
 * @param url the URL the server posts to
 * @param enabled whether the server makes the call
 * @param extra settings the server keeps with the hook, such as a Slack channel, none unless set
 */
public record Hook(String type, String url, boolean enabled, Map<String, String> extra) {

    private static final String WEBHOOK = "webhook";
    private static final String SLACK = "slack";
    private static final String EMAIL = "email";
    private static final List<String> TYPES = List.of(WEBHOOK, SLACK, EMAIL);

    /**
     * Creates the hook, with a copy of its settings.
     *
     * @param type the hook's type
     * @param url the URL the server posts to
     * @param enabled whether the call is made
     * @param extra the settings kept with the hook
     * @throws IllegalArgumentException if the type is not {@code webhook}, {@code slack} or {@code email}, or the
     *         URL is empty
     * @throws NullPointerException if a value, or a name or value in the settings, is {@code null}
     */
    public Hook {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(url, "url");
        extra = Map.copyOf(extra);
        if (!TYPES.contains(type)) {
            throw new IllegalArgumentException("hook type " + type + " is not one of " + String.join(", ", TYPES));
        }
        if (url.isEmpty()) {
            throw new IllegalArgumentException("a " + type + " hook needs a url");
        }
    }

    /**
     * An enabled hook that posts to a URL.
     *
     * @param url the URL, such as {@code "https://hooks.example/oncall"}
     * @return the hook
     * @throws IllegalArgumentException if the URL is empty
     */
    public static Hook webhook(String url) {
        return new Hook(WEBHOOK, url, true, Map.of());
    }

    /**
     * An enabled hook of type {@code slack}, which the server posts to.
     *
     * @param url the URL, such as a Slack incoming webhook's
     * @return the hook
     * @throws IllegalArgumentException if the URL is empty
     */
    public static Hook slack(String url) {
        return new Hook(SLACK, url, true, Map.of());
    }

    /**
     * An enabled hook of type {@code email}, which the server posts to.
     *
     * @param url the URL
     * @return the hook
     * @throws IllegalArgumentException if the URL is empty
     */
    public static Hook email(String url) {
        return new Hook(EMAIL, url, true, Map.of());
    }

    /**
     * This hook, kept in the pipeline but not called.
     *
     * @return the new hook
     */
    public Hook disabled() {
        return new Hook(type, url, false, extra);
    }

    /**
     * This hook with other settings of its type.
     *
     * @param extra the settings by name, such as {@code Map.of("channel", "#etl")}
     * @return the new hook
     */
    public Hook extra(Map<String, String> extra) {
        return new Hook(type, url, enabled, extra);
    }
}
