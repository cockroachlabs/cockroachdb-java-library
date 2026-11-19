# Concurrent Ideas Board — pure-Java example

The pure-Java twin of the Spring and .NET fundamentals demos. It shows the
library's hero feature: **automatic retry on serialization conflicts (SQLSTATE
40001)**. Many threads upvote the same idea; without retry votes are lost, with
`Crdb.tx(...)` every vote lands.

Pinned to `cockroachdb-java-library` **0.1.2** (anonymous Maven Pages feed).

## What it shows

- `upvoteWithoutRetry(id)` — a plain SERIALIZABLE read-modify-write. Under
  contention CockroachDB raises 40001 and the lost conflicts never recover.
- `upvote(id)` — the same work wrapped in `Crdb.tx(ds, conn -> …)`, which runs
  SERIALIZABLE and replays the whole transaction on each 40001. All votes land.

## Prerequisites

- Java 25 (`sdk use java 25.0.2-graalce`) and Gradle 9.x.
- For `gradle run`: a reachable CockroachDB. Start one locally:
  ```
  cockroach start-single-node --insecure --listen-addr=localhost:26257
  ```
  Override the URL with `JDBC_URL` (default
  `jdbc:postgresql://localhost:26257/defaultdb?sslmode=disable&user=root`).
- For `gradle test`: Docker only — the test spins up CockroachDB via
  Testcontainers (`CockroachHarness`). No local cluster needed.

## Run

```bash
# Prove it under contention (Testcontainers; Docker must be up):
gradle test

# Watch lost votes vs. retried votes against a running cluster:
gradle run
```

`gradle run` prints both outcomes, e.g.:

```
WITHOUT retry (plain)    successes=41 failures=19 votes=41/60
WITH Crdb.tx retry       successes=60 failures=0  votes=60/60
```

## The proof

`IdeaServiceConcurrencyTest` runs 3 threads × 20 iterations (60 upvotes) on one
idea via the testing module's `ConcurrencyRunner`, and asserts:

- the retry path lands **exactly 60** votes with **zero** failures;
- the plain path does **not** reliably reach 60 (votes lost or failures raised).
