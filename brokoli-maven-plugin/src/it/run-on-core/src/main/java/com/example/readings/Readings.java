package com.example.readings;

import dev.brokoli.task.Default;
import dev.brokoli.task.Task;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class Readings {

    public record Reading(String sensor, long count) {
    }

    public record Scaled(String sensor, long count, String unit) {
    }

    private Readings() {
    }

    @Task
    public static List<Scaled> scale(List<Reading> input, @Default("2") long factor) {
        String unit = unit();
        return input.stream()
                .map(reading -> new Scaled(reading.sensor(), reading.count() * factor, unit))
                .toList();
    }

    private static String unit() {
        try (InputStream resource = Readings.class.getResourceAsStream("/unit.txt")) {
            return new String(resource.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
