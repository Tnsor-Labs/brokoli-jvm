package dev.brokoli.benchmarks;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttemptsTest {

    @TempDir
    Path records;

    @Test
    void countsNoAttemptsBeforeTheHarnessFirstRan() throws IOException {
        assertThat(new Attempts(records).count(Attempts.PYTHON)).isZero();
    }

    @Test
    void readsTheNewAttemptAsMillisecondsAndMebibytes() throws IOException {
        Files.writeString(records.resolve("jvm.txt"), "71286 39384\n51712 40960\n");

        Attempts.Sample sample = new Attempts(records).since(Attempts.JVM, 1);

        assertThat(sample.wallMillis()).isEqualTo(51.712);
        assertThat(sample.rssMebibytes()).isEqualTo(40.0);
    }

    @Test
    void keepsEachRuntimeInItsOwnRecords() throws IOException {
        Files.writeString(records.resolve("jvm.txt"), "1000 1024\n");
        Files.writeString(records.resolve("python.txt"), "2000 2048\n3000 3072\n");

        Attempts attempts = new Attempts(records);

        assertThat(attempts.count(Attempts.JVM)).isEqualTo(1);
        assertThat(attempts.count(Attempts.PYTHON)).isEqualTo(2);
    }

    @Test
    void refusesARunThatLeftNoRecord() throws IOException {
        Files.writeString(records.resolve("jvm.txt"), "1000 1024\n");

        assertThatThrownBy(() -> new Attempts(records).since(Attempts.JVM, 1))
                .isInstanceOf(BenchmarkException.class)
                .hasMessageEndingWith("has 1 records, expected one more than 1: one per attempt of the jvm harness");
    }

    @Test
    void refusesARunThatLeftMoreThanOneRecord() throws IOException {
        Files.writeString(records.resolve("jvm.txt"), "1000 1024\n2000 2048\n3000 3072\n");

        assertThatThrownBy(() -> new Attempts(records).since(Attempts.JVM, 1))
                .isInstanceOf(BenchmarkException.class)
                .hasMessageEndingWith("has 3 records, expected one more than 1: one per attempt of the jvm harness");
    }
}
