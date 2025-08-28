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
  - Status: Done
  - Assignee: Unassigned
  - Priority: P1
  - Size: M
  - Deps: [K-20250809-001]
  - Blockers: []
  - Links: `docs/PROJECT.md`, `src/main/java/app/App.java`
  - Artifacts: [`docs/openapi.yaml` (new), `src/main/java/app/App.java`]
  - Acceptance:
    - OpenAPI 3.1 published; route and DTOs match schema; examples included.
    - Nullables use JSON Schema 2020-12 (`type: [T, "null"]`), not `nullable:`.
    - HTTP semantics: 200 for `ok` and `timeout`; 4xx/5xx only for transport/server faults.
    - Verified by linting tool; integrated into README. Unit tests cover SAT/UNSAT/timeout/bad_request examples.
    - OpenAI orchestration (grammar/tool wiring) is out of repo scope.
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

- ID: K-20250815-015
  - Title: Azure pre-release (staging) with IP allowlist and caps
  - Status: Doing
  - Assignee: Unassigned
  - Priority: P1
  - Size: M
  - Deps: [K-20250809-010, K-20250809-007]
  - Blockers: ["Azure subscription access", "GHCR read token/PAT"]
  - Links: `Dockerfile`, `.github/workflows/ci.yml`, `README.md`, `docs/openapi.yaml`
  - Artifacts: []
  - Acceptance:
    - Container App deployed in Azure (staging) using immutable image tag (`:sha-<commit>`), reachable at a stable URL.
    - Ingress restricted to allowlisted IPs via APIM (or equivalent) in front of the app; direct public access blocked.
    - Runtime caps enforced: `MAX_MODEL_CHARS=200k`, `DEFAULT_TIMEOUT_MS=20s` (or `options.timeout_ms`), `MAX_INSTANCES=5`; container sized at ~0.5 CPU / 1Gi; autoscale min=0, max=2.
    - `--self-check` passes in Azure container run; `/healthz` returns OK; SAT and UNSAT smoke calls succeed.
    - Logs flow to Log Analytics; basic operator runbook captured (deploy, smoke, rollback using revisions).
  - Notes:
    - Use APIM inbound `ip-filter` allowlist and optional basic concurrency limit.
    - Deploy with Container Apps revisions; validate at 0% traffic before promotion; rollback by re-pointing traffic to prior revision.
  - Tasks —
    1) Provision RG, Log Analytics, and Container Apps environment.
    2) Push/pull GHCR image; deploy Container App `staging` with `--target-port 8080`, resource limits, autoscale 0..2.
    3) Front with APIM: add IP allowlist policy and route to the Container App ingress.
    4) Run smoke tests: `/healthz`, SAT run, UNSAT check; run one-off `--self-check` job in Azure.
    5) Wire logs to Log Analytics; set simple alerts (restart count/5xx) if feasible.
    6) Document endpoint URL and access policy in `README.md`; note gates (no multi-file `open`, keep caps).

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

- ID: K-20250828-016
  - Title: Error reporting – include source range and types
  - Status: Done
  - Assignee: GPT-5
  - Priority: P1
  - Size: S
  - Deps: []
  - Blockers: []
  - Links: `src/main/java/app/AlloyRunner.java`, `src/main/java/app/dto/SolveResponse.java`
  - Artifacts: [`src/main/java/app/AlloyRunner.java`, `src/main/java/app/dto/SolveResponse.java`]
  - Acceptance:
    - Parse/type errors mapped to `parse_error`/`type_error`.
    - `error.range{file,startLine,startCol,endLine,endCol}` present when available.
    - Unit tests pass on SAT4J path.
  - Notes:
    - 2025-08-28: Catch Alloy `Err`, map to types, attach `Pos` as `range` in response; updated DTO.

- ID: K-20250828-017
  - Title: Epic — Execute all commands when selection omitted
  - Status: Doing
  - Assignee: GPT-5
  - Priority: P2
  - Size: M
  - Deps: [K-20250809-007, K-20250809-003]
  - Blockers: []
  - Links: `src/main/java/app/AlloyRunner.java`, `docs/openapi.yaml`, `src/main/java/app/dto/SolveResponse.java`, `docs/USER_REFERENCE.md`
  - Artifacts: []
  - Acceptance:
    - High-level: When selection is omitted, execute multiple commands and aggregate results (`results[]`). Single-command fields MUST be null at top-level in this mode.
    - Success criteria: detailed semantics are implemented in child cards K-20250828-020 (DTO), K-20250828-021 (runner behavior and gating), K-20250828-022 (docs/OpenAPI), and K-20250828-023 (default flip); tests/docs green.
    - Behavior is off by default initially; gated via option or explicit mode to maintain compatibility.
  - Children: [K-20250828-018, K-20250828-019, K-20250828-020, K-20250828-021, K-20250828-022, K-20250828-023, K-20250828-024]

- ID: K-20250828-018
  - Title: Runner refactor: extract instance serialization
  - Status: Backlog
  - Assignee: GPT-5
  - Priority: P1
  - Size: S
  - Deps: []
  - Blockers: []
  - Links: `src/main/java/app/AlloyRunner.java`
  - Artifacts: [`src/main/java/app/core/InstanceSerializer.java` (new), `src/main/java/app/AlloyRunner.java`]
  - Acceptance:
    - `serializeInstance(...)`, `normalizeLabel(...)`, `isBuiltinSigLabel(...)` moved to `app.core.InstanceSerializer`.
    - `AlloyRunner` delegates to `InstanceSerializer`; no behavior change; all tests green.

- ID: K-20250828-019
  - Title: Runner refactor: extract per-command executor
  - Status: Backlog
  - Assignee: GPT-5
  - Priority: P1
  - Size: M
  - Deps: [K-20250828-018]
  - Blockers: []
  - Links: `src/main/java/app/AlloyRunner.java`
  - Artifacts: [`src/main/java/app/core/CommandExecutor.java` (new), `src/main/java/app/AlloyRunner.java`]
  - Acceptance:
    - Create `app.core.CommandExecutor` with `execute(world, cmd, options, caps) -> CommandResult` (internal type).
    - `AlloyRunner` keeps validation, timeout, solver selection, error mapping; delegates command execution.
    - No behavior change; existing tests green.

- ID: K-20250828-020
  - Title: DTO scaffolding for multi-command results
  - Status: Backlog
  - Assignee: GPT-5
  - Priority: P2
  - Size: S
  - Deps: [K-20250828-019]
  - Blockers: []
  - Links: `src/main/java/app/dto/SolveResponse.java`
  - Artifacts: [`src/main/java/app/dto/SolveResponse.java`]
  - Acceptance:
    - Add `SolveResponse.CommandResult` with fields mirroring the single-command shape: `command`, `satisfiable`, `instances`, `unsat_core`, `warnings`, `debug`, `error`; and add top-level `List<CommandResult> results`.
    - Include `CommandResult.debug` to carry per-result solver info; top-level `debug` remains request-level.
    - Shape invariant: when `results` is present, top-level `satisfiable`, `instances`, `unsat_core`, `command`, and `error` MUST be null. No population yet; no behavior change.
    - Compilation and tests remain green.

- ID: K-20250828-021
  - Title: Feature: execute all when unspecified (gated, default OFF)
  - Status: Backlog
  - Assignee: GPT-5
  - Priority: P1
  - Size: M
  - Deps: [K-20250828-019, K-20250828-020]
  - Blockers: []
  - Links: `src/main/java/app/AlloyRunner.java`, `src/main/java/app/core/CommandExecutor.java`, `src/main/java/app/dto/SolveRequest.java`, `src/test/java/app/**`
  - Artifacts: []
  - Acceptance:
    - Add `options.execute_all_when_unspecified` (Boolean) to request DTO.
    - When gate true and no `target`/`commandIndex`, execute multiple commands: if `command` is provided, execute commands of that kind; otherwise execute all; preserve source order in `results[]`.
    - Enforce a shared internal deadline derived from `options.timeout_ms`; on deadline, return partial `results[]` for completed sub-commands only and set top-level `status=timeout`.
    - Define completed sub-command: SAT/UNSAT reached and either instance enumeration finished (up to per-result cap) or UNSAT-core extraction attempt completed. Sub-commands in progress at timeout are omitted.
    - Apply `scope.max_instances` per sub-command, subject to service hard cap. Single-command fields are null in multi-command mode.
    - Execution errors post-parse/typecheck are captured in `results[i].error`; continue with subsequent commands. Global parse/type errors remain top-level and abort multi-command mode.
    - Existing tests unchanged and green; add tests covering SAT/UNSAT mix, run/check filtering, ordering, sub-command error continuation, and partial results under timeout.

- ID: K-20250828-022
  - Title: Docs and OpenAPI for multi-command mode
  - Status: Backlog
  - Assignee: GPT-5
  - Priority: P2
  - Size: S
  - Deps: [K-20250828-021]
  - Blockers: []
  - Links: `docs/openapi.yaml`, `docs/USER_REFERENCE.md`
  - Artifacts: [`docs/openapi.yaml`, `docs/USER_REFERENCE.md`]
  - Acceptance:
    - Document `options.execute_all_when_unspecified`, dual response shapes, and aggregation semantics.
    - Use presence of `results` to indicate multi-command shape; document that top-level `satisfiable`, `instances`, `unsat_core`, `command`, and `error` are null in this mode.
    - Clarify `scope.max_instances` applies per result; explain top-level vs per-result warnings and debug placement.
    - Add examples for: (a) single-command; (b) mixed SAT/UNSAT multi-command; (c) run-only filtered multi-command; (d) partial-timeout with completed-only `results`; (e) empty `results` with "no commands found" warning.
    - Lint OpenAPI; examples match current gated behavior (default OFF).

- ID: K-20250828-023
  - Title: Flip default ON for execute-all and align tests
  - Status: Backlog
  - Assignee: GPT-5
  - Priority: P2
  - Size: S
  - Deps: [K-20250828-021, K-20250828-022]
  - Blockers: []
  - Links: `src/main/java/app/AlloyRunner.java`, `src/main/java/app/dto/SolveRequest.java`, `src/test/java/app/**`
  - Artifacts: []
  - Acceptance:
    - Default `execute_all_when_unspecified` to true when absent; confirm behavior with and without explicit selection to ensure backward compatibility.
    - Update tests to either select a command or assert `results[]` (and shape invariants) as appropriate.
    - Docs updated to reflect default behavior and how to opt out (specify a selection or set the gate false).
    - All tests green.

- ID: K-20250828-024
  - Title: Cleanup: remove migrated helpers and tighten boundaries
  - Status: Backlog
  - Assignee: GPT-5
  - Priority: P3
  - Size: S
  - Deps: [K-20250828-019]
  - Blockers: []
  - Links: `src/main/java/app/AlloyRunner.java`
  - Artifacts: [`src/main/java/app/AlloyRunner.java`]
  - Acceptance:
    - Remove redundant private helpers superseded by `InstanceSerializer`/`CommandExecutor`.
    - Ensure `AlloyRunner.solve(...)` remains the only outer boundary for validation/timeout/error mapping.
    - Tests green.

## Board

### Backlog

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
- [K-20250809-008] Testing – timeout, snapshot JSON, property-based options — Unassigned
  - Links: `src/test/java/app/**`, `src/test/resources/models/**`
- [K-20250828-018] Runner refactor: extract instance serialization — GPT-5
  - Links: `src/main/java/app/AlloyRunner.java`
- [K-20250828-019] Runner refactor: extract per-command executor — GPT-5
  - Links: `src/main/java/app/AlloyRunner.java`
- [K-20250828-020] DTO scaffolding for multi-command results — GPT-5
  - Links: `src/main/java/app/dto/SolveResponse.java`
- [K-20250828-021] Feature: execute all when unspecified (gated, default OFF) — GPT-5
  - Links: `src/main/java/app/AlloyRunner.java`, `src/main/java/app/dto/SolveRequest.java`
- [K-20250828-022] Docs and OpenAPI for multi-command mode — GPT-5
  - Links: `docs/openapi.yaml`, `docs/USER_REFERENCE.md`
- [K-20250828-023] Flip default ON for execute-all and align tests — GPT-5
  - Links: `src/main/java/app/AlloyRunner.java`, `src/test/java/app/**`
- [K-20250828-024] Cleanup: remove migrated helpers and tighten boundaries — GPT-5
  - Links: `src/main/java/app/AlloyRunner.java`

### Doing

- [K-20250815-015] Azure pre-release (staging) with IP allowlist and caps — Unassigned
  - Links: `Dockerfile`, `.github/workflows/ci.yml`, `README.md`, `docs/openapi.yaml`

- [K-20250828-017] Execute all commands when selection omitted — GPT-5
  - Links: `src/main/java/app/AlloyRunner.java`, `docs/openapi.yaml`, `src/main/java/app/dto/SolveResponse.java`, `docs/USER_REFERENCE.md`

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
- [K-20250809-007] OpenAPI spec for /v1/solve (Alloy.Api-aligned) — GPT-5
  - Links: `docs/PROJECT.md`, `src/main/java/app/App.java`
- [K-20250828-016] Error reporting – include source range and types — GPT-5
  - Links: `src/main/java/app/AlloyRunner.java`, `src/main/java/app/dto/SolveResponse.java`

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
- 2025-08-14 [GPT-5]: EDIT_CARD K-20250809-007 acceptance strengthened (OAS 3.1, JSON Schema nullables via oneOf, HTTP 200 semantics for ok/timeout, orchestration out-of-repo); RENDER_BOARD refreshed.
- 2025-08-14 [GPT-5]: MOVE_CARD K-20250809-007 to Review; RENDER_BOARD refreshed.
- 2025-08-14 [GPT-5]: MOVE_CARD K-20250809-007 to Done; RENDER_BOARD refreshed.
- 2025-08-15 [GPT-5]: MOVE_CARD K-20250809-008 to Doing; RENDER_BOARD refreshed.
- 2025-08-15 [GPT-5]: ADD_CARD K-20250815-015 Azure pre-release (staging) with IP allowlist and caps; RENDER_BOARD refreshed.
- 2025-08-28 [GPT-5]: ADD_CARD K-20250828-016 Error reporting – include source range and types; RENDER_BOARD refreshed.
- 2025-08-28 [GPT-5]: MOVE_CARD K-20250828-016 to Done; RENDER_BOARD refreshed.
- 2025-08-28 [GPT-5]: ADD_CARD K-20250828-017 Execute all commands when selection omitted; RENDER_BOARD refreshed.
- 2025-08-28 [GPT-5]: EDIT_CARD K-20250828-017 added plan/tasks/tests; MOVE_CARD to Doing; RENDER_BOARD refreshed.
- 2025-08-28 [GPT-5]: EDIT_CARD K-20250828-017 converted to Epic; RENDER_BOARD refreshed.
- 2025-08-28 [GPT-5]: ADD_CARD K-20250828-018 Runner refactor: extract instance serialization; RENDER_BOARD refreshed.
- 2025-08-28 [GPT-5]: ADD_CARD K-20250828-019 Runner refactor: extract per-command executor; RENDER_BOARD refreshed.
- 2025-08-28 [GPT-5]: ADD_CARD K-20250828-020 DTO scaffolding for multi-command results; RENDER_BOARD refreshed.
- 2025-08-28 [GPT-5]: ADD_CARD K-20250828-021 Feature: execute all when unspecified (gated, default OFF); RENDER_BOARD refreshed.
- 2025-08-28 [GPT-5]: ADD_CARD K-20250828-022 Docs and OpenAPI for multi-command mode; RENDER_BOARD refreshed.
- 2025-08-28 [GPT-5]: ADD_CARD K-20250828-023 Flip default ON for execute-all and align tests; RENDER_BOARD refreshed.
- 2025-08-28 [GPT-5]: ADD_CARD K-20250828-024 Cleanup: remove migrated helpers and tighten boundaries; RENDER_BOARD refreshed.
