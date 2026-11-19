package dev.brokoli.pipeline;

/**
 * The language of a {@code code} node's script.
 */
public enum CodeLanguage {

    /**
     * Python, run by the worker's Python interpreter or the one {@link Code#pythonPath(String)} names.
     */
    PYTHON("python"),

    /**
     * TypeScript, run by Node 20 or later, the worker's or the one {@link Code#nodePath(String)} names.
     */
    TYPESCRIPT("typescript");

    private final String irValue;

    CodeLanguage(String irValue) {
        this.irValue = irValue;
    }

    String irValue() {
        return irValue;
    }
}
