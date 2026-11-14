package dev.brokoli.pipeline;

/**
 * A node whose output is a dataset, such as a {@code source_file} or a {@code transform}.
 */
public final class DatasetRef extends NodeRef {

    DatasetRef(String nodeId, Pipeline pipeline) {
        super(nodeId, pipeline);
    }
}
