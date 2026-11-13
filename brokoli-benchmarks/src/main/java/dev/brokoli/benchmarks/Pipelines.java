package dev.brokoli.benchmarks;

import tools.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class Pipelines {

    static final String TASK = "task";
    static final String SOURCE = "read";

    private static final String BUNDLE_FORMAT = "brokoli.task-bundle/v2";

    private Pipelines() {
    }

    static Map<String, Object> taskOnly(String name, String digest, JsonNode taskInterface) {
        return pipeline(name, List.of(taskNode(name, digest, taskInterface, null)), List.of());
    }

    static Map<String, Object> fromFile(String name, String path, String digest, JsonNode taskInterface,
            int timeoutSeconds) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("id", SOURCE);
        source.put("type", "source_file");
        source.put("name", "Read");
        source.put("config", Map.of("path", path, "format", "json"));
        return pipeline(name, List.of(source, taskNode(name, digest, taskInterface, timeoutSeconds)),
                List.of(Map.of("from", SOURCE, "to", TASK)));
    }

    static double nodeDuration(JsonNode run) {
        for (JsonNode nodeRun : run.path("node_runs").values()) {
            if (TASK.equals(nodeRun.path("node_id").asString())) {
                return nodeRun.path("duration_ms").asDouble();
            }
        }
        throw new BenchmarkException("run " + run.path("id").asString() + " has no node run for " + TASK);
    }

    private static Map<String, Object> taskNode(String name, String digest, JsonNode taskInterface,
            Integer timeoutSeconds) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("task_bundle", Map.of("digest", digest, "format", BUNDLE_FORMAT));
        if (timeoutSeconds != null) {
            config.put("timeout", timeoutSeconds);
        }
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", TASK);
        node.put("type", TASK);
        node.put("name", name);
        node.put("config", config);
        if (taskInterface != null && !taskInterface.isNull()) {
            node.put("interface", taskInterface);
        }
        return node;
    }

    private static Map<String, Object> pipeline(String name, List<Map<String, Object>> nodes,
            List<Map<String, Object>> edges) {
        Map<String, Object> pipeline = new LinkedHashMap<>();
        pipeline.put("name", name + " " + UUID.randomUUID());
        pipeline.put("nodes", nodes);
        pipeline.put("edges", edges);
        return pipeline;
    }
}
