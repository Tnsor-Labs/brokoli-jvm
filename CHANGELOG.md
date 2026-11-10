# Changelog

All notable changes to the Brokoli JVM SDK are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows [Semantic Versioning](https://semver.org/).

Every entry names its owner.

## [Unreleased]

### Added

- **Task processor** -- @DarzVadckawy. `brokoli-task-processor` checks
  every `@Task` method at compile time and writes its task interface and
  pipeline parameters to `META-INF/brokoli/tasks/<class>.json`. It maps
  the same types as the Python SDK's inference, reads `@Default` values
  into the form core validates, treats any annotation named `Nullable` as
  nullable, and refuses with a compile error a method or type it cannot
  describe. Its output matches core's cross-SDK differential vectors and
  validates against core's `task-interface-v1.json`.
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
