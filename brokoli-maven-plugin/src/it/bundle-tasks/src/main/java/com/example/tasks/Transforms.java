package com.example.tasks;

import dev.brokoli.task.Default;
import dev.brokoli.task.Rows;
import dev.brokoli.task.Task;
import java.util.List;

public final class Transforms {

    public record Reading(String sensor, long count) {
    }

    private Transforms() {
    }

    @Task
    public static List<Reading> dailyRollup(List<Reading> input, @Default("0.5") double threshold) {
        return input;
    }

    @Task(name = "Pass Through")
    public static Rows passThrough(Rows input) {
        return input;
    }
}
