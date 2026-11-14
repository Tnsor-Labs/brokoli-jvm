package dev.brokoli.pipeline;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * A compiled JVM project whose {@code @Task} methods a pipeline packages into task bundles, as
 * {@code brokoli-maven-plugin}'s {@code bundle} goal does for the same project.
 *
 * @param classesDirectory the compiler's output directory, such as {@code target/classes}, holding the task files
 *        the task processor wrote
 * @param jars the jars the tasks need at run time, which each bundle carries under {@code lib/}
 */
public record TaskProject(Path classesDirectory, List<Path> jars) {

    /**
     * Creates the project, with a copy of the list of jars.
     *
     * @param classesDirectory the compiler's output directory
     * @param jars the jars the tasks need at run time
     * @throws NullPointerException if a value, or a jar in the list, is {@code null}
     */
    public TaskProject {
        Objects.requireNonNull(classesDirectory, "classesDirectory");
        jars = List.copyOf(jars);
    }

    /**
     * Creates the project.
     *
     * @param classesDirectory the compiler's output directory, such as {@code target/classes}
     * @param jars the jars the tasks need at run time
     * @return the project
     * @throws NullPointerException if a value, or a jar in the list, is {@code null}
     */
    public static TaskProject of(Path classesDirectory, List<Path> jars) {
        return new TaskProject(classesDirectory, jars);
    }
}
