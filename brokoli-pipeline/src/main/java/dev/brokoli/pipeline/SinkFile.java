package dev.brokoli.pipeline;

import java.util.Objects;

/**
 * The options of a {@code sink_file} node, which writes its input to a CSV, JSON or Parquet file. Start from
 * {@link #options()} and change what differs:
 *
 * <pre>{@code
 * SinkFile.options().path("/data/out.csv").format("csv")
 * }</pre>
 *
 * @param path the file to write, empty unless set; the server refuses a {@code sink_file} without one
 * @param format the file's format, such as {@code "csv"} or {@code "parquet"}, {@code "csv"} unless set
 */
public record SinkFile(String path, String format) {

    private static final String CSV = "csv";

    /**
     * Creates the options.
     *
     * @param path the file to write
     * @param format the file's format
     * @throws NullPointerException if a value is {@code null}
     */
    public SinkFile {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(format, "format");
    }

    /**
     * The default options: no path, and the {@code "csv"} format.
     *
     * @return the default options
     */
    public static SinkFile options() {
        return new SinkFile("", CSV);
    }

    /**
     * These options with another file to write.
     *
     * @param path the file to write, such as {@code "/data/out.csv"}
     * @return the new options
     */
    public SinkFile path(String path) {
        return new SinkFile(path, format);
    }

    /**
     * These options with another format.
     *
     * @param format the file's format, such as {@code "parquet"}
     * @return the new options
     */
    public SinkFile format(String format) {
        return new SinkFile(path, format);
    }
}
