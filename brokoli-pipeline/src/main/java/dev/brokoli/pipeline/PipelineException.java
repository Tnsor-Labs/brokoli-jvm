package dev.brokoli.pipeline;

/**
 * Thrown when a pipeline cannot be built as written, such as an edge to a node of another pipeline, a transform
 * rule with no JSON form, or a task the task project does not have.
 */
public class PipelineException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message what could not be built, and why
     */
    public PipelineException(String message) {
        super(message);
    }
}
