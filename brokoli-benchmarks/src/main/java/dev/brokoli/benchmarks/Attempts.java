package dev.brokoli.benchmarks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class Attempts {

    static final String JVM = "jvm";
    static final String PYTHON = "python";

    private static final String SUFFIX = ".txt";
    private static final String SEPARATOR = " ";
    private static final double MICROS_PER_MILLI = 1000.0;
    private static final double KIB_PER_MIB = 1024.0;

    record Sample(double wallMillis, double rssMebibytes) {
    }

    private final Path directory;

    Attempts(Path directory) {
        this.directory = directory;
    }

    int count(String runtime) throws IOException {
        Path records = records(runtime);
        return Files.exists(records) ? Files.readAllLines(records).size() : 0;
    }

    Sample since(String runtime, int before) throws IOException {
        Path records = records(runtime);
        List<String> lines = Files.exists(records) ? Files.readAllLines(records) : List.of();
        if (lines.size() != before + 1) {
            throw new BenchmarkException(records + " has " + lines.size() + " records, expected one more than "
                    + before + ": one per attempt of the " + runtime + " harness");
        }
        String[] fields = lines.get(before).trim().split(SEPARATOR);
        return new Sample(Long.parseLong(fields[0]) / MICROS_PER_MILLI, Long.parseLong(fields[1]) / KIB_PER_MIB);
    }

    private Path records(String runtime) {
        return directory.resolve(runtime + SUFFIX);
    }
}
