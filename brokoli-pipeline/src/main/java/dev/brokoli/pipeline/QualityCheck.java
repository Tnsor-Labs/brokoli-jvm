package dev.brokoli.pipeline;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The options of a {@code quality_check} node, which checks its input against its rules. Each rule is a map in the
 * shape of the server's quality checks: a {@code rule} such as {@code not_null}, {@code unique}, {@code min} or
 * {@code row_count}, the {@code column} it checks, its {@code params}, and {@code on_failure}, {@code block} to stop
 * the run or {@code warn} to go on:
 *
 * <pre>{@code
 * QualityCheck.options().rules(List.of(
 *         Map.of("rule", "not_null", "column", "email", "on_failure", "block"),
 *         Map.of("rule", "min", "column", "age", "params", Map.of("min", 0), "on_failure", "warn")))
 * }</pre>
 *
 * <p>A {@code quality_check} runs in no pool, as in the Python SDK.
 *
 * @param rules the rules, none unless set
 * @param node the options every node has, with no pool
 */
public record QualityCheck(List<Map<String, Object>> rules, NodeOptions node)
        implements WithNodeOptions<QualityCheck> {

    /**
     * Creates the options, with a copy of each rule.
     *
     * @param rules the rules
     * @param node the options every node has
     * @throws IllegalArgumentException if the node options have a pool
     * @throws NullPointerException if a value, or a rule in the list, is {@code null}
     */
    public QualityCheck {
        rules = rules.stream()
                .map(rule -> Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(rule, "rule"))))
                .toList();
        Objects.requireNonNull(node, "node");
        node.pool().ifPresent(pool -> {
            throw new IllegalArgumentException("a quality_check node runs in no pool, but its options have the pool "
                    + pool);
        });
    }

    /**
     * The default options: no rules, and {@link NodeOptions#defaults()}.
     *
     * @return the default options
     */
    public static QualityCheck options() {
        return new QualityCheck(List.of(), NodeOptions.defaults());
    }

    /**
     * These options with other rules.
     *
     * @param rules the rules, in the order they run; a value in a rule is {@code null}, a {@code String}, a
     *        {@code Boolean}, an {@code Integer}, a {@code Long}, a {@code BigInteger}, a finite {@code Double}, a
     *        {@code List} of such values, or a {@code Map} from {@code String} to such values
     * @return the new options
     */
    public QualityCheck rules(List<? extends Map<String, ?>> rules) {
        return new QualityCheck(rules.stream()
                .<Map<String, Object>>map(rule -> new LinkedHashMap<>(Objects.requireNonNull(rule, "rule")))
                .toList(), node);
    }

    /**
     * These options with other options every node has.
     *
     * @param node the options every node has
     * @return the new options
     * @throws IllegalArgumentException if the node options have a pool
     */
    @Override
    public QualityCheck node(NodeOptions node) {
        return new QualityCheck(rules, node);
    }
}
