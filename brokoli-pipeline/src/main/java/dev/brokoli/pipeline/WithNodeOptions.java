package dev.brokoli.pipeline;

import java.time.Duration;

/**
 * The options every node has, on the options of one node type: a node key, retries, a retry delay and a timeout.
 * Each method returns new options of the same type, with every other option as it was.
 *
 * @param <SELF> the options type, such as {@link SourceFile}
 */
public interface WithNodeOptions<SELF extends WithNodeOptions<SELF>> {

    /**
     * The options every node has.
     *
     * @return the options every node has
     */
    NodeOptions node();

    /**
     * These options with other options every node has.
     *
     * @param node the options every node has
     * @return the new options
     */
    SELF node(NodeOptions node);

    /**
     * These options with a node key, see {@link NodeOptions#nodeKey(String)}.
     *
     * @param nodeKey the key, such as {@code "read-sales"}
     * @return the new options
     * @throws IllegalArgumentException if the key does not match {@code [a-z][a-z0-9_-]{0,63}}
     */
    default SELF nodeKey(String nodeKey) {
        return node(node().nodeKey(nodeKey));
    }

    /**
     * These options with retries spaced by {@link Backoff#EXPONENTIAL}.
     *
     * @param retries how many times the server retries the node after it fails
     * @return the new options
     * @throws IllegalArgumentException if the retries are negative
     */
    default SELF retries(int retries) {
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
    default SELF retries(int retries, Backoff backoff) {
        return node(node().retries(retries, backoff));
    }

    /**
     * These options with a delay before the first retry.
     *
     * @param retryDelay the delay, a whole number of milliseconds
     * @return the new options
     * @throws IllegalArgumentException if the delay is negative or not a whole number of milliseconds
     */
    default SELF retryDelay(Duration retryDelay) {
        return node(node().retryDelay(retryDelay));
    }

    /**
     * These options with a timeout for one attempt.
     *
     * @param timeout the timeout, a whole number of seconds
     * @return the new options
     * @throws IllegalArgumentException if the timeout is negative or not a whole number of seconds
     */
    default SELF timeout(Duration timeout) {
        return node(node().timeout(timeout));
    }
}
