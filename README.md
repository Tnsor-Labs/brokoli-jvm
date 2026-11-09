# brokoli-jvm

The Brokoli SDK for the JVM. It lets you write Brokoli tasks in Java (and other JVM languages), package them as `jvm` task bundles, and build pipelines that compile to Brokoli IR.

It is not usable yet. The design is core's [ADR-036](https://github.com/Tnsor-Labs/brokoli/blob/main/docs/adr/036-jvm-task-bundles-and-sdk.md), and the work is tracked in this repo's issues.

## Modules

- `brokoli-task` is the task API: the `@Task` annotation and the row types. It has no dependencies, because it sits on the classpath of every task and anything it pulled in would clash with the task's own dependencies.
- `brokoli-pipeline` is the authoring side: the pipeline builder, IR emission and task bundle packaging.

## How it fits with the rest of Brokoli

- **Java 17 is the floor**, the same minimum core's JVM harness requires (`pkg/taskharness/jvmharness/invocation.go`). CI builds on 17, 21 and 25.
- **The IR matches the Python SDK byte for byte.** For the same pipeline, the IR and its `sha256:` digest must be identical to what [brokoli-sdk](https://github.com/Tnsor-Labs/brokoli-sdk) emits, following core's `docs/schema/ir-canonicalization.md`. [brokoli-typescript](https://github.com/Tnsor-Labs/brokoli-typescript) enforces the same rule with differential fixtures. Schemas are copied from core's `docs/schema/` and checked against it, never edited here.
- **Tasks run in core's JVM harness.** `Harness.java` calls a `public static` method that takes no arguments or a single `java.util.Map`, with staged input rows under the `"input"` key. The SDK builds on that contract.

## Building

```sh
./mvnw verify
```

On Windows use `mvnw.cmd`. Contributions follow [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Apache License 2.0, see [LICENSE](LICENSE).
