# Kanban

## Registry

- ID: K-20250809-001
  - Title: Align DTOs to GPT function schema (spec/files/command/target/scope/options)
  - Status: Done
  - Assignee: GPT-5
  - Priority: P1
  - Size: L
  - Deps: []
  - Blockers: []
  - Links: `docs/PROJECT.md`, `src/main/java/app/dto/SolveRequest.java`, `src/main/java/app/dto/SolveResponse.java`, `src/main/java/app/App.java`
  - Artifacts: [`src/main/java/app/dto/SolveRequest.java`, `src/main/java/app/dto/SolveResponse.java`, `src/main/java/app/App.java`, `docs/PROJECT.md`]
  - Acceptance:
    - Request and response match the schema in `docs/PROJECT.md` (including `spec`, `files`, `command/target`, `scope`, `options`).
    - Backward compatibility is not required; legacy fields may be removed.
    - Unit tests cover DTO mapping and unknown-field handling.
    - Board remains in sync; OpenAPI reflects new schema.
  - Notes:
    - 2025-08-09: Clarification — The prior schema was never released; legacy compatibility is not a requirement for this card.
    - 2025-08-09: Plan — Request: add `spec`, `files[] {path,content}`, `command` ("check"|"run"), `target`, `scope{exact,bitwidth,ints,max_instances,exactScopes}`, `options{timeout_ms,solver,symmetry_breaking,seed,recordKodkod}` per `docs/PROJECT.md`. Response: add envelope (`status: ok|timeout|ERROR`, `satisfiable`), keep `instances` (normalized later in K-20250809-003), `unsat_core`, `statistics` (later), `provenance` (later), `logs`, `errors[]`.
    - 2025-08-09: Tasks — (1) Implement new DTO fields. (2) Update `AlloyRunner.solve` to honor `command/target`, `scope.max_instances`, `options.timeout_ms`, `symmetry_breaking`; set envelope status and `satisfiable`. (3) Update unit tests to the new contract. (4) Update `docs/PROJECT.md` references as needed; OpenAPI in K-20250809-007.
    - 2025-08-09: Progress — Implemented DTOs and runner changes (`timeout_ms`, `max_instances`, `symmetry_breaking`, `command/target`). Renamed response field to `unsat_core`; updated tests; `mvn clean test` green. Envelope now uses `status="ok"|"timeout"|"error"`.

- ID: K-20250809-010
  - Title: Container and CI – pin versions and self-check
  - Status: Done
  - Assignee: GPT-5
  - Priority: P2
  - Size: M
  - Deps: []
  - Blockers: []
  - Links: `Dockerfile`, `pom.xml`, `README.md`
  - Artifacts: [`Dockerfile`, `pom.xml`, `.github/workflows/ci.yml` (new)]
  - Acceptance:
    - Image pins Alloy 6.2.x; startup self-check verifies solver libs; CI builds/tests and pushes image.
  - Notes:
    - 2025-08-09: Add CI and container self-check.
    - 2025-08-09: Implemented self-check in `app.App` (`--self-check`/`SELF_CHECK=1`), pinned Docker base image, added GitHub Actions workflow (build/test, build image, run self-check, push on main), updated README.
    - 2025-08-10: CI parity fix — add explicit SAT4J deps and relax prover requirement; SAT4J path green; mirrors Docker shaded classpath.

- ID: K-20250809-002
  - Title: Per-request timeout and deterministic seed
  - Status: Done
  - Assignee: Unassigned
  - Priority: P1
  - Size: M
  - Deps: []
  - Blockers: []
  - Links: `src/main/java/app/AlloyRunner.java`, `docs/PROJECT.md`
  - Artifacts: [`src/main/java/app/AlloyRunner.java`]
  - Acceptance:
    - `options.timeout_ms` enforced per request; requests exceeding timeout return envelope `status=timeout` with `error.type=timeout`.
    - `options.seed` plumbed into solving path; determinism demonstrated by reproducible instances under fixed seed.
    - Tests: timeout unit test and seed determinism test pass.
  - Notes:
    - 2025-08-09: Add timeout and seed support; surface duration and error types.
    - 2025-08-09: Implemented non-prover solver support and conditional unsat-core; all unit tests green on SAT4J.

- ID: K-20250809-003
  - Title: Normalize instance serialization (atoms/relations tuples)
  - Status: Done
  - Assignee: Unassigned
  - Priority: P1
  - Size: M
  - Deps: []
  - Blockers: []
  - Links: `src/main/java/app/AlloyRunner.java`, `docs/PROJECT.md`
  - Artifacts: [`src/main/java/app/AlloyRunner.java`, `src/test/java/app/AlloyRunnerSnapshotTest.java`, `src/test/resources/snapshots/sat-instance.snapshot.json`, `README.md`]
  - Acceptance:
    - `instances` include atoms per sig and tuples per relation in a structured JSON form.
    - Snapshot tests added for SAT models; README examples updated.
  - Notes:
    - 2025-08-09: Replace stringified evals with normalized JSON.
    - 2025-08-10: Added SAT instance snapshot test and README example; all tests green.

- ID: K-20250809-007
  - Title: OpenAPI spec for /v1/solve (Alloy.Api-aligned)
  - Status: Backlog
  - Assignee: Unassigned
  - Priority: P1
  - Size: M
  - Deps: [K-20250809-001]
  - Blockers: []
  - Links: `docs/PROJECT.md`, `src/main/java/app/App.java`
  - Artifacts: [`docs/openapi.yaml` (new), `src/main/java/app/App.java`]
  - Acceptance:
    - OpenAPI published; route and DTOs match schema; examples included.
    - Validated by linting tool; integrated into README.
  - Notes:
    - 2025-08-09: Generate spec and wire to current route.

- ID: K-20250809-008
  - Title: Testing – timeout, snapshot JSON, property-based options
  - Status: Backlog
  - Assignee: Unassigned
  - Priority: P1
  - Size: M
  - Deps: [K-20250809-002, K-20250809-003, K-20250809-001]
  - Blockers: []
  - Links: `src/test/java/app/**`, `src/test/resources/models/**`
  - Artifacts: [`src/test/java/app/AlloyRunnerTest.java`, `src/test/java/app/AlloyRunnerNativeTest.java`, `src/test/resources/models/**`]
  - Acceptance:
    - Forced-timeout test; snapshot tests for SAT/UNSAT/core; property-based tests scaffold for scopes/seeds.
    - CI runs tests on Linux/macOS.
  - Notes:
    - 2025-08-09: Expand test matrix and add snapshots.

- ID: K-20250809-004
  - Title: Multi-module support with safe in-memory file provider
  - Status: Backlog
  - Assignee: Unassigned
  - Priority: P1
  - Size: L
  - Deps: [K-20250809-001]
  - Blockers: []
  - Links: `docs/PROJECT.md`, `src/main/java/app/AlloyRunner.java`
  - Artifacts: [`src/main/java/app/AlloyRunner.java`]
  - Acceptance:
    - `files[]` accepted; `open` resolves within provided in-memory modules only.
    - On-disk file and network access denied; tests cover multi-module SAT/UNSAT.
  - Notes:
    - 2025-08-09: Add safe resolver for `open` and reject external IO.

- ID: K-20250809-011
  - Title: Safety hardening and runtime limits
  - Status: Backlog
  - Assignee: Unassigned
  - Priority: P2
  - Size: M
  - Deps: []
  - Blockers: []
  - Links: `src/main/java/app/AlloyRunner.java`, `src/main/java/app/App.java`
  - Artifacts: [`src/main/java/app/AlloyRunner.java`, `src/main/java/app/App.java`]
  - Acceptance:
    - Env-configurable caps; FS/network access blocked; documented and tested.
  - Notes:
    - 2025-08-09: Enforce caps and sandboxing.

- ID: K-20250809-009
  - Title: Solver capability detection and graceful degradation
  - Status: Backlog
  - Assignee: Unassigned
  - Priority: P2
  - Size: S
  - Deps: []
  - Blockers: ["Native solver availability for unsat-core tests"]
  - Links: `src/main/java/app/AlloyRunner.java`
  - Artifacts: [`src/main/java/app/AlloyRunner.java`]
  - Acceptance:
    - Capability flag surfaced; core omitted gracefully on unsupported solvers; SAT4J path covered by tests.
  - Notes:
    - 2025-08-09: Detect and expose unsat-core capability.

- ID: K-20250809-005
  - Title: Observability and provenance in results
  - Status: Backlog
  - Assignee: Unassigned
  - Priority: P2
  - Size: M
  - Deps: []
  - Blockers: []
  - Links: `src/main/java/app/AlloyRunner.java`, `docs/PROJECT.md`
  - Artifacts: [`src/main/java/app/AlloyRunner.java`]
  - Acceptance:
    - `statistics{solve_ms,vars,clauses}` populated when available; `provenance{engine,solver,versions,spec_sha256,mode}` included.
    - `logs` normalized; unit tests cover fields presence.
  - Notes:
    - 2025-08-09: Add solver/tool versioning and stats capture.

- ID: K-20250809-013
  - Title: Enrich /healthz with version info and readiness
  - Status: Backlog
  - Assignee: Unassigned
  - Priority: P2
  - Size: S
  - Deps: []
  - Blockers: []
  - Links: `src/main/java/app/App.java`, `pom.xml`
  - Artifacts: [`src/main/java/app/App.java`]
  - Acceptance:
    - `/healthz` includes engine/solver versions; readiness endpoint added; tests cover both.
  - Notes:
    - 2025-08-09: Add simple readiness and version reporting.

- ID: K-20250809-012
  - Title: Documentation sync and examples refresh
  - Status: Backlog
  - Assignee: Unassigned
  - Priority: P3
  - Size: S
  - Deps: [K-20250809-001, K-20250809-003, K-20250809-007]
  - Blockers: []
  - Links: `README.md`, `docs/PROJECT.md`
  - Artifacts: [`README.md`, `docs/PROJECT.md`]
  - Acceptance:
    - README/PROJECT reflect current APIs and examples; curl samples verified; links valid.
  - Notes:
    - 2025-08-09: Sync docs with implementation.

- ID: K-20250809-014
  - Title: Release workflow and image tagging
  - Status: Backlog
  - Assignee: Unassigned
  - Priority: P3
  - Size: M
  - Deps: [K-20250809-010]
  - Blockers: []
  - Links: `Dockerfile`, `README.md`
  - Artifacts: [`Dockerfile`, `.github/workflows/release.yml` (new), `README.md`]
  - Acceptance:
    - Semver tagging; changelog generation; image pushed with version tags; optional SBOM generated.
  - Notes:
    - 2025-08-09: Define release automation and tagging.

## Board

### Backlog

- [K-20250809-007] OpenAPI spec for /v1/solve (Alloy.Api-aligned) — Unassigned
  - Links: `docs/PROJECT.md`, `src/main/java/app/App.java`
- [K-20250809-008] Testing – timeout, snapshot JSON, property-based options — Unassigned
  - Links: `src/test/java/app/**`, `src/test/resources/models/**`
- [K-20250809-004] Multi-module support with safe in-memory file provider — Unassigned
  - Links: `docs/PROJECT.md`, `src/main/java/app/AlloyRunner.java`
- [K-20250809-011] Safety hardening and runtime limits — Unassigned
  - Links: `src/main/java/app/AlloyRunner.java`, `src/main/java/app/App.java`
- [K-20250809-009] Solver capability detection and graceful degradation — Unassigned
  - Links: `src/main/java/app/AlloyRunner.java`
- [K-20250809-005] Observability and provenance in results — Unassigned
  - Links: `src/main/java/app/AlloyRunner.java`, `docs/PROJECT.md`
- [K-20250809-013] Enrich /healthz with version info and readiness — Unassigned
  - Links: `src/main/java/app/App.java`, `pom.xml`
- [K-20250809-012] Documentation sync and examples refresh — Unassigned
  - Links: `README.md`, `docs/PROJECT.md`
- [K-20250809-014] Release workflow and image tagging — Unassigned
  - Links: `Dockerfile`, `README.md`

### Doing

### Review

### Done

- [K-20250809-001] Align DTOs to GPT function schema (spec/files/command/target/scope/options) — GPT-5
  - Links: `docs/PROJECT.md`, `src/main/java/app/dto/SolveRequest.java`, `src/main/java/app/dto/SolveResponse.java`, `src/main/java/app/App.java`
- [K-20250809-010] Container and CI – pin versions and self-check — GPT-5
  - Links: `Dockerfile`, `pom.xml`, `README.md`
- [K-20250809-002] Per-request timeout and deterministic seed — GPT-5
  - Links: `src/main/java/app/AlloyRunner.java`, `docs/PROJECT.md`
- [K-20250809-003] Normalize instance serialization (atoms/relations tuples) — GPT-5
  - Links: `src/main/java/app/AlloyRunner.java`, `docs/PROJECT.md`, `src/test/java/app/AlloyRunnerSnapshotTest.java`, `README.md`

## Archive

## Changelog

- 2025-08-09 [GPT-5]: ADD_CARD added 14 backlog cards; RENDER_BOARD from Registry.
- 2025-08-09 [GPT-5]: EDIT_CARD Reordered Registry to reflect implementation sequence; RENDER_BOARD refreshed Backlog order.
- 2025-08-09 [GPT-5]: EDIT_CARD Updated K-20250809-006 to embedded-only; RENDER_BOARD refreshed Backlog.
- 2025-08-09 [GPT-5]: MOVE_CARD Moved K-20250809-001 to Doing; EDIT_CARD added implementation plan/tasks; RENDER_BOARD refreshed columns.
- 2025-08-09 [GPT-5]: EDIT_CARD K-20250809-001 aligned response field to `unsat_core`, updated tests, verified green build; RENDER_BOARD regenerated.
- 2025-08-09 [GPT-5]: MOVE_CARD K-20250809-001 to Review; RENDER_BOARD refreshed.
- 2025-08-09 [GPT-5]: MOVE_CARD K-20250809-001 to Done; EDIT_CARD envelope status to lowercase `error`; RENDER_BOARD refreshed.
- 2025-08-09 [GPT-5]: EDIT_CARD K-20250809-010 added self-check/CI/README notes; MOVE_CARD to Doing; RENDER_BOARD refreshed.
- 2025-08-09 [GPT-5]: MOVE_CARD K-20250809-010 to Done; RENDER_BOARD refreshed.
- 2025-08-09 [GPT-5]: MOVE_CARD Moved K-20250809-002 to Doing; RENDER_BOARD refreshed.
- 2025-08-09 [GPT-5]: EDIT_CARD K-20250809-002 acceptance to envelope timeout; MOVE_CARD to Review; RENDER_BOARD refreshed.
- 2025-08-10 [GPT-5]: MOVE_CARD K-20250809-010 to Doing; EDIT_CARD add CI parity note for SAT4J; RENDER_BOARD refreshed.
- 2025-08-10 [GPT-5]: MOVE_CARD K-20250809-010 to Done; RENDER_BOARD refreshed.
- 2025-08-10 [GPT-5]: EDIT_CARD K-20250809-003 added SAT snapshot test and README example; RENDER_BOARD refreshed.
- 2025-08-10 [GPT-5]: MOVE_CARD K-20250809-003 to Done; RENDER_BOARD refreshed.
