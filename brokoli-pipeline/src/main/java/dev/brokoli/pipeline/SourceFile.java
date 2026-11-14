package dev.brokoli.pipeline;

import java.util.Objects;

/**
 * The options of a {@code source_file} node, which reads a CSV, JSON, Excel or XML file. Start from
 * {@link #options()} and change what differs:
 *
 * <pre>{@code
 * SourceFile.options().path("/data/sales.csv").format("csv")
 * }</pre>
 *
 * @param path the file to read, empty unless set; the server refuses a {@code source_file} without one
 * @param format the file's format, such as {@code "csv"} or {@code "json"}, {@code "csv"} unless set
 */
public record SourceFile(String path, String format) {

    private static final String CSV = "csv";

    /**
     * Creates the options.
     *
     * @param path the file to read
     * @param format the file's format
     * @throws NullPointerException if a value is {@code null}
     */
    public SourceFile {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(format, "format");
    }

    /**
     * The default options: no path, and the {@code "csv"} format.
     *
     * @return the default options
     */
    public static SourceFile options() {
        return new SourceFile("", CSV);
    }

    /**
     * These options with another file to read.
     *
     * @param path the file to read, such as {@code "/data/sales.csv"}
     * @return the new options
     */
    public SourceFile path(String path) {
        return new SourceFile(path, format);
    }

    /**
     * These options with another format.
     *
     * @param format the file's format, such as {@code "json"}
     * @return the new options
     */
    public SourceFile format(String format) {
        return new SourceFile(path, format);
    }
}
