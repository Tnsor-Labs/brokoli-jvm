package dev.brokoli.pipeline;

import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Pattern;

/**
 * The options every node has: its key, how the server retries it and times it out, and the pool it runs in. Each
 * node's options record holds one and changes it through its own methods, such as
 * {@code SourceFile.options().retries(3).timeout(Duration.ofMinutes(1))}.
 *
 * @param nodeKey the node's id in place of the one made from its name, if set
 * @param retries how many times the server retries the node after it fails, if set
 * @param retryBackoff how the server spaces the retries, written only with {@code retries}
 * @param retryDelay the delay before the first retry, written in milliseconds, if set
 * @param timeout how long one attempt may run, written in seconds, if set
 * @param pool the server's concurrency pool the node runs in, if set
 */
public record NodeOptions(Optional<String> nodeKey, OptionalInt retries, Backoff retryBackoff,
        Optional<Duration> retryDelay, Optional<Duration> timeout, Optional<String> pool) {

    private static final Pattern NODE_KEY = Pattern.compile("[a-z][a-z0-9_-]{0,63}");
    private static final int NANOS_PER_MILLI = 1_000_000;

    /**
     * Creates the options.
     *
     * @param nodeKey the node's id, if set
     * @param retries how many times the node is retried, if set
     * @param retryBackoff how the retries are spaced
     * @param retryDelay the delay before the first retry, if set
     * @param timeout how long one attempt may run, if set
     * @param pool the node's pool, if set
     * @throws IllegalArgumentException if the node key does not match {@code [a-z][a-z0-9_-]{0,63}}, the retries
     *         or a duration is negative, the retry delay is not a whole number of milliseconds, or the timeout is
     *         not a whole number of seconds
     * @throws NullPointerException if a value is {@code null}
     */
    public NodeOptions {
        Objects.requireNonNull(nodeKey, "nodeKey");
        Objects.requireNonNull(retries, "retries");
        Objects.requireNonNull(retryBackoff, "retryBackoff");
        Objects.requireNonNull(retryDelay, "retryDelay");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(pool, "pool");
        nodeKey.ifPresent(NodeOptions::requireNodeKey);
        retries.ifPresent(NodeOptions::requireRetries);
        retryDelay.ifPresent(NodeOptions::requireRetryDelay);
        timeout.ifPresent(NodeOptions::requireTimeout);
    }

    /**
     * The default options: an id made from the node's name, the server's retries, timeout and pool.
     *
     * @return the default options
     */
    public static NodeOptions defaults() {
        return new NodeOptions(Optional.empty(), OptionalInt.empty(), Backoff.EXPONENTIAL, Optional.empty(),
                Optional.empty(), Optional.empty());
    }

    /**
     * These options with a node key, which is the node's id in place of the one made from its name.
     *
     * @param nodeKey the key, such as {@code "refresh-export"}
     * @return the new options
     * @throws IllegalArgumentException if the key does not match {@code [a-z][a-z0-9_-]{0,63}}
     */
    public NodeOptions nodeKey(String nodeKey) {
        return new NodeOptions(Optional.of(Objects.requireNonNull(nodeKey, "nodeKey")), retries, retryBackoff,
                retryDelay, timeout, pool);
    }

    /**
     * These options with retries spaced by {@code backoff}.
     *
     * @param retries how many times the server retries the node after it fails
     * @param backoff how the server spaces the retries
     * @return the new options
     * @throws IllegalArgumentException if the retries are negative
     */
    public NodeOptions retries(int retries, Backoff backoff) {
        return new NodeOptions(nodeKey, OptionalInt.of(retries), backoff, retryDelay, timeout, pool);
    }

    /**
     * These options with a delay before the first retry.
     *
     * @param retryDelay the delay, a whole number of milliseconds
     * @return the new options
     * @throws IllegalArgumentException if the delay is negative or not a whole number of milliseconds
     */
    public NodeOptions retryDelay(Duration retryDelay) {
        return new NodeOptions(nodeKey, retries, retryBackoff,
                Optional.of(Objects.requireNonNull(retryDelay, "retryDelay")), timeout, pool);
    }

    /**
     * These options with a timeout for one attempt.
     *
     * @param timeout the timeout, a whole number of seconds
     * @return the new options
     * @throws IllegalArgumentException if the timeout is negative or not a whole number of seconds
     */
    public NodeOptions timeout(Duration timeout) {
        return new NodeOptions(nodeKey, retries, retryBackoff, retryDelay,
                Optional.of(Objects.requireNonNull(timeout, "timeout")), pool);
    }

    /**
     * These options with the pool the node runs in.
     *
     * @param pool the pool, such as {@code "warehouse"}
     * @return the new options
     */
    public NodeOptions pool(String pool) {
        return new NodeOptions(nodeKey, retries, retryBackoff, retryDelay, timeout,
                Optional.of(Objects.requireNonNull(pool, "pool")));
    }

    void writeRetriesAndTimeout(ObjectNode config) {
        retries.ifPresent(count -> {
            config.put(IrFields.MAX_RETRIES, count);
            config.put(IrFields.RETRY_BACKOFF, retryBackoff.irValue());
        });
        retryDelay.ifPresent(delay -> config.put(IrFields.RETRY_DELAY, delay.toMillis()));
        timeout.ifPresent(limit -> config.put(IrFields.TIMEOUT, limit.toSeconds()));
    }

    void writePool(ObjectNode config) {
        pool.ifPresent(name -> config.put(IrFields.POOL, name));
    }

    private static void requireNodeKey(String nodeKey) {
        if (!NODE_KEY.matcher(nodeKey).matches()) {
            throw new IllegalArgumentException("node key " + nodeKey + " does not match " + NODE_KEY.pattern());
        }
    }

    private static void requireRetries(int retries) {
        if (retries < 0) {
            throw new IllegalArgumentException("retries " + retries + " is negative");
        }
    }

    private static void requireRetryDelay(Duration retryDelay) {
        requireNotNegative("retry delay", retryDelay);
        if (retryDelay.getNano() % NANOS_PER_MILLI != 0) {
            throw new IllegalArgumentException("retry delay " + retryDelay
                    + " is not a whole number of milliseconds, which the server takes");
        }
    }

    private static void requireTimeout(Duration timeout) {
        requireNotNegative("timeout", timeout);
        if (timeout.getNano() != 0) {
            throw new IllegalArgumentException("timeout " + timeout
                    + " is not a whole number of seconds, which the server takes");
        }
    }

    static void requireNotNegative(String what, Duration duration) {
        if (duration.isNegative()) {
            throw new IllegalArgumentException(what + " " + duration + " is negative");
        }
    }
}
