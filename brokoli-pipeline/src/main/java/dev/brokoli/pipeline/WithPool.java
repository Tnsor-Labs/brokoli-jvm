package dev.brokoli.pipeline;

/**
 * The options every node has, with the pool, on the options of a node type that runs in a pool.
 *
 * @param <SELF> the options type, such as {@link SourceFile}
 */
public interface WithPool<SELF extends WithPool<SELF>> extends WithNodeOptions<SELF> {

    /**
     * These options with the pool the node runs in.
     *
     * @param pool the pool, such as {@code "files"}
     * @return the new options
     */
    default SELF pool(String pool) {
        return node(node().pool(pool));
    }
}
