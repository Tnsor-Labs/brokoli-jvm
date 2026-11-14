package dev.brokoli.bundle;

import dev.brokoli.bundle.TaskFile.TaskEntry;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Packages the tasks of a compiled JVM project into task-bundle/v2 archives, one per {@code @Task} method, in the
 * form core's {@code brokoli bundle jvm} writes.
 *
 * <p>Every bundle holds the same files: everything under the classes directory except
 * {@code META-INF/brokoli/}, at its path relative to that directory, and each jar under {@code lib/} by its file
 * name. Only the manifest differs: its {@code name} is the task's display name, its entrypoint is the task's
 * generated entry method, and its {@code interface_digest} is the digest of the task's interface.
 */
public final class TaskBundles {

    private TaskBundles() {
    }

    /**
     * Reads the task files the task processor wrote under {@code META-INF/brokoli/tasks/} and prepares one bundle
     * for each task they list, in the order of the task files' names and then of the tasks in each file.
     *
     * @param classesDirectory the compiler's output directory, such as {@code target/classes}
     * @param jars the jars the tasks need at run time, placed on their classpath
     * @param builder what to record as the manifest's {@code build.builder}, such as
     *        {@code "brokoli-jvm/0.1.0"}, or {@code null} to leave {@code build} out
     * @return the bundles, one per task
     * @throws BundleException if there is no task file, a task file is not valid, two files would have the same
     *         path in the bundle, a file would be the bundle's {@code manifest.json} or inside a directory of that
     *         name, a file's path would also be the directory of another file, a file is a symbolic link or cannot
     *         be read, or a path or name is not valid Unicode
     */
    public static List<TaskBundle> of(Path classesDirectory, List<Path> jars, String builder) {
        Objects.requireNonNull(classesDirectory, "classesDirectory");
        Objects.requireNonNull(jars, "jars");
        List<Path> taskFiles = ProjectFiles.taskFiles(classesDirectory);
        if (taskFiles.isEmpty()) {
            throw new BundleException("no task file under " + classesDirectory.resolve(TaskFile.DIRECTORY)
                    + "; the project has no @Task method, or brokoli-task-processor is not in the compiler's"
                    + " annotationProcessorPaths");
        }
        List<BundleFile> files = ProjectFiles.collect(classesDirectory, jars);
        String sourceDigest = BundleFile.sourceDigest(files);
        List<TaskBundle> bundles = new ArrayList<>();
        for (Path taskFile : taskFiles) {
            String path = ProjectFiles.slashPath(classesDirectory, taskFile);
            for (TaskEntry task : TaskFile.read(path, ProjectFiles.read(taskFile))) {
                bundles.add(new TaskBundle(task, files, sourceDigest, builder));
            }
        }
        return List.copyOf(bundles);
    }
}
