# AGENTS.md

## Working agreement — read before writing any code

- **An instruction is the decision.** When the maintainer states how something must be built, build it that way. If the instruction looks like it causes a problem, or departs from established practice, stop and say so: name the common practice, why it matters here, and ask with options (best practice first) before writing code. Never implement an alternative and explain it afterwards.
- **Name the analogue first.** Before the first line, name the existing code being mirrored — in this repo, or the matching feature in `brokoli-sdk` (Python, the reference) and `brokoli-typescript` — and copy its behaviour exactly. "Similar but better" is a deviation.
- **Technical choices are decided, behaviour choices are asked.** Package, class and method names, internal structure: decide and state the decision in one sentence. Anything that changes what a user of the SDK sees or emits: check what the Python SDK, the TypeScript SDK and core already do (name the files), then ask with options, recommended first.
- **Code everything, then verify once.** For a multi-point change, write all the points, then run the verification below. No intermediate compiles or partial test runs between points.
- **Every fix gets a test** that fails without the fix. A defect seen once is not a bug until it is reproduced and the line causing it is named.
- **Fix the cause wherever it occurs** in this SDK, not only at the call site reported. If the same defect exists in the Python or TypeScript SDK or in core, say so.
- **Zero comments in code**: no `//`, no `/* */`, no Javadoc, no `@SuppressWarnings` added to silence a warning. Rename or restructure instead; put the reasoning in the PR description.
- **English only** in identifiers, exception messages and every string the SDK emits. Errors name the thing that failed.
- Answers are short, answer first. Use the exact word the files, the IR or the maintainer use — never a synonym or a figure of speech.

## What this repo is

- The Brokoli JVM SDK. Its design is core's `docs/adr/036-jvm-task-bundles-and-sdk.md`: Java 17 floor (`pkg/taskharness/jvmharness/invocation.go` `MinJavaVersion`), published to Maven Central, a `@Task` annotation processor that derives the task interface from the method signature, IR emission and `jvm` task-bundle/v2 packaging.
- **Parity is the contract.** For the same pipeline the IR and its `sha256:` digest must be byte-identical to what the Python SDK emits. The rules are in core `docs/schema/ir-canonicalization.md`; `brokoli-typescript/tests/differential/` shows how that is enforced (each fixture written in both languages, compared byte for byte). Schemas are copied from core `docs/schema/` and checked against it, never edited here.
- **The runtime contract is the harness, not the ADR sketch.** Core's `pkg/taskharness/jvmharness/harness/Harness.java` only invokes a `public static` method taking `()` or a single `java.util.Map`; staged input rows arrive under the `"input"` key. The ADR's `dailyRollup(Rows input, double threshold)` does not match it.
- Sibling repos sit next to this one: `..\brokoli` (core), `..\brokoli-sdk`, `..\brokoli-typescript`. This account can push only here; changes needed in core (an ADR `## Update`, a schema) are raised in the PR description.

## Build and verification

- Maven with the Maven wrapper, a multi-module reactor, Java 17 (`<release>17</release>`). groupId and base package `dev.brokoli`.
- Verification is the full reactor: `.\mvnw.cmd clean verify`. Redirect to a file and read Maven's exit code (`.\mvnw.cmd clean verify > verify.log 2>&1; echo "EXIT=$LASTEXITCODE"`); never pipe the build into `head`/`tail`/`grep` and never judge it by a truncated log.
- Once a build starts the tree is read-only until it exits. Findings go on a list and are applied together afterwards.
- Never say "green", "build passed" or "all tests pass" unless the full run finished after the last edit. Say which suites ran (unit, integration, differential). If one could not run, say so and name what was not verified.

## Code style

The reference is the maintainer's `bsa-client` library (`..\..\Shared\Utils\bsa-client`).

- **Layout.** The public API is an interface at the package root (`BsaClient`). Implementations live in an `internal` package and are package-private wherever possible. Value types in `model`, exceptions in `exception`.
- **Value types are records.** The compact constructor validates with `Objects.requireNonNull(field, "field")`, copies collections with `List.copyOf`/`Map.copyOf`, and rejects invalid combinations with an `IllegalArgumentException` that names the values. Records hold data and validation only; derived rules live in separate classes.
- **Exceptions.** One root `RuntimeException` subclass per library carrying structured fields (status, title, detail, errors) with getters, and a message built by a `private static String describe(...)`. Subclasses only add the type. Translation from wire errors to exceptions is one class with a `switch` expression over named constants.
- **Holders and constants.** Static-only classes are `final` with a private constructor. No magic numbers or strings: `private static final int BAD_REQUEST = 400;`, paths and keys in one constants class.
- **Idioms.** Switch expressions; constructor injection into `private final` fields; small private methods named for what they do; catch variables named for what happened (`unreadable`, `notJson`, `unreachable`, `expired`). No Lombok, no wildcard imports.
- **Imports** in three groups separated by a blank line: project and third-party, then `java.*`, then static imports.
- **Formatting.** Four-space indent, eight for continuation lines.
- **Tests.** JUnit 5 and AssertJ (`assertThat`, `assertThatThrownBy`). Test classes are package-private and mirror the class under test (`RestBsaClientTest`). Method names state the behaviour as a sentence (`submitSendsThePeriodWithTheConfiguredInstCodeAndReturnsTheFileName`). JSON expectations are text blocks. HTTP clients are tested against WireMock. A guard is tested in both directions: the good input passes and the bad input is rejected.
- **POM.** Versions in `<properties>`, `<parameters>true</parameters>` on the compiler, sources jar attached, `developers` and `scm` filled in.

## Git and pull requests

- Never commit, push or tag unless asked.
- **Nothing is committed to `main` directly.** Two long-lived branches: `develop` and `main`. Every change is a `feat/` or `fix/` branch cut from `develop` and squash-merged into `develop` through a PR. `main` holds releases only: a release is a PR from `develop` to `main` merged with a merge commit (never squash, or the two branches diverge), followed by a `vX.Y.Z` tag on `main`.
- Tnsor-Labs rules (core `CONTRIBUTING.md`): branches `feat/short-description` or `fix/short-description`; PR titles in Conventional Commits (`feat: ...`, `fix: ...`, `docs: ...`), because the title becomes the squash commit; `Closes #NN` in the body; **no AI co-authorship on commits, issues or PRs** — no `Co-Authored-By` trailer, no "Generated with" line.
- Commit, push and tag one step at a time, checking each exit code before the next. Use repeated `-m` for commit messages. Check `git log -1` before pushing and name the commit when tagging.
- This repo uses the `DarzVadckawy` GitHub account: run `gh auth switch --user DarzVadckawy` before any `gh` call here and switch back to `dariomax` afterwards.
