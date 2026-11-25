# AGENTS.md — cockroachdb-java-library (consumer guide)

Guidance for coding agents writing an application that depends on
`cockroachdb-java-library`. This is the framework-free Java client for
CockroachDB. For full detail see [`README.md`](../../README.md); every symbol
below is real — do not invent APIs.

## Already handled — do NOT rebuild it

- **Serialization retry (SQLSTATE `40001`) is automatic.** Any work run through
  `Crdb.tx(ds, c -> ...)`, `Crdb.template(ds)`, or a `@Transactional(retry = true)`
  method retries serialization conflicts for you using `cockroach_restart`
  semantics. Do **not** write your own `try/catch` + retry loop.
- **Multi-region locality check** is provided: `Crdb.verifyLocality(ds, Entity.class, ...)`
  (startup check for `@RegionalByRow` / `@GlobalTable` entities). Don't re-implement it.
- **CDC plumbing** (changefeed webhook decode/dispatch) ships in the `cdc` module.
- **Metrics / Continuum** (vCluster tagging, Plenum storage, Aegis, Agent/MCP) ship
  in `metrics` / `continuum` / `agent`. Don't hand-roll these.

## Which symbol for each job

| Job | Use |
|-----|-----|
| Retrying SERIALIZABLE transaction | `Crdb.tx(ds, c -> ...)` or `@Transactional(retry = true)` |
| Transparent retry on existing beans | `Crdb.enhance(obj)` (JDK proxy on interface, ByteBuddy subclass on POJO) |
| Follower read (stale, nearest replica) | `@FollowerRead` (opt. `hoursAgo`/`minutesAgo`/`secondsAgo`) — must run inside a transaction |
| vCluster / workload QoS | `@VirtualCluster("name")` + `@Workload(Priority.LOW)` or `Crdb.vcluster("name").workload(...).tx(...)` |
| Regional-by-row homing | annotate the entity `@RegionalByRow`, mark the region-column field `@CrdbRegion`; home a row by **writing its `crdb_region` column** |
| Global (reference) table | `@GlobalTable` |
| CDC | `CdcEventDecoder`, `CdcEventHandler`, `ChangefeedHandlerRegistry`, `ChangefeedWebhookHandler` |
| Storage insights / Agent-MCP | `Crdb.storage(ds)`, `CrdbAgent.from(...)`, `ContinuumMcpFactory.create(...)` |

## Config that matters

- Connection: `CRDB_URL` / a standard `DataSource` (PostgreSQL wire driver).
- Retry tuning: `@CrdbRetry(maxRetries, baseDelayMillis, maxDelayMillis)`;
  `@CircuitBreaker(failureThreshold, resetTimeoutMillis)`.
- Follower-read staleness: `@FollowerRead` args (default `follower_read_timestamp()`).

## Anti-patterns (don't)

- Hand-rolled `40001` retry loops — the wrapper already does it.
- `SET crdb_region = ...` — **there is no such session variable.** Home rows by
  writing the `crdb_region` **column** on a `@RegionalByRow` entity.
- Writing changefeed webhook parsing by hand — use the `cdc` module.
- Running at a weaker isolation than SERIALIZABLE and expecting retry: `40001`
  won't fire, so the retry wrapper silently no-ops. `Crdb.tx`/`@Transactional`
  default to SERIALIZABLE — keep it.
- Opening a `ResultSet` and then committing in the same transaction before you
  finish reading it.

## Testing

Use the `testing` module: `CockroachHarness` (Testcontainers CockroachDB) +
`ConcurrencyRunner` / `SerializationConflicts` to drive real contention and prove
your retry paths work.
