package com.example.tasks;

import dev.brokoli.task.Default;
import dev.brokoli.task.Task;
import java.math.BigDecimal;
import java.util.List;

public final class Readings {

    public record Reading(String sensor, BigDecimal amount) {
    }

    @Task
    public static List<Reading> rollup(List<Reading> input, @Default("0.5") double threshold) {
        return input;
    }
}
