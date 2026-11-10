# JVM-ADR-003: The generated entry method

**Status:** accepted
**Date:** 2026-11-10

## Context

[JVM-ADR-002](./002-task-processor.md) left one thing out: the method core actually calls. Core's JVM harness (`pkg/taskharness/jvmharness/harness/Harness.java`) loads the class and method the bundle's `entrypoint` names (`Class#method`, split by `ParseEntrypoint`). It calls the method as `public static` with one `java.util.Map` or with no arguments. A `@Task` method takes `Rows` or a `List` of a record, then typed parameters, so something has to sit in between.

What core passes and accepts, at `e9a3d8c`:

- **kwargs** are every run parameter of the pipeline, not only the task's (`engine/task.go`, `executeTaskBundle`).
- **Input rows** arrive under `input`, decoded by the harness's own JSON reader: a whole number is a `Long`, a fraction a `Double`. They arrive only when the node declares an `input` port, and the key is absent when there are no rows (`writeTaskInputDataset`).
- **The result** of a dataset port must be an `Iterable` of `Map`s that the harness's `Json.write` can encode: null, `String`, `Boolean`, `Long`, `Integer`, a finite `Double` or `Float`, `Map` and `Iterable`. A `Row`, a record or a `BigDecimal` cannot be written.
- **Values** follow `task-interface-canonicalization.md` section 4 and `pkg/taskinterface/value.go`. A `decimal`, `timestamp`, `duration` or `bytes` is tagged, as in `{"$bptd": "decimal", "value": "12.50"}`. A `date` is `YYYY-MM-DD` text. An `int64` is a whole number or a tagged one.

Two gaps in core's task-bundle path decide part of the design:

1. **A task node with no `input` port is a source.** It cannot receive edges, gets no rows, and its output is treated as `scalar` (`engine/validate.go`, `nodeIsSourceCapable`). Core's own differential vectors require `interface: null` for a task that takes and returns `Rows`. Python can follow that rule because its tasks are `code` nodes, which always get `rows`.
2. **Task-bundle kwargs are untyped.** `runTask` passes `r.varCtx.Params`, the legacy string map. It does not pass `r.parameters`, the typed values `ResolveParameters` produced with defaults applied, which a `code` node gets. On today's core a `double threshold` arrives as `"0.5"`, and a `@Default` never reaches the task.

## Decision

For every class that declares tasks, the processor generates `<Class>_BrokoliTasks` in the same package. A nested class is flattened with `_`, as AutoValue does: `Tasks.Inner` gives `Tasks_Inner_BrokoliTasks`. The generated class has one `public static Object <method>(Map<String, Object> kwargs)` per task, and the task file's new `entrypoint` field names it, as in `com.example.Tasks_BrokoliTasks#dailyRollup`.

Each entry method:

- reads `input` into the method's first parameter, and reads an absent `input` as no rows;
- reads each parameter by its name and ignores every other run parameter;
- calls the task;
- writes what it returned as maps of JSON values.

The conversion lives in `brokoli-task`, in `dev.brokoli.task.internal.TaskValues`. `brokoli-task` is on every task's classpath and has no dependencies. The package is exported for the generated code only and is not API, as `dagger.internal` is for Dagger. The generated code builds records and reads enum constants itself, which is why JVM-ADR-002 refuses a record or enum the task's package cannot reach. It holds no logic beyond calling `TaskValues` in the right order.

Values are read and written in core's form:

| Java | Read from | Written as |
|---|---|---|
| `long`, `Long` | a whole number, or a tagged `int64`; a whole number decoded as a `Double` only up to 2^53 - 1 | `Long` |
| `int`, `Integer` | the same, inside the `int` range | `Long` |
| `double`, `Double` | a number; a `Long` only if a `double` holds it exactly | `Double` |
| `float`, `Float` | the same, inside the `float` range | `Float` |
| `boolean`, `String` | the same JSON type | the same |
| `BigDecimal` | a tagged `decimal`, keeping its scale | a tagged `decimal`, `toPlainString` |
| `LocalDate` | `YYYY-MM-DD` | `YYYY-MM-DD`, years 0000 to 9999 |
| `Instant` | a tagged `timestamp` ending in `Z` | a tagged `timestamp`, UTC, years 0000 to 9999 |
| `Duration` | a tagged `duration` | a tagged `duration` with one sign |
| `byte[]` | tagged `bytes` | tagged `bytes`, padded base64 |
| an enum | a constant name | the constant name |
| `List`, `Map<String, T>`, a record | a list, an object | a list, an object, field by field |

A `@Default` value is written through the same writers (`DefaultReader`), so a default and a returned value cannot end up in different forms.

A value of the wrong form throws `TaskValueException`, whose message names where the value is: `parameter threshold is of type String, expected a number`, or `input[3].amount is missing, expected a value`. The harness reports it as `user_code` with the message.

On the two gaps:

1. **The task file keeps the inferred interface,** so it still matches core's vectors. One helper in `brokoli-pipeline` turns a null interface into an `input` and a `result` port of `{"kind": "dataset", "row": {"kind": "unknown"}}`. The bundle plugin (week 5) and the pipeline builder (weeks 6 and 7) both use it, so the node's interface and the bundle's `interface_digest` are computed from the same document.
2. **The entry method reads the typed form only:** the values `ResolveParameters` produces, the same `parameters` a Python `code` node gets. Core should pass `r.parameters` to a task-bundle node, tracked in Tnsor-Labs/brokoli#978.

`Row.getDecimal` reads a tagged decimal exactly, a `Long` or `Integer` exactly, and a `Double` or `Float` through its decimal text (`BigDecimal.valueOf`). It refuses anything else, naming the column.

## Consequences

### Positive

- The harness's contract stays unchanged, and a task author never sees a `Map`.
- Conversion is checked in both directions. The tests compile tasks, call the generated entry methods the way the harness does, and compare the result with the input field by field for every type. Every generated class compiles with no warning under `-Xlint:all`.
- The generated source is ASCII only, with `\uXXXX` escapes, so it compiles under any build encoding.

### Negative

- Until core passes typed parameters to task-bundle nodes, a JVM task with parameters fails on its first parameter, with a message naming it. The week 5 e2e test will show this until core changes.
- A refused value is reported as `user_code`, not `contract_violation`, because the harness classifies whatever the method throws as the task's own failure.
- An input row is copied twice on the way in: once into a map with `String` keys, and once by `Row.of`. The week 5 benchmarks measure whether that stays within the 10% budget.

### Deferred

- The helper that fills a null interface's ports, with the bundle plugin (week 5).
- Collection, artifact and scalar outputs. A `@Task` method returns `Rows` or a `List` of a record today, so only dataset outputs are generated.

## Alternatives considered

- **Reflection in `TaskValues` instead of generated code.** It needs `setAccessible` to build a package-private record, which fails when the task is in a named module that does not open its package, and it is slower per row.
- **All conversion inline in the generated class.** Every task class would repeat the same few hundred lines, and a fix would need every task to be recompiled.
- **A JSON library such as Jackson.** It would put a dependency on every task's classpath, which `brokoli-task` exists to avoid.
- **Also accepting the legacy string parameters.** That makes two input forms to keep. The generated code would also have to apply `@Default` itself, which repeats core's `ResolveParameters`.
- **The processor always writing ports.** That breaks core's rule that every SDK compiles the two `Rows` vectors to a null interface.

## Follow-ups

- Core passes `r.parameters`, with declared defaults, to task-bundle nodes as kwargs: Tnsor-Labs/brokoli#978. It affects Python and Node task bundles as well.
- Core stays as it is for a task node with no interface: an absent interface means the task declares nothing, and core does not guess ports for it (answered in the review of #17). The SDK fills in the `input` and `result` ports, from the one `brokoli-pipeline` helper that both the node and `interface_digest` use.
- The interface helper and `interface_digest` in the bundle plugin (#3, week 5).
- #18: locate a non-finite result, qualify `java.lang` names in the generated class, and report a clash between a nested class and a top-level one with the flattened name.
