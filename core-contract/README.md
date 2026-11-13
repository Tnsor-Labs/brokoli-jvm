# Core contract

The parts of [Tnsor-Labs/brokoli](https://github.com/Tnsor-Labs/brokoli)'s contract this SDK is tested against, at the core commit in [`CORE_REF`](CORE_REF). Nothing here is edited by hand.

- **`docs/`** holds copies of core's files at the same paths, listed in [`VENDORED`](VENDORED): `task-interface-v1.json` and `task-bundle-v2.json`, the task interface's cross-SDK differential vectors with their expected renderings and digests, the canonical rendering vectors of `ir-canonicalization.md` section 2, and the task bundle's positive and negative manifests. `brokoli-task-processor`'s tests read them from the classpath.
- **`bundles/`** holds reference bundles made by core's own `brokoli bundle jvm` at `CORE_REF`. Each `<name>.args` is the command's arguments, run from `bundles/`, and `<name>.tar.gz` is what it wrote. The inputs in `bundles/input/` are fixed bytes: classes compiled once from `input/src`, the task files the processor wrote next to them, and a small jar. The four cases cover each way Go's `archive/tar` writes a path:

- `classes-only` and `classes-and-jar`: short paths, plain USTAR, with and without a jar under `lib/`.
- `long-paths`: a path over 100 characters that USTAR's prefix field can hold, and two whose last part is over 100 characters, which get a PAX header.
- `non-ascii-paths`: a class named `Magazyn$Ładunek` and a task method `zważ`, which get a PAX header and literal UTF-8 in the manifest.

Two Python bundles are there too, for the benchmarks to compare a JVM task with a Python task on the same `task` node path. Core has no CLI for a Python bundle, so `vendor.sh` builds them with core's own `taskbundlev2.Assemble`, through `bundles/python/assemble.go`, which it runs inside the core checkout. Each folder under `bundles/python/` is one bundle: `task.json` names it, its module and function and its interface, and `src/` holds its files. `assemble.go` writes the digests the way `brokoli bundle jvm` does, with the interface digest from core's `taskinterface.InterfaceDigest`.

- `python-noop`: `noop(input=())`, which returns its input. Like the JVM `Noop`, it declares no interface, so it runs as a source with no input, and core's digest of that is the one the JVM `Noop` bundle has.
- `python-paid`: `paid(input)`, which keeps the rows whose `paid` is true, over records of six columns.

The bundle plugin's parity test packages the same inputs and compares the result with these bundles below gzip: the manifest, the file list and the uncompressed tar. The gzip bytes are not compared, because neither Go nor zlib promises the same compressed bytes across versions.

## Checking and re-vendoring

`check.sh <core checkout>` rebuilds everything from that checkout into a temporary directory and fails, naming each file, when anything differs. It also fails when core has a fixture in a vendored directory that `VENDORED` does not list.

- The `core-contract` job runs it on every PR against core at `CORE_REF`, so it fails only when a file here was changed by hand.
- The `Core drift` workflow runs it every Monday, and on demand, against core's `main`, so it fails when core has moved on.

To re-vendor, check out core at the new commit and run:

```sh
core-contract/vendor.sh ../brokoli
git -C ../brokoli rev-parse HEAD > core-contract/CORE_REF
```

`vendor.sh` reads the files from core's git objects, not its working tree, so line endings match core on every platform, and it builds the CLI with the Go toolchain core's `go.mod` names. Commit the result in its own PR, with the new `CORE_REF`.
