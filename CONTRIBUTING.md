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

## What done means

- `./mvnw verify` passes on JDK 17, 21 and 25. CI runs all three.
- Public API has Javadoc, and the build fails on a public type or member without it. Javadoc says what the API does and what it refuses, not how it works. Code outside the public API has no comments: rename or restructure instead, and put the reasoning in the PR.
- `brokoli-task` has no dependencies outside test scope. The build fails if one is added.
