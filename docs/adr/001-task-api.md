# JVM-ADR-001: Task API: `@Task`, `@Default`, `Row` and `Rows`

**Status:** accepted
**Date:** 2026-11-10

## Context

Core's [ADR-036](https://github.com/Tnsor-Labs/brokoli/blob/main/docs/adr/036-jvm-task-bundles-and-sdk.md) has users write a JVM task as an ordinary static method, and derives the task interface from its signature. Its example is:

```java
@Task(name = "daily-rollup")
public static Rows dailyRollup(Rows input, double threshold)
```

Several things constrain the API that makes this real:

- **The harness.** Core's `Harness.java` only calls a `public static` method that takes nothing or a single `java.util.Map`. Staged input rows arrive in that map under `"input"`, and every other entry is a pipeline run parameter (`engine/task.go`, where run parameters become the task's kwargs). It cannot call `dailyRollup(Rows, double)` directly.
- **How the harness reads JSON.** `Json.java` decodes a number without a fraction as `Long` and one with a fraction as `Double`. A value of `5` in a column of prices therefore arrives as a `Long`, and a cast to `Double` fails on it.
- **How the harness writes JSON.** It writes each returned row with `Json.write`, which encodes only `null`, `String`, `Boolean`, `Long`, `Integer`, a finite `Double` or `Float`, `Map` and `Iterable`. Anything else fails after the task has finished, with no column named.
- **The classpath.** ADR-036 puts the task API on the classpath of every task, so anything it depends on can clash with the task's own dependencies.
- **What Python already does.** The Python SDK is the reference. Its `@task` takes untyped rows (`list[dict]`, which leaves the row schema unknown) or typed rows (a `TypedDict` or dataclass, which gives a record schema), in `brokoli/interface_inference.py`. Every other annotated parameter becomes a pipeline parameter: optional with its default if it has one, required if not. Core applies declared defaults on the server, in `pkg/taskinterface` `ResolveParameters`, after checking each run parameter against its declared type.
- **What Java lacks.** Java has no default arguments, and `Map.copyOf` refuses null values and does not keep order, while JSON rows have both.

## Decision

The task API is the `brokoli-task` module, one exported package, `dev.brokoli.task`. It has no dependencies outside test scope, and the build enforces that. It contains:

- **`@Task`**, which marks a `public static` method as a task. It takes only a name, which defaults to the method name split into title-case words (`dailyRollup` becomes `Daily Rollup`), as Python's `_resolve_name` does.
- **`@Default("...")`**, which makes a task parameter optional with that value. The value is written as text and read as the parameter's type. A parameter without it is required.
- **`Rows` and `Row`**, which carry rows whose schema the task does not declare. `Rows` is an unmodifiable `List<Row>` with `filter`, `map` and a `toRows()` collector, so stream code ends in `Rows`. `Row` is an immutable, ordered map of column to value, with typed getters (`getString`, `getLong`, `getDouble`, `getBoolean`) and `with`, which returns a changed copy.
- **`RowValueException`**, thrown by the getters when a column is missing, null or of another type, and by `Row.of` and `with` for a value the harness cannot write. It names the column, and the path inside it for a nested value (`attrs.size`, `tags[2]`). It never includes the value, because rows are customer data.

Typed rows are a `List` of a Java record. The record's components declare the row schema, the same way Python's dataclass and `TypedDict` do.

A `Row` holds only what `Json.write` can encode: `Row.of` and `with` check each value, and recurse into nested lists and maps. The getters refuse rather than convert, with one exception: `getDouble` accepts a whole number, because the harness decodes `5` as a `Long`. Even then, it refuses a `Long` that a `double` cannot hold exactly. The check is `d < 0x1p63 && (long) d == l`, because `(long) d == l` alone passes for `Long.MAX_VALUE`, whose `double` is 2<sup>63</sup> and casts back by saturating. `getLong` accepts `Long` and `Integer`, the two whole-number types a row can hold.

`@Task` and `@Default` have `CLASS` retention. Only the annotation processor reads them, at compile time, and the bundle plugin reads the interface JSON the processor emits, so nothing needs them by reflection at run time.

Value types use record-style accessors (`columns()`, `toMap()`). `RowValueException` uses `get` accessors (`getColumn()`), because an exception's accessors sit next to `getMessage` and `getCause`.

The annotation processor (next PR, in its own `brokoli-task-processor` module) does three things for each `@Task` method:

- generates the `public static Object name(Map)` entry method the harness calls, which converts between the map and the method's parameters and return value;
- emits the task interface JSON, with each `@Default` checked against its parameter's type at compile time;
- refuses a `@Task` method that is not `public static` or whose types it cannot map.

## Consequences

### Positive

- The harness is used as it is, with no change in core. The generated entry method plays the part of the Python SDK's `TASK_WRAPPER_TEMPLATE` in `brokoli/pipeline.py`.
- A value the harness cannot write is refused at the call that set it, naming the column, instead of failing inside the harness after the task has run.
- `getDouble` reads the `Long` that `Json.java` produces for a whole number, but never rounds a 64-bit id. `getLong` never truncates a fraction. Every refusal names the column and what it held (`column 'ratio' is of type Double, expected a whole number`).
- A task declares its row schema with a plain Java record. Users learn no SDK type for it, and the compiler checks every field access.
- Run settings (retries, timeouts, failure handling) stay on the task's node in the pipeline, where the IR holds them. A task bundle stays the same whichever pipeline uses it.
- `@Default` follows the pattern Java developers already know from JAX-RS `@DefaultValue` and picocli's `@Option(defaultValue = ...)`. Like picocli's processor, ours checks the value at compile time.

### Negative

- `@Task` and `@Default` do nothing until the annotation processor lands. Until then their Javadoc describes behaviour the processor has to deliver exactly as written.
- A default written as text is checked at compile time, but it is still a string in the source, not a typed constant.
- Each value is checked when a row is created, including the input rows the harness hands the task, which are already valid JSON. The check is a type test per value, plus recursion into nested lists and maps, and the throughput benchmark measures its cost.
- `Row` copies the columns once when it is created, and `with` copies them again, which costs a map copy per changed row. Lists and maps nested inside a value are checked but not copied, so changing one afterwards bypasses the check.
- `Row.toString` includes every value, so logging a row copies its data into the log. Its Javadoc says so.
- `RowValueException` is excluded from the documented serialized form (`@serial exclude`), so doclint does not require comments on its private fields.

### Deferred

- **Getters for `decimal`, `date`, `timestamp` and `duration`.** These come with the processor's type mapping. `getDecimal` comes first: core's Oracle native driver (#964) returns `NUMBER(p,s)` as exact decimal text such as `"1234.56"`, which `getDouble` refuses today.
- **Named ports beyond the single `input` and `result`.** Python's inference only covers those two (ADR-032 section 6), and so does this API.
- **Streaming input.** Every harness hands the task a complete list, so a task's memory grows with its input. That is core's to change, through its harness contract.

## Alternatives considered

- **Only `Rows` and `Row`, as in ADR-036's sketch.** Every row schema would stay unknown until a later version. It was rejected because records give typed schemas at no cost to users.
- **No SDK row types, with `List<Map<String, Object>>` as the untyped form.** Nothing new to learn, but every read needs a cast, `(Double) row.get("price")` fails on the `Long` the harness produces for `5`, and nothing stops a value the harness cannot write.
- **`Optional<T>` parameters instead of `@Default`.** The task would apply its own fallback, and the interface could not show the default to the pipeline or the UI.
- **Python's options on `@Task` (`retries`, `timeout`, `on_failure`).** Python puts them on the decorator because the decorator also places the node in the pipeline. A Java task compiles into a bundle on its own, so these belong to the node.
- **`Map.copyOf` for a row's columns.** It refuses null values and does not keep order, and JSON rows have both.
- **Checking values only when rows are returned to the harness.** It costs nothing on input, but the failure would name a row and column far from the code that set the value.
- **`RUNTIME` retention.** It would let tests read the annotations by reflection, but nothing in a running task needs them, and `CLASS` is the usual choice for annotations that only a processor reads.
- **A separate `dev.brokoli.task.exception` package.** It would export a second package for one class. No second exception is expected: run parameters reach the task already checked against their declared types by `ResolveParameters`.

## Follow-ups

- Annotation processor, generated entry method and interface JSON (#3, week 4).
- `getDecimal`, reading exact decimal text and whole numbers.
- Core's ADR-036 update on the entry point signature, owned by its maintainer.
