# Changelog

All notable changes to the Brokoli JVM SDK are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows [Semantic Versioning](https://semver.org/).

Every entry names its owner.

## [Unreleased]

### Added

- **Benchmarks** -- @DarzVadckawy. CI reports, on JDK 17 against core
  v0.22.1, the cold start of a no-op task over 30 runs, its first attempt
  with an empty harness cache, the peak RSS of its JVM, and a JMH run of
  100000 rows through records and `Rows`, each against a raw `Map` task
  that returns the same rows. The numbers go in the
  job summary and an artifact, next to the targets in #3. Nothing fails the
  build on them yet.
- **End-to-end test on core** -- @DarzVadckawy. CI downloads core's
  v0.22.1 release, checks its sha256, and starts it. On JDK 17, 21 and 25
  a task project is built with `brokoli-maven-plugin`, its bundle is
  uploaded, and a pipeline that reads two rows into the task runs. The test
  checks the rows the task returns, which come from its input, a run
  parameter and a resource in the bundle. A run that does not finish within
  120 s fails with its last status, and CI prints core's log when a job
  fails or is cancelled.
- **Task bundles** -- @DarzVadckawy. `brokoli-maven-plugin`'s `bundle` goal
  writes one task-bundle/v2 archive per `@Task` method when the project is
  packaged, and attaches it as `<finalName>-<method>.tar.gz` so `install`
  and `deploy` publish it. A bundle holds the project's classes and
  resources and its `compile` and `runtime` dependency jars under `lib/`,
  and its manifest names the task's display name, its generated entry
  method, the builder and the digest of the task's interface. The tar is
  byte for byte what core's `brokoli bundle jvm` writes for the same files,
  long and non-ASCII paths included. The goal refuses a project with no
  task file, two dependency jars with the same file name, two tasks with
  the same method name, a resource named `manifest.json` at the root, a
  path that would be both a file and a directory, and symbolic links. `brokoli-bundle` holds the
  packaging, for builds other than Maven.
- **Pinned core contract** -- @DarzVadckawy. `core-contract/` holds the
  copies of core's schemas and fixtures the tests use, now including
  `task-bundle-v2.json`, and reference bundles made by core's own
  `brokoli bundle jvm`, all pinned to the core commit in `CORE_REF`. The
  PR check compares them with core at that commit, so it fails only when
  a copy was edited by hand. A weekly job compares them with core's
  `main`, so it fails when core has moved on. The reference bundles cover
  long paths and non-ASCII names, which Go's tar writes with the USTAR
  prefix field and PAX headers. The copies include core's canonical
  rendering vectors, the cross-SDK cases for how a document is written
  before it is hashed, and the expected interface and parameters digest
  of every differential vector. Two Python bundles, a no-op and one that
  keeps the paid rows, are built with core's own `taskbundlev2.Assemble`
  for the benchmarks to compare a JVM task with a Python one.
- **Same task file on every JDK** -- @DarzVadckawy. A `float64` in the task
  file is written as the shortest text that reads back as the same number,
  in the form RFC 8785, Go and JavaScript use (`1e+23`, `5e-324`), so a
  JDK 17 build and a JDK 21 build of the same task give the same file.
  Before, JDK 17 wrote `1e23` as `9.999999999999999E22`.
- **Strict lint builds** -- @DarzVadckawy. The README shows the compiler
  arguments for a build with `-Xlint:all -Werror`: `-Xlint:-processing`,
  because the processor leaves `@Task` and `@Default` unclaimed so other
  processors still see them. Tests keep both behaviours in place.
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
