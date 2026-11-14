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

`brokoli-pipeline` has a `Pipeline` with one method per node type, named as in the Python and TypeScript SDKs, each taking an immutable options record. An edge is drawn with `NodeRef.then` or by passing the input to the method. The pipeline compiles to Python's keys and values, and normalizes, renders and digests as core's spec says, with Python's ordering. A `task` node names its `@Task` method by class and method name, and the pipeline packages the task from a `TaskProject` through `brokoli-bundle`, as Python packages `@task(package="bundle")`.

## Consequences

### Positive

- The `minimal` fixture renders and digests byte for byte as the Python SDK does, and the end-to-end test deploys a pipeline built by the builder to core and checks the rows its task returns.
- A misspelled option is a compile error, and an option a node does not have cannot be written.
- A pipeline packages its tasks into the same bytes as `brokoli-maven-plugin` of the same version. The end-to-end test checks that the two digests are equal.
- `taskBundles()` holds every archive the pipeline references, so a deployer uploads exactly those.

### Negative

- A `task` node packages the whole project on the pipeline's first `task` call, so building the pipeline reads every class and jar of the project.
- A task named by its method's name is checked when the pipeline is built, not when it is compiled.
- The builder sorts as the Python SDK does, and the TypeScript SDK's `normalizeIR` sorts by raw code point. For the tags `daily` and `daily x`, the two SDKs give different digests. This is raised in the PR, for the TypeScript SDK and for the wording of the spec's ordering rule.
- Node ids follow the JDK's Unicode tables, so a letter added in a Unicode version the JDK does not have yet is dropped from an id where Python keeps it.

### Deferred

- The other node types and the pipeline's schedule, hooks, SLA and webhook. They are added group by group, each checked against the fixtures in `python-reference/`.
- `node_key`, and the per-node retries, timeout and pool.
- Deploying: uploading the bundles and creating the pipeline on a server.

## Alternatives considered

- **Reading the archive the plugin wrote.** The digest is the archive's SHA-256, as a Kubeflow component pins its image. The pipeline code would have to build first and know where the archive is.
- **Binding the digest at deploy time.** The builder would write the node without a digest and a later plugin goal would fill it in. Until then the pipeline would have no IR to render or digest.
- **A generated reference per task,** such as `Readings_BrokoliTasks.scale()`. A misspelled method would fail at compile time, but the generated code ends up in every bundle and would need a type `brokoli-task` does not have.
- **Options as maps,** as the TypeScript SDK's `Config`. They take any key, and a misspelled one reaches the server. Maps stay only where the TypeScript SDK is untyped too: a `transform` rule.

## Follow-ups

- The remaining 12 fixtures in `python-reference/`, node group by node group.
- The ordering difference between the Python and TypeScript SDKs, raised in the PR.
