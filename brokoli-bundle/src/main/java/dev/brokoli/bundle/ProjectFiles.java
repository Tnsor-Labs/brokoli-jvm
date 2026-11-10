package dev.brokoli.bundle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;

final class ProjectFiles {

    static final String LIBRARIES = "lib/";
    static final String PLUGIN_METADATA = "META-INF/brokoli/";

    private final SortedMap<String, BundleFile> files = new TreeMap<>(Utf8.ORDER);
    private final Map<String, Path> sources = new TreeMap<>();

    private ProjectFiles() {
    }

    static List<BundleFile> collect(Path classesDirectory, List<Path> jars) {
        ProjectFiles project = new ProjectFiles();
        if (Files.isDirectory(classesDirectory)) {
            project.addTree(classesDirectory);
        }
        for (Path jar : jars) {
            project.addJar(jar);
        }
        return List.copyOf(project.files.values());
    }

    static List<Path> taskFiles(Path classesDirectory) {
        Path directory = classesDirectory.resolve(TaskFile.DIRECTORY);
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> listed = Files.list(directory)) {
            return listed.filter(file -> file.getFileName().toString().endsWith(".json")).sorted().toList();
        } catch (IOException unreadable) {
            throw new BundleException("could not list " + directory + ": " + unreadable.getMessage(), unreadable);
        }
    }

    static String slashPath(Path root, Path file) {
        List<String> parts = new ArrayList<>();
        for (Path part : root.relativize(file)) {
            parts.add(part.toString());
        }
        return String.join("/", parts);
    }

    static byte[] read(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException unreadable) {
            throw new BundleException("could not read " + file + ": " + unreadable.getMessage(), unreadable);
        }
    }

    private void addTree(Path root) {
        List<Path> walked;
        try (Stream<Path> tree = Files.walk(root)) {
            walked = tree.filter(path -> !path.equals(root)).toList();
        } catch (IOException unreadable) {
            throw new BundleException("could not read " + root + ": " + unreadable.getMessage(), unreadable);
        }
        for (Path path : walked) {
            String bundlePath = slashPath(root, path);
            if (Files.isSymbolicLink(path)) {
                throw new BundleException(path + " is a symbolic link; a task bundle holds only regular files");
            }
            if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || bundlePath.startsWith(PLUGIN_METADATA)) {
                continue;
            }
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new BundleException(path + " is not a regular file; a task bundle holds only regular files");
            }
            add(bundlePath, path);
        }
    }

    private void addJar(Path jar) {
        if (!Files.isRegularFile(jar)) {
            throw new BundleException("dependency " + jar + " is not a file; package it before bundling");
        }
        add(LIBRARIES + jar.getFileName(), jar);
    }

    private void add(String bundlePath, Path source) {
        Utf8.requireValid(bundlePath);
        Path earlier = sources.putIfAbsent(bundlePath, source);
        if (earlier != null) {
            throw new BundleException(earlier + " and " + source + " would both be " + bundlePath
                    + " in the task bundle");
        }
        files.put(bundlePath, new BundleFile(bundlePath, read(source)));
    }
}
