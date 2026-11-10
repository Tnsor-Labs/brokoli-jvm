package dev.brokoli.maven;

import dev.brokoli.bundle.BundleException;
import dev.brokoli.bundle.TaskBundle;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class AttachedBundles {

    static final String TYPE = "tar.gz";

    private AttachedBundles() {
    }

    record Written(TaskBundle bundle, Path file, String digest) {

        Written {
            Objects.requireNonNull(bundle, "bundle");
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(digest, "digest");
        }

        String classifier() {
            return bundle.method();
        }
    }

    static List<Written> write(List<TaskBundle> bundles, Path outputDirectory, String finalName) throws IOException {
        Map<String, TaskBundle> byClassifier = new LinkedHashMap<>();
        for (TaskBundle bundle : bundles) {
            TaskBundle earlier = byClassifier.putIfAbsent(bundle.method(), bundle);
            if (earlier != null) {
                throw new BundleException("tasks " + earlier.className() + "." + earlier.method() + " and "
                        + bundle.className() + "." + bundle.method() + " would both be attached with classifier "
                        + bundle.method() + "; rename one of the methods");
            }
        }
        Files.createDirectories(outputDirectory);
        List<Written> written = new ArrayList<>();
        for (TaskBundle bundle : byClassifier.values()) {
            Path file = outputDirectory.resolve(finalName + "-" + bundle.method() + "." + TYPE);
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(file))) {
                written.add(new Written(bundle, file, bundle.writeTo(out)));
            }
        }
        return List.copyOf(written);
    }
}
