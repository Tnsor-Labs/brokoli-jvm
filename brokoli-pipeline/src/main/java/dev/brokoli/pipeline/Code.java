package dev.brokoli.pipeline;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The options of a {@code code} node, which runs a Python or TypeScript script on its input's rows. Start from
 * {@link #python(String)} or {@link #typescript(String)} and change what differs:
 *
 * <pre>{@code
 * Code.python("output_data = {'columns': columns, 'rows': rows}").timeout(Duration.ofMinutes(1))
 * Code.typescript("output_data = { columns, rows: rows.filter((row) => row.amount > 0) };").maxMemoryMb(256)
 * }</pre>
 *
 * <p>In either language the script reads its input as {@code rows} and {@code columns} and sets
 * {@code output_data} to the output's columns and rows. A run parameter cannot be written into the script as
 * {@code ${param.x}}: the server refuses it, because whoever starts a run would choose the code. The script reads
 * {@code params["x"]} in Python or {@code params.x} in TypeScript instead.
 *
 * @param language the script's language
 * @param script the script; the server refuses an empty one
 * @param pythonPath the Python interpreter that runs a Python script, if set
 * @param nodePath the Node executable that runs a TypeScript script, if set
 * @param maxMemoryMb the most memory the script may use, in megabytes, if set
 * @param maxCpuTime the most CPU time the script may use, written in seconds, if set
 * @param node the options every node has
 */
public record Code(CodeLanguage language, String script, Optional<String> pythonPath, Optional<String> nodePath,
        OptionalInt maxMemoryMb, Optional<Duration> maxCpuTime, NodeOptions node) implements WithPool<Code> {

    /**
     * Creates the options.
     *
     * @param language the language
     * @param script the script
     * @param pythonPath the Python interpreter, if set
     * @param nodePath the Node executable, if set
     * @param maxMemoryMb the memory limit in megabytes, if set
     * @param maxCpuTime the CPU time limit, if set
     * @param node the options every node has
     * @throws IllegalArgumentException if a TypeScript script has a Python interpreter, a Python script has a Node
     *         executable, the memory limit is not positive, or the CPU time limit is negative or not a whole number
     *         of seconds
     * @throws NullPointerException if a value is {@code null}
     */
    public Code {
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(script, "script");
        Objects.requireNonNull(pythonPath, "pythonPath");
        Objects.requireNonNull(nodePath, "nodePath");
        Objects.requireNonNull(maxMemoryMb, "maxMemoryMb");
        Objects.requireNonNull(maxCpuTime, "maxCpuTime");
        Objects.requireNonNull(node, "node");
        if (language != CodeLanguage.PYTHON && pythonPath.isPresent()) {
            throw new IllegalArgumentException("the Python interpreter " + pythonPath.get() + " only runs a Python"
                    + " script, but the script is " + language.irValue());
        }
        if (language != CodeLanguage.TYPESCRIPT && nodePath.isPresent()) {
            throw new IllegalArgumentException("the Node executable " + nodePath.get() + " only runs a TypeScript"
                    + " script, but the script is " + language.irValue());
        }
        maxMemoryMb.ifPresent(ResourceLimits::requireMemory);
        maxCpuTime.ifPresent(ResourceLimits::requireCpuTime);
    }

    /**
     * The options of a Python script, with {@link NodeOptions#defaults()}.
     *
     * @param script the script, such as {@code "output_data = {'columns': columns, 'rows': rows}"}
     * @return the options
     * @throws NullPointerException if the script is {@code null}
     */
    public static Code python(String script) {
        return new Code(CodeLanguage.PYTHON, script, Optional.empty(), Optional.empty(), OptionalInt.empty(),
                Optional.empty(), NodeOptions.defaults());
    }

    /**
     * The options of a TypeScript script, with {@link NodeOptions#defaults()}.
     *
     * @param script the script, such as {@code "output_data = { columns, rows };"}
     * @return the options
     * @throws NullPointerException if the script is {@code null}
     */
    public static Code typescript(String script) {
        return new Code(CodeLanguage.TYPESCRIPT, script, Optional.empty(), Optional.empty(), OptionalInt.empty(),
                Optional.empty(), NodeOptions.defaults());
    }

    /**
     * These options with the Python interpreter that runs the script.
     *
     * @param pythonPath the interpreter, such as {@code "/opt/venv/bin/python"}
     * @return the new options
     * @throws IllegalArgumentException if the script is not Python
     */
    public Code pythonPath(String pythonPath) {
        return new Code(language, script, Optional.of(Objects.requireNonNull(pythonPath, "pythonPath")), nodePath,
                maxMemoryMb, maxCpuTime, node);
    }

    /**
     * These options with the Node executable that runs the script, which must be Node 20 or later.
     *
     * @param nodePath the executable, such as {@code "/usr/bin/node"}
     * @return the new options
     * @throws IllegalArgumentException if the script is not TypeScript
     */
    public Code nodePath(String nodePath) {
        return new Code(language, script, pythonPath, Optional.of(Objects.requireNonNull(nodePath, "nodePath")),
                maxMemoryMb, maxCpuTime, node);
    }

    /**
     * These options with a memory limit.
     *
     * @param maxMemoryMb the most memory the script may use, in megabytes
     * @return the new options
     * @throws IllegalArgumentException if the limit is not positive
     */
    public Code maxMemoryMb(int maxMemoryMb) {
        return new Code(language, script, pythonPath, nodePath, OptionalInt.of(maxMemoryMb), maxCpuTime, node);
    }

    /**
     * These options with a CPU time limit.
     *
     * @param maxCpuTime the most CPU time the script may use, a whole number of seconds
     * @return the new options
     * @throws IllegalArgumentException if the limit is negative or not a whole number of seconds
     */
    public Code maxCpuTime(Duration maxCpuTime) {
        return new Code(language, script, pythonPath, nodePath, maxMemoryMb,
                Optional.of(Objects.requireNonNull(maxCpuTime, "maxCpuTime")), node);
    }

    /**
     * These options with other options every node has.
     *
     * @param node the options every node has
     * @return the new options
     */
    @Override
    public Code node(NodeOptions node) {
        return new Code(language, script, pythonPath, nodePath, maxMemoryMb, maxCpuTime, node);
    }
}
