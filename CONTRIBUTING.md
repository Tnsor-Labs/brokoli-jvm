# Contributing

This repo follows core's [CONTRIBUTING.md](https://github.com/Tnsor-Labs/brokoli/blob/main/CONTRIBUTING.md). What differs, or is specific to this repo, is below.

## Branches

`develop` is where work lands, and `main` only holds releases. Branch from `develop` as `feat/short-description` or `fix/short-description` and open the PR against `develop`. It is squash-merged, so the PR title (Conventional Commits, `feat: ...`, `fix: ...`) becomes the commit on `develop`.

A release is a PR from `develop` to `main`, merged with a merge commit and never squashed, so the two branches don't diverge. The release is then tagged `vX.Y.Z` on `main`.

## Pull requests

Say why the change is needed and what fails without it, then how you checked it: the commands you ran and what they printed. For a fix, name the test that fails without it. Link the issue (`Closes #NN`) and add an entry under `[Unreleased]` in [CHANGELOG.md](CHANGELOG.md) naming yourself as its owner.

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
