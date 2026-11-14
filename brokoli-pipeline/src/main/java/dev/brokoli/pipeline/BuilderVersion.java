package dev.brokoli.pipeline;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

final class BuilderVersion {

    private static final String RESOURCE = "brokoli-pipeline.properties";
    private static final String VERSION = "version";
    private static final String PREFIX = "brokoli-jvm/";

    static final String BUILDER = PREFIX + version();

    private BuilderVersion() {
    }

    private static String version() {
        try (InputStream in = BuilderVersion.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("brokoli-pipeline has no " + RESOURCE + " next to "
                        + BuilderVersion.class.getName() + "; the jar is incomplete");
            }
            Properties properties = new Properties();
            properties.load(in);
            return properties.getProperty(VERSION);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
