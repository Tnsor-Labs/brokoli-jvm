package dev.brokoli.pipeline;

/**
 * What a {@code join} node does when a right column has the name of a left column. The key is not a collision
 * when both sides name it the same.
 */
public enum CollisionPolicy {

    /**
     * When any right column collides, every right column is written as {@code right_} and its name. The default,
     * as in the Python SDK.
     */
    PREFIX("prefix"),

    /**
     * The server fails the join when a right column collides.
     */
    ERROR("error"),

    /**
     * Every right column is written as the join's right alias, {@code _} and its name. Set by
     * {@link Join#rightAlias(String)}, which gives the alias with it.
     */
    ALIAS("alias");

    private final String irValue;

    CollisionPolicy(String irValue) {
        this.irValue = irValue;
    }

    String irValue() {
        return irValue;
    }
}
