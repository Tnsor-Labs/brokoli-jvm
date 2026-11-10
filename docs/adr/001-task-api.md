# JVM-ADR-001: Task API: `@Task`, `@Default`, `Row` and `Rows`

**Status:** proposed
**Date:** 2026-11-10

## Context

Core's [ADR-036](https://github.com/Tnsor-Labs/brokoli/blob/main/docs/adr/036-jvm-task-bundles-and-sdk.md) has users write a JVM task as an ordinary static method, and derives the task interface from its signature. Its example is:

```java
@Task(name = "daily-rollup")
public static Rows dailyRollup(Rows input, double threshold)
```

Several things constrain the API that makes this real:

- **The harness.** Core's `Harness.java` only calls a `public static` method that takes nothing or a single `java.util.Map`. Staged input rows arrive in that map under `"input"`, and every other entry is a pipeline run parameter (`engine/task.go`, where run parameters become the task's kwargs). It cannot call `dailyRollup(Rows, double)` directly.
- **How the harness decodes JSON.** `Json.java` decodes a number without a fraction as `Long` and one with a fraction as `Double`. A value of `5` in a column of prices therefore arrives as a `Long`, and a cast to `Double` fails on it.
- **The classpath.** ADR-036 puts the task API on the classpath of every task, so anything it depends on can clash with the task's own dependencies.
- **What Python already does.** The Python SDK is the reference. Its `@task` takes untyped rows (`list[dict]`, which leaves the row schema unknown) or typed rows (a `TypedDict` or dataclass, which gives a record schema), in `brokoli/interface_inference.py`. Every other annotated parameter becomes a pipeline parameter: optional with its default if it has one, required if not. Core applies declared defaults on the server, in `pkg/taskinterface` `ResolveParameters`.
- **What Java lacks.** Java has no default arguments, and `Map.copyOf` refuses null values and does not keep order, while JSON rows have both.

## Decision

The task API is the `brokoli-task` module. It has no dependencies outside test scope, and the build enforces that. It contains four things:

- **`@Task`** marks a `public static` method as a task. It takes only a name, which defaults to the method name split into title-case words (`dailyRollup` becomes `Daily Rollup`), as Python's `_resolve_name` does.
- **`@Default("...")`** makes a task parameter optional with that value. The value is written as text and read as the parameter's type. A parameter without it is required.
- **`Rows` and `Row`** carry rows whose schema the task does not declare. `Rows` is an unmodifiable `List<Row>` with `filter` and `map`. `Row` is an immutable, ordered map of column to value. Its typed getters (`getString`, `getLong`, `getDouble`, `getBoolean`) throw `RowValueException` naming the column when it is missing, null or of another type, and `with` returns a changed copy.
- **Typed rows are a `List` of a Java record.** The record's components declare the row schema, the same way Python's dataclass and `TypedDict` do.

The annotation processor (next PR, in its own `brokoli-task-processor` module) does three things for each `@Task` method:

- generates the `public static Object name(Map)` entry method the harness calls, which converts between the map and the method's parameters and return value;
- emits the task interface JSON, with each `@Default` checked against its parameter's type at compile time;
- refuses a `@Task` method that is not `public static` or whose types it cannot map.

## Consequences

### Positive

- The harness is used as it is, with no change in core. The generated entry method plays the part of the Python SDK's `TASK_WRAPPER_TEMPLATE` in `brokoli/pipeline.py`.
- `getDouble` accepts any number, so the `Long` that `Json.java` produces for a whole number reads correctly. The other getters refuse rather than convert, so `getLong` never truncates a fraction, and every refusal names the column and what it held (`column 'ratio' is of type Double, expected a whole number`).
- A task declares its row schema with a plain Java record. Users learn no SDK type for it, and the compiler checks every field access.
- Run settings (retries, timeouts, failure handling) stay on the task's node in the pipeline, where the IR holds them. A task bundle stays the same whichever pipeline uses it.
- `@Default` follows the pattern Java developers already know from JAX-RS `@DefaultValue` and picocli's `@Option(defaultValue = ...)`. Like picocli's processor, ours checks the value at compile time.

### Negative

- `@Task` and `@Default` do nothing until the annotation processor lands. Until then the Javadoc describes behaviour the processor will deliver.
- A default written as text is checked at compile time, but it is still a string in the source, not a typed constant.
- `Row` copies the columns once when it is created, and `with` copies them again, which costs a map copy per changed row. Lists and maps nested inside a value are not copied.
- `RowValueException` is excluded from the documented serialized form (`@serial exclude`), so doclint does not require comments on its private fields.

### Deferred

- **Getters for `date`, `timestamp`, `decimal` and `duration`.** These need the wire encoding core uses for each type in NDJSON rows, and they come with the processor's type mapping.
- **Named ports beyond the single `input` and `result`.** Python's inference only covers those two (ADR-032 section 6), and so does this API.
- **Streaming input.** Every harness hands the task a complete list, so a task's memory grows with its input. That is core's to change, through its harness contract.

## Alternatives considered

- **Only `Rows` and `Row`, as in ADR-036's sketch.** Every row schema would stay unknown until a later version. It was rejected because records give typed schemas at no cost to users.
- **No SDK row types, with `List<Map<String, Object>>` as the untyped form.** Nothing new to learn, but every read needs a cast, and `(Double) row.get("price")` fails on the `Long` the harness produces for `5`.
- **`Optional<T>` parameters instead of `@Default`.** The task would apply its own fallback, and the interface could not show the default to the pipeline or the UI.
- **Python's options on `@Task` (`retries`, `timeout`, `on_failure`).** Python puts them on the decorator because the decorator also places the node in the pipeline. A Java task compiles into a bundle on its own, so these belong to the node.
- **`Map.copyOf` for a row's columns, as bsa-client copies collections.** It refuses null values and does not keep order, and JSON rows have both.

## Follow-ups

- Annotation processor, generated entry method and interface JSON (#3, week 4).
- Core's ADR-036 update on the entry point signature, owned by its maintainer.
