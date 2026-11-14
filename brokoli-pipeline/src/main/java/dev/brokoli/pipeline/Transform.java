package dev.brokoli.pipeline;

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
 * a {@code mapping}; the pipeline writes it as a {@code mapping}.
 *
 * @param rules the rules, none unless set
 */
public record Transform(List<Map<String, Object>> rules) {

    /**
     * Creates the options, with a copy of each rule.
     *
     * @param rules the rules
     * @throws NullPointerException if the list or a rule in it is {@code null}
     */
    public Transform {
        rules = rules.stream()
                .map(rule -> Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(rule, "rule"))))
                .toList();
    }

    /**
     * The default options: no rules.
     *
     * @return the default options
     */
    public static Transform options() {
        return new Transform(List.of());
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
                .toList());
    }
}
