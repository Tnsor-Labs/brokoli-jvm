# JVM-ADR-002: The task processor and its task file

**Status:** proposed
**Date:** 2026-11-10

## Context

[JVM-ADR-001](./001-task-api.md) defines what a task method looks like. Something has to turn that signature into the contract core works with, which has two parts:

- **The node interface.** It sits on the task node in the IR (`"interface"` in core's `pipeline-ir-2.2.json`) and follows `task-interface-v1.json`. Core validates every input and output row against the record it declares (`engine/task_dataset.go`).
- **Pipeline parameters.** A task's other parameters become pipeline-level parameters, because no per-node parameter binding exists yet. Python's `interface_inference.py` does the same, and core's `fixtures/task-interface/differential/README.md` explains why.

Core never computes an interface digest itself. A task bundle carries `interface_digest`, and the engine only checks that the task's result echoes it (`engine/task.go`). For now `brokoli bundle jvm` puts the source digest there.

Core also owns six cross-SDK vectors in `docs/schema/fixtures/task-interface/differential/`. Every official SDK's tests must compile each vector's declaration to exactly its expected interface or parameters.

Two facts about the JVM shape the design:

- Only an annotation processor can read a method signature at build time without loading the user's classes.
- Since JDK 23, javac runs no processor it finds on the classpath unless the build names it.

## Decision

`brokoli-task-processor` is an annotation processor, in its own artifact. It writes one file per class that declares tasks: `META-INF/brokoli/tasks/<binary class name>.json`. The file holds `format` (`brokoli.jvm-tasks/v1`), the class, and for each task its method, display name, node interface (or `null`) and pipeline parameters. The bundle plugin and the pipeline builder read this file; nothing reads the annotations at run time.

The interface follows Python's inference rule for rule:

- `Rows` leaves a port's row unknown, and a `List` of a record declares it.
- When both ports are unknown, there is no interface (`null`).
- When only one is known, the other port is `{"kind": "dataset", "row": {"kind": "unknown"}}`.
- Record fields are all `required: true`, in declaration order, with `additional_fields: false`.

Types map as in Python:

| Java | BPTD kind |
|---|---|
| `long`, `int` and their boxed types | `int64` |
| `double`, `float` and their boxed types | `float64` |
| `boolean`, `Boolean` | `boolean` |
| `String` | `string` |
| `BigDecimal` | `decimal` |
| `LocalDate` | `date` |
| `Instant` | `timestamp` |
| `Duration` | `duration` |
| `byte[]` | `bytes` |
| an enum, whose values are its constant names | `enum` |
| `List<T>` | `array` |
| `Map<String, T>` | `map` |
| a record | `record` |

Nullability works like this:

- **Nullable:** a value is nullable when it carries any annotation whose simple name is `Nullable`, on the declaration or on the type. That includes JSpecify, Jakarta, JetBrains and the Checker Framework.
- **Non-null:** every other value. A boxed type is not nullable by itself.
- **Primitives:** `@Nullable` on a primitive is refused.

A `@Default` value is read as its parameter's type and written in the form core's `ResolveParameters` validates:

- **Plain values:** `int64`, `float64`, `boolean`, `string`, `enum` and `date`.
- **Tagged values:** `decimal`, `timestamp`, `bytes` and `duration`, written as `{"$bptd": ...}`, normalized as `task-interface-canonicalization.md` requires:
  - a timestamp in UTC with `Z`;
  - a duration with one sign;
  - no negative zero.

Anything the processor cannot describe is a compile error on that method or parameter. The message starts with `@Task method Class.method`, then the parameter and the path inside it, such as `@Task method Tasks.run parameter shift.starts[] is a java.time.LocalTime, which has no Brokoli type; use ...`. A class with an invalid task gets no task file.

The interface digest is computed by the bundle plugin, from the interface in this file, by the rule in `task-interface-canonicalization.md` section 5.

## Consequences

### Positive

- The task interface is derived from the signature at compile time, so the two cannot drift. This is ADR-036's main claim for a JVM SDK.
- The output is checked against core itself. The tests compile Java declarations for core's six differential vectors and compare the result field by field, and validate every interface and parameter set against core's schema. CI fails when the vendored copies differ from core.
- A mistake shows up in the IDE as a compile error on the line that caused it, not as a failed run.

### Negative

- Python skips a parameter it cannot type, with a warning. Java refuses it, because the generated entry method (next PR) has to convert every parameter it passes to the task, and it cannot convert a type it does not know.
- `@Default` covers scalars only. A default for a `List`, `Map` or record parameter is refused.
- Builds on JDK 23 and later must name the processor, for example in `annotationProcessorPaths`. The README shows how.

### Deferred

- The generated `Class_BrokoliTasks` entry method, and the `entrypoint` it adds to the task file.
- Per-node parameter bindings (ADR-032 section 7), which move parameters from pipeline level into the task interface, once core has a consumer for them.

## Alternatives considered

- **Reading the compiled classes in the bundle plugin, by reflection or bytecode.** It works without a processor, but every error would surface at packaging time instead of in the IDE, and the plugin would have to load user classes.
- **Skipping unmappable parameters with a warning, as Python does.** The entry method could not pass those parameters to the task.
- **Treating boxed types as nullable.** There would be no way to mark a `String` or a record nullable, and no way to make an `Integer` non-null.
- **JavaPoet for writing files.** Square's JavaPoet is archived; Palantir's fork is maintained, but it would be the processor's only dependency, for a JSON file and a short generated class.
- **One index file for all classes.** A per-class file lets incremental builds rewrite only the classes that changed.

## Follow-ups

- Generated entry method and `getDecimal` (#3, the second processor PR).
- Bundle plugin computing `interface_digest` from the task file (#3, week 5).
