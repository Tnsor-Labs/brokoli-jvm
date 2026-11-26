package dev.brokoli.pipeline;

import dev.brokoli.bundle.TaskBundle;
import dev.brokoli.bundle.TaskBundles;
import dev.brokoli.bundle.internal.CanonicalJson;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

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
    private static final List<String> HOOK_EVENTS = List.of(IrFields.ON_START, IrFields.ON_SUCCESS,
            IrFields.ON_FAILURE);

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final JsonMapper WRITER = JsonMapper.builder().build();

    private final String name;
    private final String pipelineId;
    private final String description;
    private final String schedule;
    private final String scheduleTimezone;
    private final boolean catchUp;
    private final String slaDeadline;
    private final String slaTimezone;
    private final List<String> tags;
    private final List<String> dependsOn;
    private final boolean webhook;
    private final Map<String, Hook> hooks;
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
        this.schedule = builder.schedule;
        this.scheduleTimezone = builder.scheduleTimezone;
        this.catchUp = builder.catchUp;
        this.slaDeadline = builder.slaDeadline;
        this.slaTimezone = builder.slaTimezone;
        this.tags = builder.tags;
        this.dependsOn = builder.dependsOn;
        this.webhook = builder.webhook;
        this.hooks = new LinkedHashMap<>();
        HOOK_EVENTS.stream()
                .filter(builder.hooks::containsKey)
                .forEach(event -> hooks.put(event, builder.hooks.get(event)));
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
     * @param options the file to read, its format and the node's options
     * @return the node
     * @throws PipelineException if the node key is the id of another node of the pipeline
     * @throws NullPointerException if a value is {@code null}
     */
    public DatasetRef sourceFile(String name, SourceFile options) {
        ObjectNode config = JSON.objectNode();
        config.put(IrFields.PATH, options.path());
        config.put(IrFields.FORMAT, options.format());
        options.connId().ifPresent(connId -> config.put(IrFields.CONN_ID, connId));
        options.node().writeRetriesAndTimeout(config);
        options.node().writePool(config);
        return new DatasetRef(add(IrFields.SOURCE_FILE, name, config, null, List.of(), options.node()), this);
    }

    /**
     * Adds a {@code transform} node with no input yet; {@link NodeRef#then(NodeRef)} gives it one.
     *
     * @param name the node's name, such as {@code "Clean"}
     * @param options the node's rules and options
     * @return the node
     * @throws PipelineException if a rule has a value with no JSON form, a {@code rename} rule has no mapping, or
     *         the node key is the id of another node of the pipeline
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
     * @param options the node's rules and options
     * @return the node
     * @throws PipelineException if the input belongs to another pipeline, a rule has a value with no JSON form, a
     *         {@code rename} rule has no mapping, or the node key is the id of another node of the pipeline
     * @throws NullPointerException if a value is {@code null}
     */
    public DatasetRef transform(String name, NodeRef input, Transform options) {
        return transform(name, List.of(input), options);
    }

    /**
     * Adds a {@code sink_file} node with no input yet; {@link NodeRef#then(NodeRef)} gives it one.
     *
     * @param name the node's name, such as {@code "Write"}
     * @param options the file to write, its format and the node's options
     * @return the node
     * @throws PipelineException if the node key is the id of another node of the pipeline
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
     * @param options the file to write, its format and the node's options
     * @return the node
     * @throws PipelineException if the input belongs to another pipeline, or the node key is the id of another
     *         node of the pipeline
     * @throws NullPointerException if a value is {@code null}
     */
    public NodeRef sinkFile(String name, NodeRef input, SinkFile options) {
        return sinkFile(name, List.of(input), options);
    }

    /**
     * Adds a {@code bash} node with no input yet; {@link NodeRef#then(NodeRef)} gives it one.
     *
     * @param name the node's name, such as {@code "Refresh"}
     * @param options the command, its environment and limits, and the node's options
     * @return the node
     * @throws PipelineException if the node key is the id of another node of the pipeline
     * @throws NullPointerException if a value is {@code null}
     */
    public NodeRef bash(String name, Bash options) {
        return bash(name, List.of(), options);
    }

    /**
     * Adds a {@code bash} node that runs after another node and passes that node's output through.
     *
     * @param name the node's name, such as {@code "Refresh"}
     * @param input the node it runs after
     * @param options the command, its environment and limits, and the node's options
     * @return the node
     * @throws PipelineException if the input belongs to another pipeline, or the node key is the id of another
     *         node of the pipeline
     * @throws NullPointerException if a value is {@code null}
     */
    public NodeRef bash(String name, NodeRef input, Bash options) {
        return bash(name, List.of(input), options);
    }

    /**
     * Adds a {@code source_db} node, which reads the rows a query returns.
     *
     * @param name the node's name, such as {@code "Orders"}
     * @param options the query, the database it runs on and the node's options
     * @return the node
     * @throws PipelineException if the node key is the id of another node of the pipeline
     * @throws NullPointerException if a value is {@code null}
     */
    public DatasetRef sourceDb(String name, SourceDb options) {
        ObjectNode config = JSON.objectNode();
        config.put(IrFields.QUERY, options.query());
        options.connId().ifPresent(connId -> config.put(IrFields.CONN_ID, connId));
        options.uri().ifPresent(uri -> config.put(IrFields.URI, uri));
        options.node().writeRetriesAndTimeout(config);
        options.node().writePool(config);
        config.put(IrFields.SCHEMA_HINT, IrFields.QUERY_RESULT);
        return new DatasetRef(add(IrFields.SOURCE_DB, name, config, null, List.of(), options.node()), this);
    }

    /**
     * Adds a {@code join} node that joins two nodes' outputs. The edge from the left input is drawn first.
     *
     * @param name the node's name, such as {@code "Enrich"}
     * @param left the node whose output is the left side
     * @param right the node whose output is the right side
     * @param options the key columns, the join type, the collision policy and the node's options
     * @return the node
     * @throws PipelineException if an input belongs to another pipeline, or the node key is the id of another node
     *         of the pipeline
     * @throws NullPointerException if a value is {@code null}
     */
    public DatasetRef join(String name, NodeRef left, NodeRef right, Join options) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        ObjectNode config = JSON.objectNode();
        config.put(IrFields.JOIN_TYPE, options.type().irValue());
        config.put(IrFields.LEFT_KEY, options.leftKey());
        config.put(IrFields.RIGHT_KEY, options.rightKey());
        config.put(IrFields.COLLISION_POLICY, options.collisionPolicy().irValue());
        options.rightAlias().ifPresent(alias -> config.put(IrFields.RIGHT_ALIAS, alias));
        options.node().writeRetriesAndTimeout(config);
        return new DatasetRef(add(IrFields.JOIN, name, config, null, List.of(left, right), options.node()), this);
    }

    /**
     * Adds a {@code quality_check} node with no input yet; {@link NodeRef#then(NodeRef)} gives it one.
     *
     * @param name the node's name, such as {@code "Gate"}
     * @param options the node's rules and options
     * @return the node
     * @throws PipelineException if a rule has a value with no JSON form, or the node key is the id of another node
     *         of the pipeline
     * @throws NullPointerException if a value is {@code null}
     */
    public NodeRef qualityCheck(String name, QualityCheck options) {
        return qualityCheck(name, List.of(), options);
    }

    /**
     * Adds a {@code quality_check} node that checks another node's output.
     *
     * @param name the node's name, such as {@code "Gate"}
     * @param input the node whose output it checks
     * @param options the node's rules and options
     * @return the node
     * @throws PipelineException if the input belongs to another pipeline, a rule has a value with no JSON form, or
     *         the node key is the id of another node of the pipeline
     * @throws NullPointerException if a value is {@code null}
     */
    public NodeRef qualityCheck(String name, NodeRef input, QualityCheck options) {
        return qualityCheck(name, List.of(input), options);
    }

    /**
     * Adds a {@code sink_db} node with no input yet; {@link NodeRef#then(NodeRef)} gives it one.
     *
     * @param name the node's name, such as {@code "Load"}
     * @param options the table, how it is written, the database and the node's options
     * @return the node
     * @throws PipelineException if the node key is the id of another node of the pipeline
     * @throws NullPointerException if a value is {@code null}
     */
    public NodeRef sinkDb(String name, SinkDb options) {
        return sinkDb(name, List.of(), options);
    }

    /**
     * Adds a {@code sink_db} node that writes another node's output.
     *
     * @param name the node's name, such as {@code "Load"}
     * @param input the node whose output it writes
     * @param options the table, how it is written, the database and the node's options
     * @return the node
     * @throws PipelineException if the input belongs to another pipeline, or the node key is the id of another
     *         node of the pipeline
     * @throws NullPointerException if a value is {@code null}
     */
    public NodeRef sinkDb(String name, NodeRef input, SinkDb options) {
        return sinkDb(name, List.of(input), options);
    }

    /**
     * Adds a {@code source_api} node, which reads the records an HTTP endpoint returns.
     *
     * @param name the node's name, such as {@code "Fetch"}
     * @param options the request, where the records are in the body and the node's options
     * @return the node
     * @throws PipelineException if the body has a value with no JSON form, or the node key is the id of another
     *         node of the pipeline
     * @throws NullPointerException if a value is {@code null}
     */
    public DatasetRef sourceApi(String name, SourceApi options) {
        Objects.requireNonNull(name, "name");
        ObjectNode config = JSON.objectNode();
        config.put(IrFields.URL, options.url());
        config.put(IrFields.METHOD, options.method());
        config.put(IrFields.RESPONSE, IrFields.DATASET);
        putStrings(config, IrFields.HEADERS, options.headers());
        options.body().ifPresent(body -> config.set(IrFields.BODY,
                JsonValues.of(IrFields.SOURCE_API + " " + name + ": " + IrFields.BODY, body)));
        options.connId().ifPresent(connId -> config.put(IrFields.CONN_ID, connId));
        putStrings(config, IrFields.PARAMS, options.params());
        options.records().ifPresent(records -> config.put(IrFields.RECORDS, records));
        options.node().writeRetriesAndTimeout(config);
        options.sourceType().ifPresent(sourceType -> config.put(IrFields.SOURCE_TYPE, sourceType));
        options.node().writePool(config);
        config.put(IrFields.SCHEMA_HINT, IrFields.API_RESPONSE);
        return new DatasetRef(add(IrFields.SOURCE_API, name, config, null, List.of(), options.node()), this);
    }

    /**
     * Adds a {@code sink_api} node with no input yet; {@link NodeRef#then(NodeRef)} gives it one.
     *
     * @param name the node's name, such as {@code "Push"}
     * @param options the endpoint, how the rows are sent and the node's options
     * @return the node
     * @throws PipelineException if the node key is the id of another node of the pipeline
     * @throws NullPointerException if a value is {@code null}
     */
    public NodeRef sinkApi(String name, SinkApi options) {
        return sinkApi(name, List.of(), options);
    }

    /**
     * Adds a {@code sink_api} node that sends another node's output.
     *
     * @param name the node's name, such as {@code "Push"}
     * @param input the node whose output it sends
     * @param options the endpoint, how the rows are sent and the node's options
     * @return the node
     * @throws PipelineException if the input belongs to another pipeline, or the node key is the id of another
     *         node of the pipeline
     * @throws NullPointerException if a value is {@code null}
     */
    public NodeRef sinkApi(String name, NodeRef input, SinkApi options) {
        return sinkApi(name, List.of(input), options);
    }

    /**
     * Adds a {@code code} node with no input yet; {@link NodeRef#then(NodeRef)} gives it one.
     *
     * @param name the node's name, such as {@code "Enrich"}
     * @param options the script, its runtime and limits, and the node's options
     * @return the node
     * @throws PipelineException if the node key is the id of another node of the pipeline
     * @throws NullPointerException if a value is {@code null}
     */
    public NodeRef code(String name, Code options) {
        return code(name, List.of(), options);
    }

    /**
     * Adds a {@code code} node that runs its script on another node's output.
     *
     * @param name the node's name, such as {@code "Enrich"}
     * @param input the node whose output the script reads
     * @param options the script, its runtime and limits, and the node's options
     * @return the node
     * @throws PipelineException if the input belongs to another pipeline, or the node key is the id of another
     *         node of the pipeline
     * @throws NullPointerException if a value is {@code null}
     */
    public NodeRef code(String name, NodeRef input, Code options) {
        return code(name, List.of(input), options);
    }

    /**
     * Adds a {@code notify} node with no input yet; {@link NodeRef#then(NodeRef)} gives it one.
     *
     * @param name the node's name, such as {@code "Alert"}
     * @param options where the message goes, the message and the node's options
     * @return the node
     * @throws PipelineException if the node key is the id of another node of the pipeline
     * @throws NullPointerException if a value is {@code null}
     */
    public NodeRef notify(String name, Notify options) {
        return notify(name, List.of(), options);
    }

    /**
     * Adds a {@code notify} node that runs after another node and passes that node's output through.
     *
     * @param name the node's name, such as {@code "Alert"}
     * @param input the node it runs after, whose row count the message can name
     * @param options where the message goes, the message and the node's options
     * @return the node
     * @throws PipelineException if the input belongs to another pipeline, or the node key is the id of another
     *         node of the pipeline
     * @throws NullPointerException if a value is {@code null}
     */
    public NodeRef notify(String name, NodeRef input, Notify options) {
        return notify(name, List.of(input), options);
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
     * @throws dev.brokoli.bundle.BundleException if the task project cannot be packaged, or its bundle would be over
     *         a cap core applies on upload
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
        String id = add(IrFields.TASK, bundle.name(), config, bundle.nodeInterface().orElse(null), from,
                NodeOptions.defaults());
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
        addEdge(from.nodeId(), to.nodeId());
    }

    private DatasetRef transform(String name, List<NodeRef> inputs, Transform options) {
        Objects.requireNonNull(name, "name");
        ObjectNode config = JSON.objectNode();
        if (!options.rules().isEmpty()) {
            config.set(IrFields.RULES, TransformRules.parse(name, options.rules()));
        }
        options.node().writeRetriesAndTimeout(config);
        return new DatasetRef(add(IrFields.TRANSFORM, name, config, null, inputs, options.node()), this);
    }

    private NodeRef sinkFile(String name, List<NodeRef> inputs, SinkFile options) {
        ObjectNode config = JSON.objectNode();
        config.put(IrFields.PATH, options.path());
        config.put(IrFields.FORMAT, options.format());
        options.compress().ifPresent(compress -> config.put(IrFields.COMPRESS, compress));
        options.connId().ifPresent(connId -> config.put(IrFields.CONN_ID, connId));
        options.node().writeRetriesAndTimeout(config);
        options.node().writePool(config);
        return new NodeRef(add(IrFields.SINK_FILE, name, config, null, inputs, options.node()), this);
    }

    private NodeRef bash(String name, List<NodeRef> inputs, Bash options) {
        ObjectNode config = JSON.objectNode();
        config.put(IrFields.COMMAND, options.command());
        options.workingDir().ifPresent(workingDir -> config.put(IrFields.WORKING_DIR, workingDir));
        putStrings(config, IrFields.ENV, options.env());
        options.maxMemoryMb().ifPresent(megabytes -> config.put(IrFields.MAX_MEMORY_MB, megabytes));
        options.maxCpuTime().ifPresent(cpuTime -> config.put(IrFields.MAX_CPU_SECONDS, cpuTime.toSeconds()));
        options.node().writeRetriesAndTimeout(config);
        options.node().writePool(config);
        return new NodeRef(add(IrFields.BASH, name, config, null, inputs, options.node()), this);
    }

    private NodeRef qualityCheck(String name, List<NodeRef> inputs, QualityCheck options) {
        Objects.requireNonNull(name, "name");
        ObjectNode config = JSON.objectNode();
        if (!options.rules().isEmpty()) {
            config.set(IrFields.RULES, JsonValues.rules(IrFields.QUALITY_CHECK + " " + name + ": " + IrFields.RULES,
                    options.rules()));
        }
        options.node().writeRetriesAndTimeout(config);
        return new NodeRef(add(IrFields.QUALITY_CHECK, name, config, null, inputs, options.node()), this);
    }

    private NodeRef sinkDb(String name, List<NodeRef> inputs, SinkDb options) {
        ObjectNode config = JSON.objectNode();
        config.put(IrFields.TABLE, options.table());
        config.put(IrFields.MODE, options.mode().irValue());
        options.connId().ifPresent(connId -> config.put(IrFields.CONN_ID, connId));
        options.uri().ifPresent(uri -> config.put(IrFields.URI, uri));
        if (!options.keyColumns().isEmpty()) {
            options.keyColumns().forEach(config.putArray(IrFields.KEY_COLUMNS)::add);
        }
        options.truncate().ifPresent(truncate -> config.put(IrFields.TRUNCATE, truncate));
        options.node().writeRetriesAndTimeout(config);
        options.createTable().ifPresent(createTable -> config.put(IrFields.CREATE_TABLE, createTable));
        options.tableEngine().ifPresent(engine -> config.put(IrFields.TABLE_ENGINE, engine));
        options.node().writePool(config);
        return new NodeRef(add(IrFields.SINK_DB, name, config, null, inputs, options.node()), this);
    }

    private NodeRef sinkApi(String name, List<NodeRef> inputs, SinkApi options) {
        ObjectNode config = JSON.objectNode();
        config.put(IrFields.URL, options.url());
        config.put(IrFields.METHOD, options.method());
        putStrings(config, IrFields.HEADERS, options.headers());
        options.connId().ifPresent(connId -> config.put(IrFields.CONN_ID, connId));
        options.batchSize().ifPresent(size -> config.put(IrFields.BATCH_SIZE, size));
        options.authUser().ifPresent(user -> config.put(IrFields.AUTH_USER, user));
        options.authPassword().ifPresent(password -> config.put(IrFields.AUTH_PASSWORD, password));
        options.node().writeRetriesAndTimeout(config);
        options.node().writePool(config);
        return new NodeRef(add(IrFields.SINK_API, name, config, null, inputs, options.node()), this);
    }

    private NodeRef code(String name, List<NodeRef> inputs, Code options) {
        ObjectNode config = JSON.objectNode();
        config.put(IrFields.LANGUAGE, options.language().irValue());
        config.put(IrFields.SCRIPT, options.script());
        options.pythonPath().ifPresent(pythonPath -> config.put(IrFields.PYTHON_PATH, pythonPath));
        options.node().writeRetriesAndTimeout(config);
        options.nodePath().ifPresent(nodePath -> config.put(IrFields.NODE_PATH, nodePath));
        options.maxMemoryMb().ifPresent(megabytes -> config.put(IrFields.MAX_MEMORY_MB, megabytes));
        options.maxCpuTime().ifPresent(cpuTime -> config.put(IrFields.MAX_CPU_SECONDS, cpuTime.toSeconds()));
        options.node().writePool(config);
        return new NodeRef(add(IrFields.CODE, name, config, null, inputs, options.node()), this);
    }

    private NodeRef notify(String name, List<NodeRef> inputs, Notify options) {
        ObjectNode config = JSON.objectNode();
        config.put(IrFields.NOTIFY_TYPE, options.type().irValue());
        config.put(IrFields.WEBHOOK_URL, options.webhookUrl());
        options.message().ifPresent(message -> config.put(IrFields.MESSAGE, message));
        options.channel().ifPresent(channel -> config.put(IrFields.CHANNEL, channel));
        options.node().writeRetriesAndTimeout(config);
        return new NodeRef(add(IrFields.NOTIFY, name, config, null, inputs, options.node()), this);
    }

    private static void putStrings(ObjectNode config, String field, Map<String, String> values) {
        if (!values.isEmpty()) {
            ObjectNode object = config.putObject(field);
            new TreeMap<>(values).forEach(object::put);
        }
    }

    private String add(String type, String nodeName, ObjectNode config, JsonNode nodeInterface,
            List<NodeRef> inputs, NodeOptions options) {
        Objects.requireNonNull(nodeName, "name");
        inputs.forEach(this::requireOwn);
        String id = options.nodeKey().map(this::requireFreeKey).orElseGet(() -> allocateId(nodeName));
        nodes.put(id, new Node(id, type, nodeName, config, Capabilities.of(type), nodeInterface));
        inputs.forEach(input -> addEdge(input.nodeId(), id));
        return id;
    }

    private String requireFreeKey(String nodeKey) {
        if (nodes.containsKey(nodeKey)) {
            throw new PipelineException("pipeline " + name + " already has a node with the id " + nodeKey
                    + "; give the node another key");
        }
        return nodeKey;
    }

    private void addEdge(String from, String to) {
        Edge edge = new Edge(from, to);
        if (!edges.contains(edge)) {
            edges.add(edge);
        }
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
        ir.put(IrFields.SCHEDULE, schedule);
        ir.put(IrFields.ENABLED, true);
        ir.put(IrFields.IR_VERSION, usesTaskInterfaces() ? TASK_INTERFACE_IR_VERSION : IR_VERSION);
        ArrayNode nodeList = ir.putArray(IrFields.NODES);
        nodes.values().forEach(node -> nodeList.add(node.toJson()));
        ArrayNode edgeList = ir.putArray(IrFields.EDGES);
        edges.forEach(edge -> edgeList.add(edge.toJson()));
        ArrayNode tagList = ir.putArray(IrFields.TAGS);
        tags.forEach(tagList::add);
        ArrayNode dependsOnList = ir.putArray(IrFields.DEPENDS_ON);
        dependsOn.forEach(dependsOnList::add);
        if (!scheduleTimezone.isEmpty()) {
            ir.put(IrFields.SCHEDULE_TIMEZONE, scheduleTimezone);
        }
        if (catchUp) {
            ir.put(IrFields.CATCHUP, true);
        }
        if (!slaDeadline.isEmpty()) {
            ir.put(IrFields.SLA_DEADLINE, slaDeadline);
            ir.put(IrFields.SLA_TIMEZONE, slaTimezone);
        }
        if (!hooks.isEmpty()) {
            ObjectNode hookMap = ir.putObject(IrFields.HOOKS);
            hooks.forEach((event, hook) -> hookMap.set(event, hookJson(hook)));
        }
        if (webhook) {
            ir.put(IrFields.WEBHOOK_TOKEN, "");
        }
        if (!parameters.isEmpty()) {
            ObjectNode parameterMap = ir.putObject(IrFields.PARAMETERS);
            parameters.forEach((parameter, declaration) -> parameterMap.set(parameter, declaration.deepCopy()));
        }
        return ir;
    }

    private boolean usesTaskInterfaces() {
        return !parameters.isEmpty() || nodes.values().stream().anyMatch(node -> node.nodeInterface() != null);
    }

    private static ObjectNode hookJson(Hook hook) {
        ObjectNode json = JSON.objectNode();
        json.put(IrFields.TYPE, hook.type());
        json.put(IrFields.URL, hook.url());
        json.put(IrFields.ENABLED, hook.enabled());
        if (!hook.extra().isEmpty()) {
            ObjectNode extra = json.putObject(IrFields.EXTRA);
            new TreeMap<>(hook.extra()).forEach(extra::put);
        }
        return json;
    }

    /**
     * The settings of a pipeline before its first node is added.
     */
    public static final class Builder {

        private static final DateTimeFormatter DEADLINE = DateTimeFormatter.ofPattern("HH:mm");

        private final String name;
        private String pipelineId = "";
        private String description = "";
        private String schedule = "";
        private String scheduleTimezone = "";
        private boolean catchUp;
        private String slaDeadline = "";
        private String slaTimezone = "";
        private List<String> tags = List.of();
        private List<String> dependsOn = List.of();
        private boolean webhook;
        private final Map<String, Hook> hooks = new LinkedHashMap<>();
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
         * Sets the pipeline's schedule, a cron expression the server runs it on; without one the pipeline runs
         * only when it is started.
         *
         * @param schedule the cron expression, such as {@code "0 * * * *"}
         * @return this builder
         * @throws NullPointerException if the schedule is {@code null}
         */
        public Builder schedule(String schedule) {
            this.schedule = Objects.requireNonNull(schedule, "schedule");
            return this;
        }

        /**
         * Sets the time zone the schedule is read in, the server's UTC unless set.
         *
         * @param zone the time zone, such as {@code ZoneId.of("Africa/Maputo")}
         * @return this builder
         * @throws IllegalArgumentException if the zone is not an IANA time zone, such as a fixed offset other than
         *         UTC
         * @throws NullPointerException if the zone is {@code null}
         */
        public Builder scheduleTimezone(ZoneId zone) {
            this.scheduleTimezone = TimeZones.irId("schedule time zone", Objects.requireNonNull(zone, "zone"));
            return this;
        }

        /**
         * Sets whether the server, after downtime, runs the pipeline once for every schedule interval it missed,
         * oldest first, in place of one run for the last one. Off unless set, and the server must support data
         * intervals.
         *
         * @param catchUp whether missed intervals are run
         * @return this builder
         */
        public Builder catchUp(boolean catchUp) {
            this.catchUp = catchUp;
            return this;
        }

        /**
         * Sets the time of day, in UTC, by which a run of the pipeline must finish.
         *
         * @param deadline the time, in whole minutes, such as {@code LocalTime.of(7, 30)}
         * @return this builder
         * @throws IllegalArgumentException if the time has seconds
         * @throws NullPointerException if the time is {@code null}
         */
        public Builder sla(LocalTime deadline) {
            return sla(deadline, ZoneOffset.UTC);
        }

        /**
         * Sets the time of day by which a run of the pipeline must finish.
         *
         * @param deadline the time, in whole minutes, such as {@code LocalTime.of(7, 30)}
         * @param zone the time zone the time is read in, such as {@code ZoneId.of("Africa/Maputo")}
         * @return this builder
         * @throws IllegalArgumentException if the time has seconds, or the zone is not an IANA time zone, such as
         *         a fixed offset other than UTC
         * @throws NullPointerException if a value is {@code null}
         */
        public Builder sla(LocalTime deadline, ZoneId zone) {
            Objects.requireNonNull(deadline, "deadline");
            if (deadline.getSecond() != 0 || deadline.getNano() != 0) {
                throw new IllegalArgumentException("SLA deadline " + deadline
                        + " has seconds; the server takes hours and minutes");
            }
            this.slaTimezone = TimeZones.irId("SLA time zone", Objects.requireNonNull(zone, "zone"));
            this.slaDeadline = DEADLINE.format(deadline);
            return this;
        }

        /**
         * Sets the pipelines that must succeed before this one runs, none unless set.
         *
         * @param pipelineIds the pipelines' ids, such as {@code "upstream-etl"}
         * @return this builder
         * @throws NullPointerException if an id is {@code null}
         */
        public Builder dependsOn(String... pipelineIds) {
            this.dependsOn = List.of(pipelineIds);
            return this;
        }

        /**
         * Sets whether the pipeline can be started by a webhook, whose token the server makes when it creates the
         * pipeline. Off unless set.
         *
         * @param webhook whether the pipeline has a webhook
         * @return this builder
         */
        public Builder webhook(boolean webhook) {
            this.webhook = webhook;
            return this;
        }

        /**
         * Sets the hook the server calls when a run starts.
         *
         * @param hook the hook
         * @return this builder
         * @throws NullPointerException if the hook is {@code null}
         */
        public Builder onStart(Hook hook) {
            return hook(IrFields.ON_START, hook);
        }

        /**
         * Sets the URL the server posts to when a run starts, as {@link Hook#webhook(String)}.
         *
         * @param url the URL, such as {@code "https://hooks.example/runs"}
         * @return this builder
         * @throws IllegalArgumentException if the URL is empty
         * @throws NullPointerException if the URL is {@code null}
         */
        public Builder onStart(String url) {
            return onStart(Hook.webhook(url));
        }

        /**
         * Sets the hook the server calls when a run succeeds.
         *
         * @param hook the hook
         * @return this builder
         * @throws NullPointerException if the hook is {@code null}
         */
        public Builder onSuccess(Hook hook) {
            return hook(IrFields.ON_SUCCESS, hook);
        }

        /**
         * Sets the URL the server posts to when a run succeeds, as {@link Hook#webhook(String)}.
         *
         * @param url the URL, such as {@code "https://hooks.example/runs"}
         * @return this builder
         * @throws IllegalArgumentException if the URL is empty
         * @throws NullPointerException if the URL is {@code null}
         */
        public Builder onSuccess(String url) {
            return onSuccess(Hook.webhook(url));
        }

        /**
         * Sets the hook the server calls when a run fails.
         *
         * @param hook the hook
         * @return this builder
         * @throws NullPointerException if the hook is {@code null}
         */
        public Builder onFailure(Hook hook) {
            return hook(IrFields.ON_FAILURE, hook);
        }

        /**
         * Sets the URL the server posts to when a run fails, as {@link Hook#webhook(String)}.
         *
         * @param url the URL, such as {@code "https://hooks.example/oncall"}
         * @return this builder
         * @throws IllegalArgumentException if the URL is empty
         * @throws NullPointerException if the URL is {@code null}
         */
        public Builder onFailure(String url) {
            return onFailure(Hook.webhook(url));
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
         * @throws PipelineException if the pipeline catches up but has no schedule
         */
        public Pipeline build() {
            if (catchUp && schedule.isEmpty()) {
                throw new PipelineException("pipeline " + name + " catches up but has no schedule; catch-up runs"
                        + " the schedule intervals the server missed, so give it a schedule");
            }
            return new Pipeline(this);
        }

        private Builder hook(String event, Hook hook) {
            hooks.put(event, Objects.requireNonNull(hook, "hook"));
            return this;
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
