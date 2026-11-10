package dev.brokoli.bundle;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

final class CoreContract {

    static final Path ROOT = Path.of(System.getProperty("brokoli.core-contract", "../core-contract"));
    static final Path BUNDLES = ROOT.resolve("bundles");
    static final Path INPUT = BUNDLES.resolve("input");

    private CoreContract() {
    }

    static byte[] resource(String name) {
        try (InputStream in = CoreContract.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("no test resource " + name);
            }
            return in.readAllBytes();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    static String resourceText(String name) {
        return new String(resource(name), StandardCharsets.UTF_8);
    }

    static byte[] referenceTar(String name) {
        return gunzip(read(BUNDLES.resolve(name + ".tar.gz")));
    }

    static List<String> arguments(String name) {
        try {
            return Files.readAllLines(BUNDLES.resolve(name + ".args"), StandardCharsets.UTF_8).stream()
                    .filter(line -> !line.isEmpty()).toList();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    static List<String> values(List<String> arguments, String flag) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < arguments.size() - 1; i++) {
            if (arguments.get(i).equals(flag)) {
                values.add(arguments.get(i + 1));
            }
        }
        return values;
    }

    static byte[] gunzip(byte[] archive) {
        try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(archive))) {
            return in.readAllBytes();
        } catch (IOException notGzip) {
            throw new UncheckedIOException(notGzip);
        }
    }

    static byte[] read(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
