package dev.brokoli.pipeline;

/**
 * A node of a pipeline, returned by the method of {@link Pipeline} that added it. {@link #then(NodeRef)} draws an
 * edge from it to another node of the same pipeline.
 */
public class NodeRef {

    private final String nodeId;
    private final Pipeline pipeline;

    NodeRef(String nodeId, Pipeline pipeline) {
        this.nodeId = nodeId;
        this.pipeline = pipeline;
    }

    /**
     * The node's id in the pipeline IR, made from its name as the Python SDK makes it.
     *
     * @return the id, such as {@code "read_1"}
     */
    public String nodeId() {
        return nodeId;
    }

    /**
     * Draws an edge from this node to another, so the other node reads this node's output. Drawing the same edge
     * twice draws it once.
     *
     * @param target the node to draw the edge to
     * @param <T> the kind of node
     * @return the target, so edges can be drawn in a chain: {@code read.then(clean).then(write)}
     * @throws PipelineException if the target belongs to another pipeline
     */
    public <T extends NodeRef> T then(T target) {
        pipeline.edge(this, target);
        return target;
    }

    Pipeline pipeline() {
        return pipeline;
    }
}
