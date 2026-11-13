package dev.brokoli.benchmarks;

import dev.brokoli.task.Rows;
import dev.brokoli.task.Task;

public final class Noop {

    private Noop() {
    }

    @Task
    public static Rows noop(Rows input) {
        return input;
    }
}
