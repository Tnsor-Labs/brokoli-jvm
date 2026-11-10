package dev.brokoli.task;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Makes a task parameter optional, with the value it takes when a run does not supply one.
 *
 * <p>The value is written as text and read as the parameter's type: {@code "0.5"} for a
 * {@code double}, {@code "true"} for a {@code boolean}, {@code "eu-west"} for a {@code String}.
 * A value that cannot be read as the parameter's type is refused when the task is compiled. A
 * task parameter without this annotation is required.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Default {

    /**
     * The parameter's value when a run does not supply one, written as text.
     *
     * @return the default value as text
     */
    String value();
}
