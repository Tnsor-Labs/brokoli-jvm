# Python reference

The pipelines the Python SDK compiles, and what it prints for each, that the JVM SDK's pipeline builder is checked against. Core's `docs/schema/ir-canonicalization.md` names the Python SDK as the reference: a second SDK must render the same pipeline byte for byte and give the same digest. Nothing here is edited by hand.

- **`fixtures/`** holds the Python half of [brokoli-typescript](https://github.com/Tnsor-Labs/brokoli-typescript)'s differential suite, `tests/differential/fixtures/*.py`, at the commit in [`REFS`](REFS). Each builds one pipeline with the Python SDK and prints its canonical rendering and digest. The TypeScript SDK runs the same files against its own builder.
- **`expected/`** holds what each fixture printed with [brokoli-sdk](https://github.com/Tnsor-Labs/brokoli-sdk) at the commit in `REFS`: `<name>.ir.json` is the canonical rendering, and `<name>.digest` is the `sha256:` digest, which is the SHA-256 of that file's bytes.

The JVM SDK's tests build each of these pipelines with its own builder and compare the rendering and the digest with `expected/`, so Maven needs no Python.

## Checking and re-vendoring

`check.sh <brokoli-sdk checkout> <brokoli-typescript checkout>` runs every fixture again into a temporary directory and fails, naming each file, when one differs, when the TypeScript SDK has a fixture that is not vendored, or when a vendored one is gone. It needs `python3`, Linux or WSL, and checkouts with no local changes, because the Python SDK runs from the checkout's files.

- The `python-reference` job runs it on every PR against both SDKs at the commits in `REFS`, so it fails only when a file here was changed by hand.
- The `Python reference drift` workflow runs it every Monday, and on demand, against both SDKs' `main`, so it fails when the Python SDK compiles a pipeline differently or the fixtures change.

To re-vendor, check out both SDKs at the new commits and run:

```sh
python-reference/vendor.sh ../brokoli-sdk ../brokoli-typescript
printf 'brokoli-sdk %s\nbrokoli-typescript %s\n' "$(git -C ../brokoli-sdk rev-parse HEAD)" "$(git -C ../brokoli-typescript rev-parse HEAD)" > python-reference/REFS
```

Commit the result in its own PR, with the new `REFS`.
