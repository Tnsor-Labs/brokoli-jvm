# Contributing

This repo follows core's [CONTRIBUTING.md](https://github.com/Tnsor-Labs/brokoli/blob/main/CONTRIBUTING.md). What differs, or is specific to this repo, is below.

## Getting set up

You need JDK 17, 21 and 25, because CI builds on all three and a change can pass on one and fail on another. [SDKMAN](https://sdkman.io/) installs them side by side. `sdk list java` shows the identifiers, which change with every update; take the newest Temurin 17, 21 and 25, such as `17.0.21.0+9-tem`:

```sh
sdk list java
sdk install java <identifier>
sdk use java <identifier>
```

On Windows without WSL, `winget install EclipseAdoptium.Temurin.17.JDK`, and the same for 21 and 25, then set `JAVA_HOME` to the one you build with. Nothing else is needed: the Maven wrapper downloads Maven 3.9.11 and checks its sha256.

```sh
./mvnw verify
./mvnw -pl brokoli-task-processor -am test '-Dtest=TypeMapperTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

The first builds and tests every module, as CI does, except the end-to-end test, which needs a server (see [below](#the-end-to-end-test)). The second runs one test class; `-am` builds the modules it depends on first, and the last flag lets those modules run no test. On Windows, use `.\mvnw.cmd`, and keep the quotes around each `-D` in PowerShell, which otherwise splits it at the dot.

**IntelliJ IDEA.** Open the root `pom.xml` as a project. IntelliJ takes the annotation processors from each module's `annotationProcessorPaths` and turns on annotation processing for those modules, so `brokoli-benchmarks` gets its generated `<Class>_BrokoliTasks` classes. If they are missing, check Settings, Build, Execution, Deployment, Compiler, Annotation Processors, and reload the Maven project.

**VS Code.** The Extension Pack for Java runs the annotation processors of a Maven project by itself. If a generated class is missing, run `./mvnw compile` once and then the `Java: Clean Java Language Server Workspace` command.

The copies of core's contract and of the Python SDK's output that the tests use are described in [Core's contract](#cores-contract) and [The Python reference](#the-python-reference), with how to take newer ones.

## Branches

`develop` is where work lands, and `main` only holds releases. Branch from `develop` as `feat/short-description` or `fix/short-description` and open the PR against `develop`. It is squash-merged, so the PR title (Conventional Commits, `feat: ...`, `fix: ...`) becomes the commit on `develop`.

A release is a PR from `develop` to `main`, merged with a merge commit and never squashed, so the two branches don't diverge. The release is then tagged `vX.Y.Z` on `main`.

`main` stays the default branch, so the repo opens on the last release. That has two consequences:

- GitHub acts on `Closes #NN` only in a PR to the default branch, so a PR merged into `develop` closes nothing. Whoever merges it closes its issues by hand.
- GitHub reads the issue forms and the PR template only from the default branch, so a change to `.github/ISSUE_TEMPLATE/` or `.github/pull_request_template.md` takes effect with the next release.

## Issues and labels

Open an issue with one of the forms: **Feature** for something the SDK does not do yet, **Bug** for something it does wrong or differently from the Python SDK or core, and **Task** for work that changes no behaviour, such as docs, build, CI or a refactor. Each asks for the problem, the proposed approach, what done means as a checklist, and what is out of scope.

At triage every issue gets:

- its type, Task, Bug or Feature, which the form sets;
- one `area/*` label: `area/task-api` (`brokoli-task`: `@Task`, `@Default`, `Row` and `Rows`), `area/processor` (`brokoli-task-processor`: the task file and the generated entry method), `area/pipeline` (`brokoli-pipeline`: the builder, the IR and bundle packaging), `area/build-ci` (the Maven build, CI and the vendored copies) or `area/docs` (README, CONTRIBUTING, ADRs and templates);
- one `priority/*` label: `priority/now` (in the current milestone, being worked on), `priority/next` (planned for the next milestone) or `priority/later` (wanted, not scheduled yet);
- a milestone, one per stage of the plan in #3, each with a due date.

`good first issue` marks an issue a newcomer can take without reading an ADR first: it names the files to change, what done means and how to test it. At least five are kept open. `help wanted` marks work we would like help with at any level.

A PR carries the labels and milestone of its issue, links the issue, and has an assignee.

## Pull requests

The PR template has five sections, and every PR fills them in:

- **Why:** what fails or is missing without the change.
- **What changed:** per module, and the analogue in the Python SDK, the TypeScript SDK or core that it follows.
- **How it was verified:** the commands you ran and what they printed, the mutation checks, and for a fix the test that fails without it. Say what was not run.
- **Follow-ups:** each one filed as an issue and linked.
- **Closes:** `Closes #NN`.

Write each section as a few short paragraphs, not a list of files. The title is in Conventional Commits and at most 72 characters, because it becomes the squash commit on `develop`. Add an entry under `[Unreleased]` in [CHANGELOG.md](CHANGELOG.md) naming yourself as its owner.

Keep a PR to one change a reviewer can read in one sitting. Vendored files and CI changes land in their own PR, ahead of the code that uses them, so the code's PR is checked against copies that are already reviewed.

Commit messages explain why in prose, not as a list of changes. No AI co-authorship on commits, issues or PRs.

## Decisions

Decisions that only concern this SDK are recorded in [`docs/adr/`](docs/adr/README.md) as `JVM-ADR-NNN`. Ones that change something shared with core go in core's ADRs. Read the ADR for an area before changing it, and update it in the same PR when the change affects what it describes.

## Core's contract

The schemas, fixtures and reference bundles this SDK is tested against are copies of core's, in [`core-contract/`](core-contract/README.md), pinned to the core commit in `core-contract/CORE_REF`. They are never edited by hand. To take a newer core, run `core-contract/vendor.sh` against a core checkout at that commit, update `CORE_REF`, and open the result as its own PR, ahead of any code that depends on it. It needs Go, because the reference bundles are built with core's own CLI. Some reference inputs have paths of up to 180 characters, on purpose, to cover how a tar writes long paths. On Windows, clone with `git config --global core.longpaths true` set, or into a short folder such as `C:\src`.

## The Python reference

The pipeline builder is checked against the Python SDK, the reference core's `ir-canonicalization.md` names. [`python-reference/`](python-reference/README.md) holds the Python fixtures of the TypeScript SDK's differential suite and the canonical rendering and digest the Python SDK prints for each, pinned to the SDK commits in `python-reference/REFS`. They are never edited by hand. To take newer SDKs, run `python-reference/vendor.sh` against both checkouts at those commits, which needs `python3` and Linux or WSL, update `REFS`, and open the result as its own PR, ahead of any code that depends on it.

## The end-to-end test

The invoker IT `run-on-core` in `brokoli-maven-plugin` builds a task project with the plugin. It then builds a pipeline with `brokoli-pipeline` that reads two rows into the task, and checks that the pipeline packaged the task into the same bundle as the plugin. It uploads that bundle and the pipeline to a core server, runs the pipeline, and checks the rows the task returns. CI runs it on every JDK, against the core release named by `CORE_VERSION` in `.github/workflows/ci.yml`, after checking the archive against `CORE_SHA256`. A plain `./mvnw verify` leaves it out, because it needs a running server. To run it, start a core release with `brokoli serve --api-key <key>` from a folder outside the checkout, since core writes its data where it runs. It listens on port 8080. Then:

```sh
BROKOLI_E2E_SERVER=http://127.0.0.1:8080 BROKOLI_E2E_API_KEY=<key> ./mvnw verify '-Dinvoker.test=*'
```

To take a newer core release, change `CORE_VERSION` and `CORE_SHA256` together, with the sha256 from the release's `checksums.txt`.

## Benchmarks

CI's `Benchmarks` job measures the SDK on JDK 17 and only reports: the results go in the job's summary and in the `benchmarks` artifact, and nothing fails the build yet. `brokoli-benchmarks` holds them and is never published.

- **Cold start and memory.** `ColdStart` uploads the bundle of the no-op task `Noop.noop` and the vendored `python-noop` bundle to core, and runs each 31 times, taking turns. The first run of each is the first attempt; for the JVM it starts with an empty harness cache. `brokoli-benchmarks/bin/java` and `bin/python3`, first on core's `PATH`, run each harness core starts for an attempt under `/usr/bin/time`, through `measure.sh`, and record its wall time and peak RSS in `jvm.txt` or `python.txt`. Every other call, such as core's version checks, goes straight to the real binary. Core passes the variables the scripts need through `BROKOLI_CODE_PASS_ENV`.
- **1M rows.** `LargeInput` writes 1000000 rows of six columns to a JSON file and runs a `source_file` into three tasks that keep the paid rows, taking turns, three times each: the JVM SDK task `Lines.paidLines`, the raw `Map` method `Lines.paidLineMaps`, bundled with core's own `brokoli bundle jvm`, and the vendored `python-paid` bundle. It reports each task node's duration and its harness's peak RSS. `LinesTest` checks that the raw `Map` method returns what the SDK task returns, and that each JVM task declares the interface digest of the Python task it is compared with.
- **Row decoding.** `RowsBenchmark` is a JMH class that runs 100000 rows of six columns, a decimal and a timestamp among them, through the generated entry methods for a `List` of records and for `Rows`. Each is compared with a raw `Map` task that gives the same result: records with one that decodes and writes back the six columns by hand, `Rows` with one that reads only the column it filters on. `OrdersTest` checks that each pair returns the same rows, so a baseline cannot drift into doing less work.

To run JMH locally, `./mvnw package -DskipTests` and then `java -jar brokoli-benchmarks/target/benchmarks.jar`. The cold start and 1M rows need Linux, GNU time, Python 3 and a core server, started as the `Benchmarks` job in `.github/workflows/ci.yml` starts it.

## What done means

- `./mvnw verify` passes on JDK 17, 21 and 25. CI runs all three, with the end-to-end test.
- Public API has Javadoc, and the build fails on a public type or member without it. Javadoc says what the API does and what it refuses, not how it works. A Maven plugin parameter is public API too: Maven takes its description in `mvn help:describe` from the Javadoc on its field. Code outside the public API has no comments: rename or restructure instead, and put the reasoning in the PR.
- `brokoli-task` has no dependencies outside test scope. The build fails if one is added.
