package dev.brokoli.task;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code public static} method as a Brokoli task.
 *
 * <p>The first parameter is the task's input and the return value is its output. Each is either
 * {@link Rows}, which leaves the row schema undeclared, or a {@code List} of a Java record, whose
 * components declare the row schema. Every other parameter is a task parameter, required unless it
 * carries {@link Default}. Run settings such as retries and timeouts belong to the task's node in
 * the pipeline, not to the task.
 *
 * <pre>{@code
 * public final class Scores {
 *
 *     @Task(name = "Daily Rollup")
 *     public static Rows dailyRollup(Rows input, @Default("0.5") double threshold) {
 *         return input.filter(row -> row.getDouble("score") >= threshold);
 *     }
 * }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Task {

    /**
     * The task's display name. When empty, the name is the method name split into words and
     * title-cased, so {@code dailyRollup} becomes {@code Daily Rollup}.
     *
     * @return the display name, or an empty string to take it from the method name
     */
    String name() default "";
}
