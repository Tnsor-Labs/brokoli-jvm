package dev.brokoli.pipeline;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The options of a {@code bash} node, which runs a trusted Bash command as the worker's OS user and passes its
 * input through unchanged. The deployment must allow the {@code bash} node type. Put dynamic values in
 * {@link #env()} and quote their use in the command, because the server refuses platform references in it:
 *
 * <pre>{@code
 * Bash.options()
 *         .command("./refresh.sh --since \"$SINCE\"")
 *         .env(Map.of("SINCE", "${param.since}"))
 *         .timeout(Duration.ofMinutes(10))
 * }</pre>
 *
 * @param command the command, empty unless set; the server refuses a {@code bash} node without one
 * @param workingDir the directory the command runs in, if set
 * @param env the environment variables the command gets, none unless set
 * @param maxMemoryMb the most memory the command may use, in megabytes, if set
 * @param maxCpuTime the most CPU time the command may use, written in seconds, if set
 * @param node the options every node has
 */
public record Bash(String command, Optional<String> workingDir, Map<String, String> env, OptionalInt maxMemoryMb,
        Optional<Duration> maxCpuTime, NodeOptions node) implements WithPool<Bash> {

    /**
     * Creates the options, with a copy of the environment.
     *
     * @param command the command
     * @param workingDir the working directory, if set
     * @param env the environment variables
     * @param maxMemoryMb the memory limit in megabytes, if set
     * @param maxCpuTime the CPU time limit, if set
     * @param node the options every node has
     * @throws IllegalArgumentException if the memory limit is not positive, or the CPU time limit is negative or
     *         not a whole number of seconds
     * @throws NullPointerException if a value, or a name or value in the environment, is {@code null}
     */
    public Bash {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(workingDir, "workingDir");
        env = Map.copyOf(env);
        Objects.requireNonNull(maxMemoryMb, "maxMemoryMb");
        Objects.requireNonNull(maxCpuTime, "maxCpuTime");
        Objects.requireNonNull(node, "node");
        maxMemoryMb.ifPresent(Bash::requireMemory);
        maxCpuTime.ifPresent(Bash::requireCpuTime);
    }

    /**
     * The default options: no command, working directory, environment or limits, and
     * {@link NodeOptions#defaults()}.
     *
     * @return the default options
     */
    public static Bash options() {
        return new Bash("", Optional.empty(), Map.of(), OptionalInt.empty(), Optional.empty(), NodeOptions.defaults());
    }

    /**
     * These options with another command.
     *
     * @param command the command, such as {@code "./refresh.sh"}
     * @return the new options
     */
    public Bash command(String command) {
        return new Bash(command, workingDir, env, maxMemoryMb, maxCpuTime, node);
    }

    /**
     * These options with the directory the command runs in.
     *
     * @param workingDir the directory, such as {@code "/data/jobs"}
     * @return the new options
     */
    public Bash workingDir(String workingDir) {
        return new Bash(command, Optional.of(Objects.requireNonNull(workingDir, "workingDir")), env, maxMemoryMb,
                maxCpuTime, node);
    }

    /**
     * These options with other environment variables.
     *
     * @param env the variables by name, such as {@code Map.of("SINCE", "${param.since}")}
     * @return the new options
     */
    public Bash env(Map<String, String> env) {
        return new Bash(command, workingDir, env, maxMemoryMb, maxCpuTime, node);
    }

    /**
     * These options with a memory limit.
     *
     * @param maxMemoryMb the most memory the command may use, in megabytes
     * @return the new options
     * @throws IllegalArgumentException if the limit is not positive
     */
    public Bash maxMemoryMb(int maxMemoryMb) {
        return new Bash(command, workingDir, env, OptionalInt.of(maxMemoryMb), maxCpuTime, node);
    }

    /**
     * These options with a CPU time limit.
     *
     * @param maxCpuTime the most CPU time the command may use, a whole number of seconds
     * @return the new options
     * @throws IllegalArgumentException if the limit is negative or not a whole number of seconds
     */
    public Bash maxCpuTime(Duration maxCpuTime) {
        return new Bash(command, workingDir, env, maxMemoryMb,
                Optional.of(Objects.requireNonNull(maxCpuTime, "maxCpuTime")), node);
    }

    /**
     * These options with other options every node has.
     *
     * @param node the options every node has
     * @return the new options
     */
    @Override
    public Bash node(NodeOptions node) {
        return new Bash(command, workingDir, env, maxMemoryMb, maxCpuTime, node);
    }

    private static void requireMemory(int maxMemoryMb) {
        if (maxMemoryMb <= 0) {
            throw new IllegalArgumentException("memory limit " + maxMemoryMb + " MB is not positive");
        }
    }

    private static void requireCpuTime(Duration maxCpuTime) {
        NodeOptions.requireNotNegative("CPU time limit", maxCpuTime);
        if (maxCpuTime.getNano() != 0) {
            throw new IllegalArgumentException("CPU time limit " + maxCpuTime
                    + " is not a whole number of seconds, which the server takes");
        }
    }
}
