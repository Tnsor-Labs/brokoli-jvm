package dev.brokoli.pipeline;

import java.util.Objects;
import java.util.Optional;

/**
 * The options of a {@code join} node, which joins its left input with its right input on a key column of each.
 * Start from {@link #options()} and change what differs:
 *
 * <pre>{@code
 * Join.options().on("user_id").type(JoinType.LEFT)
 * Join.options().on("id", "user_id").rightAlias("user")
 * }</pre>
 *
 * <p>A {@code join} runs in no pool, as in the Python SDK. It writes no {@code schema}: the Python SDK writes the
 * schema it derives for the join's output when both inputs declare a dataset schema, and no node of this builder
 * declares one yet.
 *
 * @param leftKey the left input's key column, empty unless set
 * @param rightKey the right input's key column, empty unless set
 * @param type which rows the join keeps, {@link JoinType#INNER} unless set
 * @param collisionPolicy what the join does when a right column has the name of a left column,
 *        {@link CollisionPolicy#PREFIX} unless set
 * @param rightAlias the alias of the right input's columns, which only {@link CollisionPolicy#ALIAS} has
 * @param node the options every node has, with no pool
 */
public record Join(String leftKey, String rightKey, JoinType type, CollisionPolicy collisionPolicy,
        Optional<String> rightAlias, NodeOptions node) implements WithNodeOptions<Join> {

    /**
     * Creates the options.
     *
     * @param leftKey the left key column
     * @param rightKey the right key column
     * @param type which rows the join keeps
     * @param collisionPolicy what the join does with a colliding column
     * @param rightAlias the alias of the right columns, if the policy is {@link CollisionPolicy#ALIAS}
     * @param node the options every node has
     * @throws IllegalArgumentException if the policy is {@link CollisionPolicy#ALIAS} with no alias or a blank one,
     *         another policy has an alias, or the node options have a pool
     * @throws NullPointerException if a value is {@code null}
     */
    public Join {
        Objects.requireNonNull(leftKey, "leftKey");
        Objects.requireNonNull(rightKey, "rightKey");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(collisionPolicy, "collisionPolicy");
        Objects.requireNonNull(rightAlias, "rightAlias");
        Objects.requireNonNull(node, "node");
        if (collisionPolicy == CollisionPolicy.ALIAS && rightAlias.isEmpty()) {
            throw new IllegalArgumentException("the alias collision policy needs the alias of the right columns;"
                    + " give it with rightAlias(\"right_row\")");
        }
        if (collisionPolicy != CollisionPolicy.ALIAS && rightAlias.isPresent()) {
            throw new IllegalArgumentException("the right alias " + rightAlias.get() + " is only written with the"
                    + " alias collision policy, but the policy is " + collisionPolicy.irValue());
        }
        rightAlias.filter(String::isBlank).ifPresent(alias -> {
            throw new IllegalArgumentException("the right alias \"" + alias + "\" is blank");
        });
        node.pool().ifPresent(pool -> {
            throw new IllegalArgumentException("a join node runs in no pool, but its options have the pool " + pool);
        });
    }

    /**
     * The default options: no key columns, {@link JoinType#INNER}, {@link CollisionPolicy#PREFIX}, and
     * {@link NodeOptions#defaults()}.
     *
     * @return the default options
     */
    public static Join options() {
        return new Join("", "", JoinType.INNER, CollisionPolicy.PREFIX, Optional.empty(), NodeOptions.defaults());
    }

    /**
     * These options with a key column that has the same name on both sides.
     *
     * @param column the key column, such as {@code "user_id"}
     * @return the new options
     */
    public Join on(String column) {
        return on(column, column);
    }

    /**
     * These options with a key column on each side. A name is used as it is, so a column whose name holds
     * {@code =} is read whole.
     *
     * @param leftKey the left input's key column, such as {@code "id"}
     * @param rightKey the right input's key column, such as {@code "user_id"}
     * @return the new options
     */
    public Join on(String leftKey, String rightKey) {
        return new Join(leftKey, rightKey, type, collisionPolicy, rightAlias, node);
    }

    /**
     * These options with another join type.
     *
     * @param type which rows the join keeps
     * @return the new options
     */
    public Join type(JoinType type) {
        return new Join(leftKey, rightKey, type, collisionPolicy, rightAlias, node);
    }

    /**
     * These options with colliding right columns prefixed or refused. The alias policy is set by
     * {@link #rightAlias(String)}, which gives the alias with it.
     *
     * @param collisionPolicy {@link CollisionPolicy#PREFIX} or {@link CollisionPolicy#ERROR}
     * @return the new options, with no right alias
     * @throws IllegalArgumentException if the policy is {@link CollisionPolicy#ALIAS}
     */
    public Join collisionPolicy(CollisionPolicy collisionPolicy) {
        if (collisionPolicy == CollisionPolicy.ALIAS) {
            throw new IllegalArgumentException("the alias collision policy needs the alias of the right columns;"
                    + " give it with rightAlias(\"right_row\") instead of collisionPolicy(CollisionPolicy.ALIAS)");
        }
        return new Join(leftKey, rightKey, type, collisionPolicy, Optional.empty(), node);
    }

    /**
     * These options with every right column written as the alias, {@code _} and its name, which sets
     * {@link CollisionPolicy#ALIAS}.
     *
     * @param rightAlias the alias, such as {@code "right_row"}
     * @return the new options
     * @throws IllegalArgumentException if the alias is blank
     */
    public Join rightAlias(String rightAlias) {
        return new Join(leftKey, rightKey, type, CollisionPolicy.ALIAS,
                Optional.of(Objects.requireNonNull(rightAlias, "rightAlias")), node);
    }

    /**
     * These options with other options every node has.
     *
     * @param node the options every node has
     * @return the new options
     * @throws IllegalArgumentException if the node options have a pool
     */
    @Override
    public Join node(NodeOptions node) {
        return new Join(leftKey, rightKey, type, collisionPolicy, rightAlias, node);
    }
}
