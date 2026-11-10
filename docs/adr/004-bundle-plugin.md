# JVM-ADR-004: The bundle plugin

**Status:** proposed
**Date:** 2026-11-10

## Context

A task only runs on a Brokoli server as a task-bundle/v2 archive. Core's `brokoli bundle jvm` (`cmd/bundle.go`) can build one, but it is a separate binary, takes one entrypoint per call, and writes the source digest into `interface_digest` because it cannot see the task's interface. A Maven user should get one bundle per `@Task` method from `mvn package`, with nothing else to install.

What a second builder has to match, at core `4fd47c1`:

- **The archive.** `task-bundle-v2-canonicalization.md` (#987) defines it: a gzip stream with `MTIME` 0 and `OS` 255, directories first in byte order with mode 0700, then `manifest.json`, then files in byte order with mode 0600, all with mtime, uid and gid 0. A path is written the way Go's `archive/tar` writes it: in the name field when it fits, split into USTAR's prefix field when it can be, and otherwise behind a PAX header (amended in #988). The manifest is compact JSON in the `Manifest` struct's field order, with Go's escaping, which writes `\b` and `\f` as two-character escapes (#992). Parity between two builders is the uncompressed tar, because neither Go nor zlib promises the same deflate bytes.
- **The JVM payload.** `brokoli bundle jvm` packages `.class` files at their path under the classes directory, each `--jar` as `lib/<file name>`, and a payload `jvm-any` with `runtime` `jvm`, `os` and `arch` `any`, `effects` `pure`, and the entrypoint's class and method as `module` and `symbol`. `source_digest` and `payload_digest` are the SHA-256 of every path, a NUL byte and its bytes, in path order. The harness puts the bundle root and every jar under `lib/` on the classpath.
- **`interface_digest`.** It must equal the ADR-032 digest of the task node's interface. `task-interface-canonicalization.md` section 5 gives the formula, over the key-sorted canonical rendering with record `fields` sorted by name (section 4 rule 2). Core implements it in `pkg/taskinterface` (Tnsor-Labs/brokoli#991, closing #990), and every differential vector carries the expected rendering and digest of its interface, and of its parameters for a parameter vector. A task that declares no interface digests the interface a `task` node has then: an `input` and a `result` dataset of unknown rows.

The reference bundles in `core-contract/bundles`, made by `brokoli bundle jvm` at `CORE_REF`, cover each way a path is written.

## Decision

Two new modules. `brokoli-bundle` is a library that reads the task files the processor wrote and writes one archive per task, byte for byte as core's `Assemble` writes it. `brokoli-maven-plugin` is a thin goal, `brokoli:bundle`, bound to `package`, that calls it and attaches each archive to the build.

- **One bundle per task.** Every bundle of a project holds the same files and differs only in its manifest. Its `name` is the task's display name, and its entrypoint is the generated entry method (JVM-ADR-003).
- **Contents.** Everything under the classes directory except `META-INF/brokoli/`, so resources reach the task's classpath, as packagers such as Jib put them there. Each `compile` and `runtime` dependency jar goes under `lib/<file name>`, `brokoli-task` included. A `provided` dependency is left out. A symbolic link, a dependency that is not a file, and two files with the same path in the bundle are refused.
- **Attached as `<finalName>-<method>.tar.gz`,** with the method name as the classifier, so `install` and `deploy` publish the bundles next to the jar. Two tasks with the same method name are refused, because they would have the same classifier.
- **The manifest records the builder,** `"build":{"builder":"brokoli-jvm/<plugin version>"}`, the form core's own byte test uses. A new plugin version gives the same input a new bundle digest; `source_digest` stays the same.
- **`interface_digest` follows the spec.** The task's interface, with the `input` and `result` ports filled in as datasets of unknown rows when the task file has `"interface": null`, has every record's `fields` sorted by name in UTF-8 byte order, is rendered as `ir-canonicalization.md` section 2 says, and is hashed. The tests compare the rendering and the digest with every differential vector's expected fields. The plugin computed the same values before core published them, and they were proposed in Tnsor-Labs/brokoli#990.
- **The tar writer is written here,** about 200 lines, rather than taken from a library. It follows Go's `archive/tar` rules, which a general-purpose tar writer does not: the PAX header's name, the fields it leaves NUL, and the self-counting record length. `JsonNumbers` from `brokoli-task` writes the floats of the canonical rendering.

## Consequences

### Positive

- `mvn package` gives one bundle per task, and `mvn deploy` publishes them, with nothing to install beyond the plugin.
- A bundle the plugin writes from the same files is the same tar that `brokoli bundle jvm` writes. The parity test compares the two for all four reference bundles, and ten more paths are compared with what Go 1.26's `archive/tar` writes.
- Core's `taskbundlev2.Extract` reads the plugin's bundles and verifies every file's size and hash.

### Negative

- A change to section 5 in core changes every bundle digest. The vendored vectors make that a failing test here, in the PR that takes the new `CORE_REF`.
- A bundle's manifest differs from what `brokoli bundle jvm` writes for the same files in two fields, `interface_digest` and `build`, so their bundle digests differ even though the tar layout and files are the same.
- Bundles are written from memory, so the build needs heap for the project's classes and jars.

### Deferred

- A Gradle plugin, built on the same library.

## Alternatives considered

- **Running core's `brokoli bundle jvm` from the plugin.** It needs a Go binary per platform, and it cannot write `interface_digest`, since it does not read the task file.
- **Apache Commons Compress for the tar.** It writes PAX headers with its own names and fields, so the bytes would differ from Go's.
- **Only `.class` files, as core's CLI does.** A task that reads a resource with `getResource` would fail at run time.
- **Leaving `build` out, as core's CLI does.** The same input would keep its digest across plugin versions, but nothing would record which builder made a bundle.

## Follow-ups

- The week 5 end-to-end test runs a bundle from this plugin on a core server.
