# Cockroach Continuum — Library Feature Plan (project copy)

This is the library-scoped roadmap for `cockroachdb-java-library`. The
workspace-wide master plan lives at `../docs/CONTINUUM_PLAN.md`; this copy keeps
the phases, module map, and facade shape that the code references.

## Naming & disclosure guardrails (must-follow)

Use **customer-facing names only**: CockroachDB, Cockroach Continuum, Cockroach
Plenum, Cockroach vCluster, Cockroach Aegis. Prefix the first mention of
Continuum/Plenum/Aegis/vCluster on a page with "Cockroach". No internal codenames
or private repositories anywhere.

## The three pillars (+ one surface)

| Pillar | What it is | How the library exposes it |
|--------|------------|----------------------------|
| **Cockroach Plenum** | Usage-based storage (cold storage & tiering over time). | Read-only `StorageInsights` — `Crdb.storage(ds).summary()`. |
| **Cockroach vCluster** | Virtual clusters: workload isolation & prioritization. | `@VirtualCluster` + `@Workload`, or `Crdb.vcluster("analytics").run(...)`. |
| **Cockroach Aegis** | Autonomous DB-ops agents producing findings/recommendations. | `Crdb.aegis(cfg).findings()` / `.emit(...)`, read-first. |
| **Agent / MCP surface** | Makes the DB actionable to agents. | `@AgentTool` + `Crdb.agent().serve(port)`. |

## Module map (Gradle multi-module)

`core` · `cdc` · `metrics` · `ai` · `continuum` (Plenum/vCluster/Aegis) ·
`agent` (MCP surface) · `testing`.

## Design tenets

- **Java 25 first:** `ScopedValue` (not `ThreadLocal`) for context, sealed types
  for anomalies, records for value types, pattern-matching `switch` for
  exhaustiveness.
- **Functional core, annotated shell:** `Crdb.tx(...)` always works without a
  proxy; annotations + `Crdb.enhance(...)` remove the boilerplate.
- **Least privilege for agent/Aegis surfaces;** destructive tools opt-in.

## Phased roadmap

| Phase | Scope | Exit criteria |
|-------|-------|---------------|
| **P0 — Foundations (done)** | `core`: annotations, proxy engine, adaptive retry, sealed anomalies, circuit breaker, tx template, ScopedValue context. Other modules stubbed. | `./gradlew build` green; retry/anomaly/circuit + live-cluster tests pass. |
| **P1 — Continuum read surfaces** | `continuum`: Plenum `StorageInsights` (read-only) + vCluster routing/priority. | One-annotation vCluster + `storage().summary()` with tests (simulated + live). |
| **P2 — Aegis integration** | `AegisClient` (findings/recommendations) + `AegisExhaust` behind a feature flag with a simulated backend; graceful degrade when unenrolled. | Compiles/runs enrolled or not; simulated findings test. |
| **P3 — Agent/MCP surface** | `@AgentTool` + MCP-style facade; allowlisted, typed, least-privilege. | Sample agent invokes health/insights tools locally. |
| **P4 — Parity & polish** | Bring `cdc`, `ai`, `metrics`, `testing` to sibling-library parity. | Feature-matrix parity across the CockroachDB libraries. |

## Cross-cutting concerns

- **Observability:** Continuum calls emit metrics/traces through the `metrics`
  module.
- **Security:** Aegis/agent surfaces are least-privilege; credentials handled by
  the caller's config, never logged.
