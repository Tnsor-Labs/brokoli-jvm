package dev.brokoli.task;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class TaskTest {

    static final class Tasks {

        @Task
        public static Rows unnamed(Rows input) {
            return input;
        }

        @Task(name = "Daily Rollup")
        public static Rows named(Rows input, @Default("0.5") double threshold) {
            return input;
        }
    }

    @Test
    void taskAndDefaultAreReadableFromTheCompiledClass() throws NoSuchMethodException {
        Method named = Tasks.class.getMethod("named", Rows.class, double.class);

        assertThat(named.getAnnotation(Task.class).name()).isEqualTo("Daily Rollup");
        assertThat(named.getParameters()[1].getAnnotation(Default.class).value()).isEqualTo("0.5");
    }

    @Test
    void taskNameIsEmptyWhenNotGivenSoTheMethodNameIsUsed() throws NoSuchMethodException {
        Method unnamed = Tasks.class.getMethod("unnamed", Rows.class);

        assertThat(unnamed.getAnnotation(Task.class).name()).isEmpty();
    }
}
