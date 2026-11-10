# Architecture Decision Records

Decisions that only concern the JVM SDK: its modules, its public API and how it builds on core's contracts. Each ADR records one decision, why it was made, what was ruled out and what it leaves for later.

Decisions that change something shared with core, such as the task-runtime protocol, the JVM harness, the task bundle format or the IR, belong in `docs/adr/` in [Tnsor-Labs/brokoli](https://github.com/Tnsor-Labs/brokoli/tree/main/docs/adr). This SDK's parent design is core's [ADR-036](https://github.com/Tnsor-Labs/brokoli/blob/main/docs/adr/036-jvm-task-bundles-and-sdk.md).

The format and the rules are core's, see its [ADR README](https://github.com/Tnsor-Labs/brokoli/blob/main/docs/adr/README.md). Each file follows [`_template.md`](./_template.md): status, context, decision, consequences, alternatives considered and follow-ups. Numbering follows core's ADRs with a `JVM-` prefix (`JVM-ADR-001`), and a new decision gets the next number. A superseded one is marked in place with a link to the ADR that replaces it, never rewritten. When the code changes what an ADR describes, the ADR is updated in the same PR, with a dated `## Update (YYYY-MM-DD)` section when something turned out differently.

## Index

| # | Title | Status |
|---|---|---|
| [JVM-ADR-001](./001-task-api.md) | Task API: `@Task`, `@Default`, `Row` and `Rows` | Accepted |
| [JVM-ADR-002](./002-task-processor.md) | The task processor and its task file | Accepted |
| [JVM-ADR-003](./003-generated-entry-method.md) | The generated entry method | Accepted |
