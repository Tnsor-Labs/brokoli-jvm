package dev.brokoli.pipeline;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The options of a {@code transform} node, which runs its rules over its input in order. Each rule is a map in the
 * shape of the server's transform rules, such as {@code rename} with a {@code mapping} of old to new column names,
 * or {@code filter_rows} with a {@code condition}:
 *
 * <pre>{@code
 * Transform.options().rules(List.of(
 *         Map.of("type", "rename", "mapping", Map.of("amt", "amount")),
 *         Map.of("type", "filter", "condition", "amount > 0")))
 * }</pre>
 *
 * <p>A {@code rename} or {@code rename_columns} rule may give one column as {@code from} and {@code to} instead of
 * a {@code mapping}; the pipeline writes it as a {@code mapping}. A {@code transform} runs in no pool, as in the
 * Python SDK.
 *
 * @param rules the rules, none unless set
 * @param node the options every node has, with no pool
 */
public record Transform(List<Map<String, Object>> rules, NodeOptions node) {

    /**
     * Creates the options, with a copy of each rule.
     *
     * @param rules the rules
     * @param node the options every node has
     * @throws IllegalArgumentException if the node options have a pool
     * @throws NullPointerException if a value, or a rule in the list, is {@code null}
     */
    public Transform {
        rules = rules.stream()
                .map(rule -> Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(rule, "rule"))))
                .toList();
        Objects.requireNonNull(node, "node");
        node.pool().ifPresent(pool -> {
            throw new IllegalArgumentException("a transform node runs in no pool, but its options have the pool "
                    + pool);
        });
    }

    /**
     * The default options: no rules, and {@link NodeOptions#defaults()}.
     *
     * @return the default options
     */
    public static Transform options() {
        return new Transform(List.of(), NodeOptions.defaults());
    }

    /**
     * These options with other rules.
     *
     * @param rules the rules, in the order they run; a value in a rule is {@code null}, a {@code String}, a
     *        {@code Boolean}, an {@code Integer}, a {@code Long}, a {@code BigInteger}, a finite {@code Double}, a
     *        {@code List} of such values, or a {@code Map} from {@code String} to such values
     * @return the new options
     */
    public Transform rules(List<? extends Map<String, ?>> rules) {
        return new Transform(rules.stream()
                .<Map<String, Object>>map(rule -> new LinkedHashMap<>(Objects.requireNonNull(rule, "rule")))
                .toList(), node);
    }

    /**
     * These options with a node key, see {@link NodeOptions#nodeKey(String)}.
     *
     * @param nodeKey the key, such as {@code "clean"}
     * @return the new options
     * @throws IllegalArgumentException if the key does not match {@code [a-z][a-z0-9_-]{0,63}}
     */
    public Transform nodeKey(String nodeKey) {
        return new Transform(rules, node.nodeKey(nodeKey));
    }

    /**
     * These options with retries spaced by {@link Backoff#EXPONENTIAL}.
     *
     * @param retries how many times the server retries the node after it fails
     * @return the new options
     * @throws IllegalArgumentException if the retries are negative
     */
    public Transform retries(int retries) {
        return retries(retries, Backoff.EXPONENTIAL);
    }

    /**
     * These options with retries spaced by {@code backoff}.
     *
     * @param retries how many times the server retries the node after it fails
     * @param backoff how the server spaces the retries
     * @return the new options
     * @throws IllegalArgumentException if the retries are negative
     */
    public Transform retries(int retries, Backoff backoff) {
        return new Transform(rules, node.retries(retries, backoff));
    }

    /**
     * These options with a delay before the first retry.
     *
     * @param retryDelay the delay, a whole number of milliseconds
     * @return the new options
     * @throws IllegalArgumentException if the delay is negative or not a whole number of milliseconds
     */
    public Transform retryDelay(Duration retryDelay) {
        return new Transform(rules, node.retryDelay(retryDelay));
    }

    /**
     * These options with a timeout for one attempt.
     *
     * @param timeout the timeout, a whole number of seconds
     * @return the new options
     * @throws IllegalArgumentException if the timeout is negative or not a whole number of seconds
     */
    public Transform timeout(Duration timeout) {
        return new Transform(rules, node.timeout(timeout));
    }
}
