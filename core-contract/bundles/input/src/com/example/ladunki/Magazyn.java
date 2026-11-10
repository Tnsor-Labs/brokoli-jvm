package com.example.ladunki;

import dev.brokoli.task.Task;
import java.util.List;

public final class Magazyn {

    public record Ładunek(String id, long waga) {
    }

    @Task
    public static List<Ładunek> zważ(List<Ładunek> input) {
        return input;
    }
}
