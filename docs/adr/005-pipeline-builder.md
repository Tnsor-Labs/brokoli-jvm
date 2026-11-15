# JVM-ADR-005: The pipeline builder

**Status:** proposed
**Date:** 2026-11-14

## Context

A JVM user should be able to write a pipeline in Java and get the same IR and `sha256:` digest the Python SDK gives for the same pipeline. Core's `docs/schema/ir-canonicalization.md` makes the Python SDK the reference and defines normalization (section 1), rendering (section 2), the digest (section 3) and the keys a compiler emits (section 4). `python-reference/` holds the Python fixtures of the TypeScript SDK's differential suite and what the Python SDK prints for each, so the builder can be checked against them byte for byte.

What the builder has to match, at brokoli-sdk `6bc8e70`, brokoli-typescript `062ef7a` and core `191d3fd`:

- **The API shape.** The Python SDK adds a node with a function per node type inside a `with Pipeline(...)` block and draws edges with `>>` (`brokoli/nodes.py`, `brokoli/pipeline.py`). The TypeScript SDK has a method per node type on `Pipeline`, taking an options object, and draws edges with `then` (`src/pipeline.ts`). Java has no operator overloading, and a thread-bound "current pipeline" does not suit it.
- **The keys and their values.** `Pipeline.to_json()` writes `pipeline_id`, `name`, `description`, `schedule`, `enabled`, `ir_version`, `nodes`, `edges`, `tags` and `depends_on`, and `parameters` only when a task declares one. `ir_version` is `2.2` when a node has an interface or the pipeline has parameters, and `2.0` otherwise. A node's capabilities come from `NODE_TYPE_CAPABILITIES`, with `compute` for any type it does not list. A node id is `_make_id(name)` and a per-base counter, and a missing `pipeline_id` is `_generate_pipeline_id(name)`.
- **The ordering.** `normalize_ir` sorts `tags`, `depends_on`, capabilities and nodes by each value's `json.dumps` text, not by the raw string. The two orders differ when a value is a prefix of another followed by a space or `!`, or holds a `"` or `\`. So Python writes the tags `daily` and `daily x` as `["daily x", "daily"]`.
- **The task node.** Neither SDK writes core's `task` node yet. Its config is `{"task_bundle": {"digest", "format": "brokoli.task-bundle/v2"}}`, and core checks it in `engine/validate.go`. The closest analogue is Python's `@task(package="bundle")`, which packages the task while the pipeline is built, references the archive by digest, and keeps the archives in `Pipeline.task_bundles` for the deployer to upload.

The research behind the API also covered AWS CDK and Pulumi Java, which type each resource's options, Conductor's Java SDK, and the Java SDK of Airflow 3.3, which names a task by class (`dag.task("extract", Extract.class)`).

## Decision

`brokoli-pipeline` has a `Pipeline` with one method per node type, named as in the Python and TypeScript SDKs, each taking an immutable options record. An edge is drawn with `NodeRef.then` or by passing the input to the method. The pipeline compiles to Python's keys and values, and normalizes, renders and digests as core's spec says, with Python's ordering. A `task` node names its `@Task` method by class and method name, and the pipeline packages the task from a `TaskProject` through `brokoli-bundle`, as Python packages `@task(package="bundle")`. Times are `java.time` values: a node's timeout and retry delay are a `Duration`, written as the seconds and milliseconds the server reads, and the pipeline's SLA is a `LocalTime` and a `ZoneId`. The options every node has, a node key, retries, a retry delay and a timeout, come from the `WithNodeOptions` interface, and the pool from `WithPool`, as the TypeScript SDK shares `RetryOptions` and `PoolOption` across its option types. An options record implements one of them and writes only `node(NodeOptions)`; a `transform` implements `WithNodeOptions` alone, because it runs in no pool.

## Consequences

### Positive

- The `minimal` fixture renders and digests byte for byte as the Python SDK does, and the end-to-end test deploys a pipeline built by the builder to core and checks the rows its task returns.
- A misspelled option is a compile error, and an option a node does not have cannot be written.
- A pipeline packages its tasks into the same bytes as `brokoli-maven-plugin` of the same version. The end-to-end test checks that the two digests are equal.
- `taskBundles()` holds every archive the pipeline references, so a deployer uploads exactly those.
- A retry delay cannot be given in seconds by mistake, which the Python SDK's docs warn about for `retry_delay=1`. A delay or timeout finer than the server reads, and a fixed offset in place of a time zone, are refused while the pipeline is built.

### Negative

- A `task` node packages the whole project on the pipeline's first `task` call, so building the pipeline reads every class and jar of the project.
- A task named by its method's name is checked when the pipeline is built, not when it is compiled.
- The builder sorts as the Python SDK does, and the TypeScript SDK's `normalizeIR` sorts by raw code point. For the tags `daily` and `daily x`, the two SDKs give different digests. This is raised in the PR, for the TypeScript SDK and for the wording of the spec's ordering rule.
- Node ids follow the JDK's Unicode tables, so a letter added in a Unicode version the JDK does not have yet is dropped from an id where Python keeps it.
- A time zone is checked against the JDK's time zone data, and core checks it against Go's, so a zone added to one before the other is refused by the builder or by the server.

### Deferred

- The other node types. They are added group by group, each checked against the fixtures in `python-reference/`.
- Deploying: uploading the bundles and creating the pipeline on a server.

## Alternatives considered

- **Reading the archive the plugin wrote.** The digest is the archive's SHA-256, as a Kubeflow component pins its image. The pipeline code would have to build first and know where the archive is.
- **Binding the digest at deploy time.** The builder would write the node without a digest and a later plugin goal would fill it in. Until then the pipeline would have no IR to render or digest.
- **A generated reference per task,** such as `Readings_BrokoliTasks.scale()`. A misspelled method would fail at compile time, but the generated code ends up in every bundle and would need a type `brokoli-task` does not have.
- **Options as maps,** as the TypeScript SDK's `Config`. They take any key, and a misspelled one reaches the server. Maps stay only where the TypeScript SDK is untyped too: a `transform` rule.
- **Times as numbers in the Python SDK's units,** an `int` of seconds for a timeout and of milliseconds for a retry delay. Temporal's Java SDK and the AWS SDK for Java v2 take a `Duration` instead, and Error Prone's `PreferJavaTimeOverload` flags an API that takes a number and a unit.
- **The SLA as one string,** `"07:30 Africa/Maputo"`, as the Python and TypeScript SDKs take it. A wrong time or zone would reach the server.
- **The node options as their own argument,** as Pulumi Java takes `CustomResourceOptions` next to a resource's args and Temporal's `ActivityOptions` takes `RetryOptions`. No record would need the methods, but one node's options would be split over two objects, where the Python and TypeScript SDKs take them together.
- **The methods copied into each record.** A copy that rebuilds the record with the wrong field compiles, and every new node type adds five more. Java's own withers for records, JEP 468, are still a candidate.

## Follow-ups

- The remaining 10 fixtures in `python-reference/`, node group by node group.
- The ordering difference between the Python and TypeScript SDKs, raised in the PR.
