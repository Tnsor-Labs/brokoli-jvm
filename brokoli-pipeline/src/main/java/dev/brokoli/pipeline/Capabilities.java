package dev.brokoli.pipeline;

import java.util.List;
import java.util.Map;

import static java.util.Map.entry;

final class Capabilities {

    private static final String SOURCE = "source";
    private static final String SINK = "sink";
    private static final String COMPUTE = "compute";
    private static final String DATASET_OUTPUT = "dataset-output";

    private static final List<String> DATASET_SOURCE = List.of(SOURCE, DATASET_OUTPUT);
    private static final List<String> DATASET_COMPUTE = List.of(COMPUTE, DATASET_OUTPUT);
    private static final List<String> SINK_ONLY = List.of(SINK);
    private static final List<String> COMPUTE_ONLY = List.of(COMPUTE);

    private static final Map<String, List<String>> BY_NODE_TYPE = Map.ofEntries(
            entry(IrFields.SOURCE_FILE, DATASET_SOURCE),
            entry("source_api", DATASET_SOURCE),
            entry("source_db", DATASET_SOURCE),
            entry("dbt", DATASET_SOURCE),
            entry("migrate", DATASET_SOURCE),
            entry("sink_db", SINK_ONLY),
            entry(IrFields.SINK_FILE, SINK_ONLY),
            entry("sink_api", SINK_ONLY),
            entry("union", DATASET_COMPUTE),
            entry("dataset_map", DATASET_COMPUTE),
            entry("dataset_filter", DATASET_COMPUTE),
            entry("project", DATASET_COMPUTE),
            entry("aggregate", DATASET_COMPUTE),
            entry("filter", DATASET_COMPUTE),
            entry("bash", COMPUTE_ONLY),
            entry("contract_gate", COMPUTE_ONLY),
            entry("wait", DATASET_COMPUTE));

    private Capabilities() {
    }

    static List<String> of(String nodeType) {
        return BY_NODE_TYPE.getOrDefault(nodeType, COMPUTE_ONLY);
    }
}
