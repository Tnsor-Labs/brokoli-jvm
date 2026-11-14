package dev.brokoli.pipeline;

import dev.brokoli.bundle.TaskBundle;
import dev.brokoli.bundle.TaskBundles;
import dev.brokoli.bundle.internal.CanonicalJson;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A pipeline being built, which compiles to Brokoli's pipeline IR. Each node is added by the method named after
 * its type, which returns the node; {@link NodeRef#then(NodeRef)} or a node's {@code input} draws the edges:
 *
 * <pre>{@code
 * Pipeline p = Pipeline.builder("Sales ETL").pipelineId("sales-etl").build();
 * DatasetRef rows = p.sourceFile("Read", SourceFile.options().path("/data/sales.csv"));
 * DatasetRef clean = rows.then(p.transform("Clean", Transform.options().rules(List.of(
 *         Map.of("type", "filter", "condition", "amount > 0")))));
 * clean.then(p.sinkFile("Write", SinkFile.options().path("/data/out.csv")));
 * String digest = p.digest();
 * }</pre>
 *
 * <p>The same pipeline gives the same IR, rendering and digest as the Python SDK does: a node's id is made from
 * its name, {@link #render()} is the canonical rendering of core's {@code ir-canonicalization.md}, and
 * {@link #digest()} its {@code sha256:} digest. A pipeline is not safe to build from several threads at once.
 */
public final class Pipeline {

    private static final String IR_VERSION = "2.0";
    private static final String TASK_INTERFACE_IR_VERSION = "2.2";
    private static final String SEPARATOR = "_";

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final JsonMapper WRITER = JsonMapper.builder().build();

    private final String name;
    private final String pipelineId;
    private final String description;
    private final List<String> tags;
    private final TaskProject project;
    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final List<Edge> edges = new ArrayList<>();
    private final Map<String, Integer> counters = new HashMap<>();
    private final Map<String, JsonNode> parameters = new LinkedHashMap<>();
    private final Map<String, TaskBundle> bundles = new LinkedHashMap<>();
    private List<TaskBundle> packaged;

    private Pipeline(Builder builder) {
        this.name = builder.name;
        this.pipelineId = builder.pipelineId.isEmpty() ? NodeIds.pipelineId(builder.name) : builder.pipelineId;
        this.description = builder.description;
        this.tags = builder.tags;
        this.project = builder.project;
    }

    /**
     * Starts a pipeline.
     *
     * @param name the pipeline's name, such as {@code "Sales ETL"}
     * @return a builder for the pipeline's settings
     * @throws NullPointerException if the name is {@code null}
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * Adds a {@code source_file} node, which reads a file.
     *
     * @param name the node's name, such as {@code "Read"}
     * @param options the file to read and its format
     * @return the node
     * @throws NullPointerException if a value is {@code null}
     */
    public DatasetRef sourceFile(String name, SourceFile options) {
        ObjectNode config = JSON.objectNode();
        config.put(IrFields.PATH, options.path());
        config.put(IrFields.FORMAT, options.format());
        return new DatasetRef(add(IrFields.SOURCE_FILE, name, config, null, List.of()), this);
    }

    /**
     * Adds a {@code transform} node with no input yet; {@link NodeRef#then(NodeRef)} gives it one.
     *
     * @param name the node's name, such as {@code "Clean"}
     * @param options the node's rules
     * @return the node
     * @throws PipelineException if a rule has a value with no JSON form, or a {@code rename} rule has no mapping
     * @throws NullPointerException if a value is {@code null}
     */
    public DatasetRef transform(String name, Transform options) {
        return transform(name, List.of(), options);
    }

    /**
     * Adds a {@code transform} node that reads another node's output.
     *
     * @param name the node's name, such as {@code "Clean"}
     * @param input the node whose output it reads
     * @param options the node's rules
     * @return the node
     * @throws PipelineException if the input belongs to another pipeline, a rule has a value with no JSON form, or
     *         a {@code rename} rule has no mapping
     * @throws NullPointerException if a value is {@code null}
     */
    public DatasetRef transform(String name, NodeRef input, Transform options) {
        return transform(name, List.of(input), options);
    }

    /**
     * Adds a {@code sink_file} node with no input yet; {@link NodeRef#then(NodeRef)} gives it one.
     *
     * @param name the node's name, such as {@code "Write"}
     * @param options the file to write and its format
     * @return the node
     * @throws NullPointerException if a value is {@code null}
     */
    public NodeRef sinkFile(String name, SinkFile options) {
        return sinkFile(name, List.of(), options);
    }

    /**
     * Adds a {@code sink_file} node that writes another node's output.
     *
     * @param name the node's name, such as {@code "Write"}
     * @param input the node whose output it writes
     * @param options the file to write and its format
     * @return the node
     * @throws PipelineException if the input belongs to another pipeline
     * @throws NullPointerException if a value is {@code null}
     */
    public NodeRef sinkFile(String name, NodeRef input, SinkFile options) {
        return sinkFile(name, List.of(input), options);
    }

    /**
     * Adds a {@code task} node that runs a {@code @Task} method, named by the task's display name. The first call
     * packages the pipeline's task project into one task-bundle/v2 archive per task, as
     * {@code brokoli-maven-plugin} does; the node references its task's archive by digest, carries the task's
     * interface, and adds the task's parameters to the pipeline's. {@link #taskBundles()} returns the archives the
     * pipeline's task nodes reference.
     *
     * @param taskClass the class that declares the method
     * @param method the method's name, such as {@code "dailyRollup"}
     * @param inputs the nodes whose output the task reads, if any
     * @return the node
     * @throws PipelineException if the pipeline has no task project, the project has no such task, an input belongs
     *         to another pipeline, or the task declares a parameter that another task of the pipeline declares
     *         differently
     * @throws dev.brokoli.bundle.BundleException if the task project cannot be packaged
     * @throws NullPointerException if a value is {@code null}
     */
    public NodeRef task(Class<?> taskClass, String method, NodeRef... inputs) {
        Objects.requireNonNull(taskClass, "taskClass");
        Objects.requireNonNull(method, "method");
        List<NodeRef> from = List.of(inputs);
        from.forEach(this::requireOwn);
        TaskBundle bundle = packaged(taskClass, method).stream()
                .filter(task -> task.className().equals(taskClass.getName()) && task.method().equals(method))
                .findFirst()
                .orElseThrow(() -> new PipelineException(taskClass.getName() + " has no @Task method " + method));
        Map<String, JsonNode> declared = new LinkedHashMap<>();
        bundle.parameters().ifPresent(found -> found.properties().forEach(p -> declared.put(p.getKey(), p.getValue())));
        declared.forEach((parameter, declaration) -> requireSameDeclaration(bundle, parameter, declaration));
        String digest = bundle.digest();
        ObjectNode reference = JSON.objectNode();
        reference.put(IrFields.DIGEST, digest);
        reference.put(IrFields.FORMAT, TaskBundle.FORMAT);
        ObjectNode config = JSON.objectNode();
        config.set(IrFields.TASK_BUNDLE, reference);
        String id = add(IrFields.TASK, bundle.name(), config, bundle.nodeInterface().orElse(null), from);
        parameters.putAll(declared);
        bundles.putIfAbsent(digest, bundle);
        return new NodeRef(id, this);
    }

    /**
     * The task bundles the pipeline's {@code task} nodes reference, one per digest, in the order the nodes were
     * added. A server runs the pipeline only once it has them.
     *
     * @return the bundles
     */
    public List<TaskBundle> taskBundles() {
        return List.copyOf(bundles.values());
    }

    /**
     * The pipeline's IR as compact JSON, with keys in the order the Python SDK writes them: what a server takes to
     * create the pipeline.
     *
     * @return the IR
     */
    public String toJson() {
        return WRITER.writeValueAsString(compile());
    }

    /**
     * The pipeline's IR normalized and rendered canonically, as core's {@code ir-canonicalization.md} sections 1
     * and 2 say: the text whose digest {@link #digest()} is, and that two versions of a pipeline are compared by.
     *
     * @return the canonical rendering, ending in one newline
     * @throws dev.brokoli.bundle.BundleException if a name or value has text that is not valid Unicode
     */
    public String render() {
        return CanonicalJson.render(Normalization.normalize(compile()));
    }

    /**
     * The pipeline's digest, as core's {@code ir-canonicalization.md} section 3 says: {@code sha256:} and the
     * SHA-256 of {@link #render()}. Two pipelines with the same digest deploy the same thing.
     *
     * @return the digest
     * @throws dev.brokoli.bundle.BundleException if a name or value has text that is not valid Unicode
     */
    public String digest() {
        return CanonicalJson.digest(Normalization.normalize(compile()));
    }

    void edge(NodeRef from, NodeRef to) {
        Objects.requireNonNull(to, "target");
        requireOwn(from);
        requireOwn(to);
        Edge edge = new Edge(from.nodeId(), to.nodeId());
        if (!edges.contains(edge)) {
            edges.add(edge);
        }
    }

    private DatasetRef transform(String name, List<NodeRef> inputs, Transform options) {
        Objects.requireNonNull(name, "name");
        ObjectNode config = JSON.objectNode();
        if (!options.rules().isEmpty()) {
            config.set(IrFields.RULES, TransformRules.parse(name, options.rules()));
        }
        return new DatasetRef(add(IrFields.TRANSFORM, name, config, null, inputs), this);
    }

    private NodeRef sinkFile(String name, List<NodeRef> inputs, SinkFile options) {
        ObjectNode config = JSON.objectNode();
        config.put(IrFields.PATH, options.path());
        config.put(IrFields.FORMAT, options.format());
        return new NodeRef(add(IrFields.SINK_FILE, name, config, null, inputs), this);
    }

    private String add(String type, String nodeName, ObjectNode config, JsonNode nodeInterface,
            List<NodeRef> inputs) {
        Objects.requireNonNull(nodeName, "name");
        inputs.forEach(this::requireOwn);
        String id = allocateId(nodeName);
        nodes.put(id, new Node(id, type, nodeName, config, Capabilities.of(type), nodeInterface));
        inputs.forEach(input -> edges.add(new Edge(input.nodeId(), id)));
        return id;
    }

    private String allocateId(String nodeName) {
        String base = NodeIds.base(nodeName);
        int counter = counters.getOrDefault(base, 0);
        String candidate;
        do {
            counter++;
            candidate = base + SEPARATOR + counter;
        } while (nodes.containsKey(candidate));
        counters.put(base, counter);
        return candidate;
    }

    private void requireOwn(NodeRef node) {
        if (node.pipeline() != this || !nodes.containsKey(node.nodeId())) {
            throw new PipelineException("nodes from different pipelines cannot be connected: " + node.nodeId()
                    + " is not a node of pipeline " + name);
        }
    }

    private List<TaskBundle> packaged(Class<?> taskClass, String method) {
        if (packaged == null) {
            if (project == null) {
                throw new PipelineException("pipeline " + name + " has no task project to package "
                        + taskClass.getName() + "." + method + " from; give it one with Pipeline.builder(name)"
                        + ".project(TaskProject.of(classesDirectory, jars))");
            }
            packaged = TaskBundles.of(project.classesDirectory(), project.jars(), BuilderVersion.BUILDER);
        }
        return packaged;
    }

    private void requireSameDeclaration(TaskBundle bundle, String parameter, JsonNode declaration) {
        JsonNode existing = parameters.get(parameter);
        if (existing != null && !existing.equals(declaration)) {
            throw new PipelineException("parameter " + parameter + " of " + bundle.className() + "."
                    + bundle.method() + " is declared as " + declaration + ", but another task of pipeline " + name
                    + " declares it as " + existing + "; give the parameters different names");
        }
    }

    private ObjectNode compile() {
        ObjectNode ir = JSON.objectNode();
        ir.put(IrFields.PIPELINE_ID, pipelineId);
        ir.put(IrFields.NAME, name);
        ir.put(IrFields.DESCRIPTION, description);
        ir.put(IrFields.SCHEDULE, "");
        ir.put(IrFields.ENABLED, true);
        ir.put(IrFields.IR_VERSION, usesTaskInterfaces() ? TASK_INTERFACE_IR_VERSION : IR_VERSION);
        ArrayNode nodeList = ir.putArray(IrFields.NODES);
        nodes.values().forEach(node -> nodeList.add(node.toJson()));
        ArrayNode edgeList = ir.putArray(IrFields.EDGES);
        edges.forEach(edge -> edgeList.add(edge.toJson()));
        ArrayNode tagList = ir.putArray(IrFields.TAGS);
        tags.forEach(tagList::add);
        ir.putArray(IrFields.DEPENDS_ON);
        if (!parameters.isEmpty()) {
            ObjectNode parameterMap = ir.putObject(IrFields.PARAMETERS);
            parameters.forEach((parameter, declaration) -> parameterMap.set(parameter, declaration.deepCopy()));
        }
        return ir;
    }

    private boolean usesTaskInterfaces() {
        return !parameters.isEmpty() || nodes.values().stream().anyMatch(node -> node.nodeInterface() != null);
    }

    /**
     * The settings of a pipeline before its first node is added.
     */
    public static final class Builder {

        private final String name;
        private String pipelineId = "";
        private String description = "";
        private List<String> tags = List.of();
        private TaskProject project;

        private Builder(String name) {
            this.name = Objects.requireNonNull(name, "name");
        }

        /**
         * Sets the pipeline's id. Without one, or with an empty one, the id is made from the name as the Python
         * SDK makes it: lowercase, spaces as hyphens, and only {@code a-z}, {@code 0-9} and single hyphens kept,
         * so {@code "Sales ETL"} is {@code "sales-etl"}.
         *
         * @param pipelineId the id, such as {@code "sales-etl"}
         * @return this builder
         * @throws NullPointerException if the id is {@code null}
         */
        public Builder pipelineId(String pipelineId) {
            this.pipelineId = Objects.requireNonNull(pipelineId, "pipelineId");
            return this;
        }

        /**
         * Sets the pipeline's description, empty unless set.
         *
         * @param description the description
         * @return this builder
         * @throws NullPointerException if the description is {@code null}
         */
        public Builder description(String description) {
            this.description = Objects.requireNonNull(description, "description");
            return this;
        }

        /**
         * Sets the pipeline's tags, none unless set.
         *
         * @param tags the tags, such as {@code "finance"}
         * @return this builder
         * @throws NullPointerException if a tag is {@code null}
         */
        public Builder tags(String... tags) {
            this.tags = List.of(tags);
            return this;
        }

        /**
         * Sets the compiled project whose {@code @Task} methods the pipeline's {@code task} nodes run.
         *
         * @param project the project
         * @return this builder
         * @throws NullPointerException if the project is {@code null}
         */
        public Builder project(TaskProject project) {
            this.project = Objects.requireNonNull(project, "project");
            return this;
        }

        /**
         * Creates the pipeline, with no nodes yet.
         *
         * @return the pipeline
         */
        public Pipeline build() {
            return new Pipeline(this);
        }
    }

    private record Node(String id, String type, String name, ObjectNode config, List<String> capabilities,
            JsonNode nodeInterface) {

        ObjectNode toJson() {
            ObjectNode node = JSON.objectNode();
            node.put(IrFields.ID, id);
            node.put(IrFields.TYPE, type);
            node.put(IrFields.NAME, name);
            node.set(IrFields.CONFIG, config.deepCopy());
            ArrayNode capabilityList = node.putArray(IrFields.CAPABILITIES);
            capabilities.forEach(capabilityList::add);
            if (nodeInterface != null) {
                node.set(IrFields.INTERFACE, nodeInterface.deepCopy());
            }
            return node;
        }
    }

    private record Edge(String from, String to) {

        ObjectNode toJson() {
            ObjectNode edge = JSON.objectNode();
            edge.put(IrFields.FROM, from);
            edge.put(IrFields.TO, to);
            return edge;
        }
    }
}
