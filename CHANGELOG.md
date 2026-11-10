# Changelog

All notable changes to the Brokoli JVM SDK are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows [Semantic Versioning](https://semver.org/).

Every entry names its owner.

## [Unreleased]

### Added

- **Generated entry method** -- @DarzVadckawy. For every class with tasks
  the processor generates `<Class>_BrokoliTasks`, with one entry method
  per task that core's JVM harness calls with its `Map` of input rows and
  run parameters. It reads them into the method's types, in the tagged
  form core uses for decimals, timestamps, durations and bytes, ignores
  run parameters the task does not declare, and writes back what the task
  returns. A value of the wrong form is refused with a message naming
  where it is, such as `input[3].amount`. The task file names each entry
  method in its new `entrypoint` field. `Row.getDecimal` reads a tagged
  decimal exactly, and a plain number through its decimal text.
- **Task processor** -- @DarzVadckawy. `brokoli-task-processor` checks
  every `@Task` method at compile time and writes its task interface and
  pipeline parameters to `META-INF/brokoli/tasks/<class>.json`. It maps
  the same types as the Python SDK's inference, writes the range of an
  `int` or `float` so core refuses a value the task cannot hold, reads
  `@Default` values into the form core validates, and treats any annotation
  named `Nullable` as nullable. It refuses with a compile error a method or
  type it cannot describe, a record or enum the task's package cannot
  reach, and text that is not valid Unicode. A task whose types another
  processor generates is read again in the next round. Its output matches
  core's cross-SDK differential vectors, and every task file the tests
  write validates against core's `task-interface-v1.json`.
- **Task API** -- @DarzVadckawy. `brokoli-task` has `@Task` to mark a
  task method, `@Default` to make a task parameter optional, and `Row` and
  `Rows` for rows whose schema a task does not declare. A row holds only
  values the harness can write as JSON, and `Row.of` and `with` refuse any
  other value, naming the column and the path inside it. `getDouble` reads
  a whole number, because the JVM harness decodes one as a `Long`, but
  refuses a `Long` it would round. Every typed getter names the column
  when its value is missing, null or of another type, and a stream of rows
  ends in `Rows` with `Rows.toRows()`. `@Task` and `@Default` take effect
  once the annotation processor lands. The javadoc jar is built, and a
  public type or member without Javadoc fails the build.
- **Architecture decision records** -- @DarzVadckawy. `docs/adr/` holds
  the decisions that only concern the JVM SDK, in core's format, starting
  with JVM-ADR-001 on the task API.
- **Maven build and CI** -- @DarzVadckawy. A Maven reactor with two
  modules, `brokoli-task` (the task API, with no dependencies, enforced by
  the build) and `brokoli-pipeline` (authoring and packaging), built for
  Java 17. The Maven wrapper pins Maven 3.9.11 and checks its sha256. CI
  runs `./mvnw verify` on JDK 17, 21 and 25.
