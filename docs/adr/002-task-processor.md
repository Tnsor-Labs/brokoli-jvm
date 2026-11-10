# JVM-ADR-002: The task processor and its task file

**Status:** accepted
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

`brokoli-task-processor` is an annotation processor, in its own artifact. It writes one file per class that declares tasks: `META-INF/brokoli/tasks/<binary class name>.json`. The file is UTF-8 whatever the build's source encoding, written through a strict encoder. It holds `format` (`brokoli.jvm-tasks/v1`), the class, and for each task its method, display name, node interface (or `null`) and pipeline parameters. The bundle plugin and the pipeline builder read this file; nothing reads the annotations at run time.

The interface follows Python's inference rule for rule:

- `Rows` leaves a port's row unknown, and a `List` of a record declares it.
- When both ports are unknown, there is no interface (`null`).
- When only one is known, the other port is `{"kind": "dataset", "row": {"kind": "unknown"}}`.
- Record fields are all `required: true`, in declaration order, with `additional_fields: false`.

Types map as in Python:

| Java | BPTD kind |
|---|---|
| `long`, `Long` | `int64` |
| `int`, `Integer` | `int64` with `"minimum": "-2147483648"` and `"maximum": "2147483647"` |
| `double`, `Double` | `float64` |
| `float`, `Float` | `float64` with `minimum` and `maximum` at `-3.4028234663852886E38` and `3.4028234663852886E38` |
| `boolean`, `Boolean` | `boolean` |
| `String` | `string` |
| `BigDecimal` | `decimal` |
| `LocalDate` | `date` |
| `Instant` | `timestamp` |
| `Duration` | `duration` |
| `byte[]` | `bytes` |
| an enum with at least one constant, whose values are its constant names | `enum` |
| `List<T>` | `array` |
| `Map<String, T>` | `map` |
| a record | `record` |

The bounds on `int` and `float` apply wherever the type appears: a parameter, a record field, a list item or a map value. Core checks them on every value and every default (`pkg/taskinterface/value.go`), so it refuses a value the task could not hold instead of passing it on.

A record or enum must be reachable from the task's package, because the generated entry method lives there and has to build the record and read the constants. Neither it nor any class it is nested in may be `private`, and each must be `public` when it is in another package.

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
- **Bounds:** an `int` or `float` default must lie inside the bounds above.
- **Text:** a `@Default` value and a `@Task` name must be valid Unicode, so an unpaired surrogate is refused. Canonicalization rule 1 rejects invalid Unicode, and a lenient UTF-8 writer turns the surrogate into `?` without an error.

A type that does not resolve yet, because another processor generates it in a later round or because an import is missing, is not an error of this processor. The class is read again in the next round. If the type still does not resolve when processing ends, the class gets no task file and javac reports the missing symbol.

Anything the processor cannot describe is a compile error on that method or parameter. The message starts with `@Task method Class.method`, then the parameter and the path inside it, such as `@Task method Tasks.run parameter shift.starts[] is a java.time.LocalTime, which has no Brokoli type; use ...`. A class with an invalid task gets no task file.

The interface digest is computed by the bundle plugin, from the interface in this file, by the rule in `task-interface-canonicalization.md` section 5.

## Consequences

### Positive

- The task interface is derived from the signature at compile time, so the two cannot drift. This is ADR-036's main claim for a JVM SDK.
- The output is checked against core itself. The tests compile Java declarations for core's six differential vectors and compare the result field by field. Every task file any test writes is validated against core's schema, and CI fails when the vendored copies differ from core.
- A mistake shows up in the IDE as a compile error on the line that caused it, not as a failed run.

### Negative

- Python skips a parameter it cannot type, with a warning. Java refuses it, because the generated entry method (next PR) has to convert every parameter it passes to the task, and it cannot convert a type it does not know.
- `@Default` covers scalars only. A default for a `List`, `Map` or record parameter is refused.
- An edge from a producer with no bounds, such as a Python `int`, to a JVM `int` is `unverified` in core's assignability check, not compatible. Core does not block an `unverified` edge (`engine/validate.go`), and its bound check still refuses a value out of range at run time.
- Builds on JDK 23 and later must name the processor, for example in `annotationProcessorPaths`. The README shows how.

### Deferred

- The generated `Class_BrokoliTasks` entry method, and the `entrypoint` it adds to the task file.
- Per-node parameter bindings (ADR-032 section 7), which move parameters from pipeline level into the task interface, once core has a consumer for them.
- Parameter collisions. Parameters are pipeline level, so two tasks in one pipeline can declare the same name. The pipeline builder must do what Python's `Pipeline._merge_parameter` does: share a declaration that is identical, and refuse one that differs in any way, whether type, `required` or default.

## Alternatives considered

- **Reading the compiled classes in the bundle plugin, by reflection or bytecode.** It works without a processor, but every error would surface at packaging time instead of in the IDE, and the plugin would have to load user classes.
- **Skipping unmappable parameters with a warning, as Python does.** The entry method could not pass those parameters to the task.
- **Treating boxed types as nullable.** There would be no way to mark a `String` or a record nullable, and no way to make an `Integer` non-null.
- **JavaPoet for writing files.** Square's JavaPoet is archived; Palantir's fork is maintained, but it would be the processor's only dependency, for a JSON file and a short generated class.
- **One index file for all classes.** A per-class file lets incremental builds rewrite only the classes that changed.

## Follow-ups

- Generated entry method and `getDecimal` (#3, the second processor PR).
- Bundle plugin computing `interface_digest` from the task file (#3, week 5).

## Update (2026-11-10)

The task file now has an `entrypoint` for each task, `<package>.<Class>_BrokoliTasks#<method>`, naming the entry method the processor generates next to it. A class with an invalid task gets neither the file nor the generated class. A null interface stays null in the task file: [JVM-ADR-003](./003-generated-entry-method.md) records why a `task` node still needs `input` and `result` ports, and where they are filled in.

The processor never claims `@Task` or `@Default`: `process` returns `false`, so another processor still sees them, as Error Prone's DoNotClaimAnnotations rule and AutoValue do. Under `-Xlint:all`, javac then warns that no processor claimed them. The README shows `-Xlint:-processing` for a build that uses `-Werror` (#16).
