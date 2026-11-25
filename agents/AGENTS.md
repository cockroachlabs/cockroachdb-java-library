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

## Learn CockroachDB fundamentals

The library handles the Java side; these CockroachDB docs explain the database underneath. The links below go to the Markdown version of each page, which is easier for an agent to read; drop the `.md` for the HTML page. Every docs page has one, and <https://docs.cockroachlabs.com/llms.txt> indexes the whole documentation site.

**Start here**

- [Developer Basics](https://docs.cockroachlabs.com/docs/stable/developer-basics.md): what's different about building on CockroachDB.
- [PostgreSQL Compatibility](https://docs.cockroachlabs.com/docs/stable/postgresql-compatibility.md): CockroachDB speaks the PostgreSQL wire protocol (use the PostgreSQL JDBC driver), with some differences.
- [Connection Pooling](https://docs.cockroachlabs.com/docs/stable/connection-pooling.md): sizing a `DataSource` / HikariCP pool.
- [Common Errors and Solutions](https://docs.cockroachlabs.com/docs/stable/common-errors.md)

**Transactions and contention**

- [Transactions](https://docs.cockroachlabs.com/docs/stable/transactions.md): SERIALIZABLE by default, and why clients see retry errors.
- [Transaction Retry Error Reference](https://docs.cockroachlabs.com/docs/stable/transaction-retry-error-reference.md): what each `40001` (`RETRY_SERIALIZABLE`, `RETRY_WRITE_TOO_OLD`, ...) means and how to make it rarer. `Crdb.tx` / `@Transactional(retry = true)` retries them; this tells you why they happen.
- [Reduce transaction contention](https://docs.cockroachlabs.com/docs/stable/performance-best-practices-overview.md#reduce-transaction-contention): the checklist. Keep transactions small, limit the rows they touch, use `SELECT ... FOR UPDATE` for read-then-write, `UPSERT` whole rows, use historical reads where staleness is fine.
- [Understand Hotspots](https://docs.cockroachlabs.com/docs/stable/understand-hotspots.md): ranges that take far more traffic than others, and how to spread it.
- [Performance Tuning Recipes](https://docs.cockroachlabs.com/docs/stable/performance-recipes.md): symptoms and fixes, contention included.
- [Monitor and Analyze Transaction Contention](https://docs.cockroachlabs.com/docs/stable/monitor-and-analyze-transaction-contention.md)
- [`SELECT FOR UPDATE`](https://docs.cockroachlabs.com/docs/stable/select-for-update.md)
- [Read Committed Transactions](https://docs.cockroachlabs.com/docs/stable/read-committed.md): an alternative isolation level, with trade-offs. Don't switch to it just to avoid retries.

**Schema design**

- [Database Schemas](https://docs.cockroachlabs.com/docs/stable/schema-design-overview.md): how to design and change schemas, including why to change them with a migration tool rather than through the ORM.
- [Unique ID best practices](https://docs.cockroachlabs.com/docs/stable/performance-best-practices-overview.md#unique-id-best-practices): prefer `UUID` keys (`gen_random_uuid()`) over sequences or auto-increment, which create hotspots.
- [`UUID`](https://docs.cockroachlabs.com/docs/stable/uuid.md) and [Hash-sharded Indexes](https://docs.cockroachlabs.com/docs/stable/hash-sharded-indexes.md), for when an index must be on a sequential column.
- [Online Schema Changes](https://docs.cockroachlabs.com/docs/stable/online-schema-changes.md): schema changes run in the background; what that means for migrations.

**Multi-region**

- [Multi-Region Capabilities Overview](https://docs.cockroachlabs.com/docs/stable/multiregion-overview.md)
- [Table Localities](https://docs.cockroachlabs.com/docs/stable/table-localities.md): `REGIONAL BY TABLE`, `REGIONAL BY ROW` and `GLOBAL`, which `@RegionalByRow` and `@GlobalTable` map to.
- [How to Choose a Multi-Region Configuration](https://docs.cockroachlabs.com/docs/stable/choosing-a-multi-region-configuration.md)
- [Follower Reads](https://docs.cockroachlabs.com/docs/stable/follower-reads.md) and [`AS OF SYSTEM TIME`](https://docs.cockroachlabs.com/docs/stable/as-of-system-time.md), behind `@FollowerRead`.

**Change data capture**

- [`CREATE CHANGEFEED`](https://docs.cockroachlabs.com/docs/stable/create-changefeed.md) and [Changefeed Sinks](https://docs.cockroachlabs.com/docs/stable/changefeed-sinks.md) (the webhook sink is what the `cdc` module receives).

**Vector search**

- [`VECTOR`](https://docs.cockroachlabs.com/docs/stable/vector.md) and [Vector Indexes](https://docs.cockroachlabs.com/docs/stable/vector-indexes.md)
