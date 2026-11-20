package dev.brokoli.pipeline;

import java.time.Duration;

final class ResourceLimits {

    private ResourceLimits() {
    }

    static void requireMemory(int maxMemoryMb) {
        if (maxMemoryMb <= 0) {
            throw new IllegalArgumentException("memory limit " + maxMemoryMb + " MB is not positive");
        }
    }

    static void requireCpuTime(Duration maxCpuTime) {
        NodeOptions.requireNotNegative("CPU time limit", maxCpuTime);
        if (maxCpuTime.getNano() != 0) {
            throw new IllegalArgumentException("CPU time limit " + maxCpuTime
                    + " is not a whole number of seconds, which the server takes");
        }
    }
}
