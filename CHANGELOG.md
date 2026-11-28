# Changelog

All notable changes to the Brokoli JVM SDK are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows [Semantic Versioning](https://semver.org/).

Every entry names its owner.

## [Unreleased]

### Fixed

- **Bundles over core's caps** -- @DarzVadckawy. `brokoli:bundle` and
  `Pipeline.task` refuse a bundle that core would refuse on upload: more
  than 10000 tar entries, directories included, more than 64 MiB of
  files, or an archive over 64 MiB. The message names the cap, the
  bundle's size or entry count, and the five largest files or the five
  directories with the most entries, where before `mvn package` wrote the
  bundle and the upload failed. The plugin deletes a bundle it refused
  while writing it.
- **Errors from the generated entry methods** -- @DarzVadckawy. A task
  that returns NaN or an infinity in a `double` or `float` fails with the
  value's path, such as `result[3].score is NaN, expected a finite
  number`, where before the harness refused it with no path. The generated
  class writes `java.lang.String` and `java.lang.Object`, so a class named
  `String` or `Object` in the task's package no longer stops it compiling.
  A nested `Tasks.Inner` and a top-level `Tasks_Inner`, which both give
  `Tasks_Inner_BrokoliTasks`, get one compile error naming both, where
  before the second failed with `Attempt to recreate a file`.
- **Nullable map keys, rows and ports, and generic records** -- @DarzVadckawy.
  The task processor refuses a map with `@Nullable` keys, whose keys a
  Brokoli map never has, where before it dropped the annotation. A
  `@Nullable` row type, `List<@Nullable Reading>`, is written as a nullable
  row, as the Python SDK writes `list[Optional[Reading]]`, where before the
  row was written as non-null. `@Nullable` input rows and a `@Nullable`
  result are refused, because the input rows are never null and a task
  with nothing to return returns no rows. A generic record is refused with
  a message that names the record and its type parameters, where before it
  named a type variable as having no Brokoli type.
- **One edge for an input given twice** -- @DarzVadckawy. A node whose
  input is given twice, such as `p.task(Scores.class, "score", rows, rows)`,
  gets one edge from it, as the Python SDK's `_add_edge` and the TypeScript
  SDK's `edge` write it. Before, the pipeline had the same edge twice, so
  its IR and digest differed from the Python SDK's.

### Added

- **Dataset schemas** -- @DarzVadckawy. `sourceApi`, `sourceDb` and
  `sourceFile` take a `schema`, the columns of the rows they read, made
  from `DatasetSchema`, `Column`, `DataType` and `RecordField` and written
  as core's `brokoli.dataset-schema/v1`. Each column type has a factory,
  and a union, which core refuses in a dataset schema, has none. When both
  inputs of a `join` declare a schema, or are joins that wrote one, the
  join writes the schema of its output as the Python SDK derives it, and
  refuses a key that is not a column of its side's schema, keys whose
  types differ, and a right column its collision policy cannot name, each
  with a message that names the join and the columns. The `dataset_schema`
  and `dataset_schema_join` pipelines of `python-reference/` render byte
  for byte as the Python SDK renders them.
- **HTTP, code and notify nodes** -- @DarzVadckawy. `sourceApi` reads
  the records an HTTP endpoint returns as a dataset, `sinkApi` sends its
  input's rows to one in batches, `code` runs a Python or TypeScript
  script on its input, and `notify` sends a message to a webhook or to
  Slack, each written with the keys, order and values of the Python SDK,
  including the `_schema_hint` of a `source_api`. A script's language and
  a message's type are enums. `Code.python` and `Code.typescript` start a
  script with the runtime option its language takes, `Notify.slack` is the
  only type that takes a channel, and `basicAuth` sets a user with its
  password. A `sourceApi` reads a dataset only, and a `sinkApi` writes no
  body, which core does not read.
- **Database, join and quality check nodes** -- @DarzVadckawy. `sourceDb`
  reads the rows a query returns, `join` joins two inputs on a key column of
  each, `qualityCheck` checks its input against rules given as maps, and
  `sinkDb` writes its input to a table, each written with the keys, order
  and values of the Python SDK, including the `_schema_hint` of a
  `source_db`. A join's type and collision policy and a sink's write mode
  are enums. `Join.on(left, right)` names a key on each side, and a name
  holding `=` is read whole. `rightAlias` sets the alias collision policy
  and `upsert` the upsert mode with its key columns, so an alias policy
  without an alias, an alias with another policy, and an upsert without key
  columns cannot be built. The `data` and `join_collision_alias` pipelines
  of `python-reference/` render byte for byte as the Python SDK renders
  them.
- **How we work** -- @DarzVadckawy. Issues open with one of three forms,
  Feature, Bug or Task, which set the issue's type and ask for the
  problem, the approach, what done means and what is out of scope; the
  bug form also asks for the steps, the expected and actual result and
  the JDK. A blank issue is no longer offered. The PR template has the
  five sections every PR fills in. CONTRIBUTING now covers setting up
  JDK 17, 21 and 25, running one test, annotation processing in IntelliJ
  and VS Code, what each label means, the triage rule, PR size, and why
  `main` stays the default branch. The forms and the template take effect
  with the next release, because GitHub reads them from `main`.
- **Shared node options** -- @DarzVadckawy. `WithNodeOptions` gives the
  options of every node type `nodeKey`, `retries`, `retryDelay` and
  `timeout`, and `WithPool` adds `pool`, so a new node type gets them by
  implementing one interface and writing `node(NodeOptions)`. `SourceFile`,
  `SinkFile` and `Bash` implement `WithPool`, and `Transform` implements
  `WithNodeOptions` alone, as it runs in no pool. The methods and what they
  write are unchanged.
- **Local Python reference cases** -- @DarzVadckawy.
  `python-reference/local/` holds pipelines the differential suite does
  not cover yet, written with the Python SDK, and what the Python SDK
  prints for each at the commit in `REFS`: an SLA with no time zone next
  to a schedule in UTC, a retry delay and a timeout without retries, and
  a node key equal to the id the next node would get. `check.sh` runs
  them with the suite's fixtures, so the PR check and the weekly drift
  job cover them too. `brokoli-pipeline`'s tests build the three with the
  pipeline builder and check that each renders and digests byte for byte
  as the Python SDK does.
- **Pipeline settings, node options and `bash`** -- @DarzVadckawy. A
  pipeline takes its schedule and schedule time zone, catch-up, SLA,
  `depends_on`, webhook and its `on_start`, `on_success` and `on_failure`
  hooks, and every node takes a node key, retries with their backoff, a
  retry delay, a timeout and a pool, as the Python SDK writes them. A
  timeout and a retry delay are a `Duration`, written as the seconds and
  milliseconds the server reads, and the SLA is a `LocalTime` and a
  `ZoneId`. `bash` adds a `bash` node. `sourceFile` and `sinkFile` take a
  connection, and `sinkFile` a compression. The `options` and `bash`
  pipelines of `python-reference/` render byte for byte as the Python SDK
  renders them. The builder refuses a node key the Python SDK refuses or
  another node already has, catch-up without a schedule, a delay or
  timeout finer than the server reads, an SLA with seconds, and a time
  zone that is not an IANA one.
- **Pipeline builder** -- @DarzVadckawy. `brokoli-pipeline`'s `Pipeline`
  builds a pipeline with `sourceFile`, `transform`, `sinkFile` and `task`
  nodes, each taking a record of its options, and draws edges with `then`.
  `toJson()` is the IR with the keys and values the Python SDK writes,
  `render()` its canonical rendering and `digest()` its `sha256:`. The
  `minimal` pipeline of `python-reference/` renders byte for byte as the
  Python SDK renders it. A `task` node names a `@Task` method by class and
  method name. The pipeline packages its task project into the same bundles
  the Maven plugin writes, references the task's bundle by digest, and adds
  the task's parameters to the pipeline's. The end-to-end test now builds
  its pipeline this way and uploads the pipeline's own bundle. A
  `transform` refuses a rule value with no JSON form, naming where it is,
  and a `rename` without a mapping. `brokoli-bundle`'s `TaskBundle` now
  gives its digest, interface and parameters.
- **Python reference** -- @DarzVadckawy. `python-reference/` holds the 13
  pipelines of the cross-SDK differential suite, written with the Python
  SDK, and the canonical rendering and digest the Python SDK gives each,
  pinned to the SDK commits in `REFS`, for the pipeline builder to be
  checked against. The PR check runs them again at the pins, and a weekly
  job against the SDKs' `main` reports drift. `core-contract/` now also
  holds core's `pipeline-ir-2.2.json`.
- **Benchmarks** -- @DarzVadckawy. CI reports, on JDK 17 against core
  v0.22.1, the cold start of a no-op task over 30 runs next to a Python
  no-op on the same `task` node path, its first attempt with an empty
  harness cache, the peak RSS of each harness, 1M rows through the SDK
  task, a raw `Map` task and a Python task with their duration and peak
  RSS, and a JMH run of 100000 rows through records and `Rows`, each
  against a raw `Map` task that returns the same rows. The numbers go in the
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
  `bundles/limits.json` holds the caps core puts on an uploaded bundle:
  its size, the total size of its files and its number of entries, each
  checked against core's own `taskbundlev2.Extract` at the cap and one
  over it. `dataset-schema-v1.json` and its positive and negative cases
  are there too, the contract of the `schema` a source declares and a
  join derives.
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
