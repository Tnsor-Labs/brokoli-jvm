package dev.brokoli.benchmarks;

final class BenchmarkException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    BenchmarkException(String message) {
        super(message);
    }
}
