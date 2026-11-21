# Changelog

All notable changes to this project are documented here. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres
to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.0] - 2026-11-21

Checked exceptions thrown by a method annotated with `@FollowerRead`,
`@VirtualCluster`, `@Region`, `@Workload`, `@CrdbRetry` or
`@Transactional(retry = true)` now reach the caller as the **original instance**,
honoring the method's `throws` contract.

### Fixed
- Annotated methods no longer wrap a thrown checked exception in a generic
  `RuntimeException`. A method declared `throws IOException` now throws
  `IOException` to its caller. Previously the original exception was wrapped and
  its type was lost. (`CrdbContext.with`, `RetryEngine.run`, and the interface
  proxy no longer re-wrap.)

### Changed (behavior)
- Code that caught the wrapping `RuntimeException` around an annotated call now
  sees the original checked exception instead. `RuntimeException` and `Error`
  continue to propagate unchanged.

## [0.1.2] - 2026-10-28

`@FollowerRead` can now target a specific staleness, and rejects invalid values
instead of silently reading current data.

### Added
- `hoursAgo`, `minutesAgo` and `secondsAgo` attributes on `@FollowerRead`. They
  add up and compose to `AS OF SYSTEM TIME '-Ns'`. With nothing set, the read
  still uses `follower_read_timestamp()`.

### Changed
- `CrdbDispatcher` resolves the staleness expression through a new
  `followerReadStaleness(...)` helper. When any of `hoursAgo`/`minutesAgo`/
  `secondsAgo` is set, the composed interval takes precedence over the default
  `staleness` string; the existing `staleness` attribute is retained and behaves
  exactly as before when no parts are set.

### Fixed
- A negative `hoursAgo`/`minutesAgo`/`secondsAgo` now throws
  `IllegalStateException` when the annotation is processed, rather than producing
  an invalid clause.

### Known limitation
- The core `@FollowerRead` only emits `AS OF SYSTEM TIME` when the annotated
  method runs within a transaction (`TransactionTemplate`). Opening a dedicated
  read-only transaction for a bare `@FollowerRead` is handled by the Spring Boot
  starter, not the core library.

[0.1.2]: https://github.com/cockroachlabs/cockroachdb-java-library/releases/tag/v0.1.2
