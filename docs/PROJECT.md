
# Project Statement — GPT Function to Execute Alloy 6 Specifications

**Purpose.** Deliver a small, local‑first service module that exposes a single GPT‑compatible function to execute Alloy specifications and return structured results. The module embeds Alloy 6 via its Java APIs for execution and mirrors the successful REST patterns from Alloy.Api for optional remote operation. Alloy 6 is the current toolchain (latest 6.2.0 as of Jan 9, 2025); the Analyzer can surface **unsat cores** when using a compatible solver (e.g., *“Minisat with Unsat Core”*). ([Alloy Tools][1], [Maven Repository][2], [Alloy Docs][3])

**Leverage-first assumptions.**

* **Engine:** org.alloytools.alloy (Alloy 6) embedded JVM runner only (no CLI in this repo). Requires a modern JVM (Java **17+**). ([GitHub][4])
* **Interface pattern (remote mode):** Use **cmu‑sei/Alloy.Api** as the reference for REST shape (routes/DTOs, Swagger). ([GitHub][5])
* **Temporal extensions:** Acknowledge Alloy 6’s temporal capabilities and the **Pardinus/Electrum** ecosystem, but keep them out of v1 scope (plan a spike later if needed). ([Alloy Tools][1], [Nuno Macedo][6])

---

## Outcomes (end of discovery)

* **Decision log** (embedded JVM selected; CLI moved to separate project; local‑only vs optional remote worker noted).
* **Pre‑PoC**: run one tiny Alloy command headlessly and return normalized JSON.
* **v1 requirements & API schema** (function signature + result envelope).
* **Portability plan** (local → container → optional remote worker).
* **Backlog & timeline** to reach a shippable v1.

---

## Scope

**In:** Executing provided Alloy specs/commands; returning SAT/UNSAT/instances/unsat‑core (when solver supports it); basic options (scope, solver, timeout); a single function compatible with GPT function‑call grammar.
**Out (for v1):** Multi‑tenant scheduling, persistent datasets, UI/visualization beyond simple exports, temporal analysis beyond what’s built‑in and straightforward, multi‑solver benchmarking, CLI subprocess runner (moved to separate project).

---

## Immediate Objectives & Work Plan

### 1) Research & Scoping for v1 (reuse‑first)

* **Confirm execution path** using Alloy 6 artifacts:

  * **Embedded JVM** using the Alloy/Kodkod API (single path in this repo). ([GitHub][4])
* **Compare against remote shape**: capture how **Alloy.Api** structures endpoints and DTOs; note what we keep/omit (no DB for v1). ([GitHub][5])
* **Evaluation criteria** for runner choice
  Headless support, unsat core availability, instance enumeration, timeout/kill semantics, determinism (seed), cold/warm latency, portability (Mac/Linux/CI, container), error surface, and maintenance cadence. ([Alloy Docs][3])
* **Smallest pre‑PoC (48–72h)**
  Execute a hard‑coded tiny spec + single check/run via the **embedded API**; capture {status, satisfiable, instance_count, stats, versions, logs}; enforce timeout/kill; include solver/tool versions in results. ([Maven Repository][2])

### 2) Requirements Definition

#### Functional (v1)

* **Inputs:**

  * spec (primary Alloy module text), optional files (aux modules), command (check|run), target (predicate/assertion), optional scope and options (e.g., bitwidth, max\_instances, timeout\_ms, solver, seed).
* **Execution:**

  * Find 0..K instances; interruptible via timeout; deterministic mode via seed when supported.
* **Outputs:**

  * SAT | UNSAT | UNKNOWN, **first K instances** (atoms/relations), **unsat core** when solver supports it (e.g., Minisat‑with‑Unsat‑Core), timings, solver/tool versions, normalized logs. ([Alloy Docs][3])

#### Non‑functional (v1)

* **Local‑first**: single command; no network required.
* **Portability**: JVM **17+** or container image with pinned Alloy 6 CLI; CI on Linux and macOS. ([GitHub][4], [Maven Repository][2])
* **Performance targets**: small models finish in ≤5s (cold), ≤2s (warm); configurable timeout_ms.
* **Safety**: no network/filesystem access from specs; process/container limits (CPU/mem), temp workdir, input size caps.
* **Observability**: structured logs, spec hash, tool/solver versions, mode (local|remote).

#### Portability plan

* **P0**: Local module + **embedded API** runner.
* **P1**: Containerized runner (pinned Alloy 6 + solvers).
* **P2**: Optional **remote** worker (stateless REST) shaped after **Alloy.Api** but speaking the **same JSON contract** as the local function. ([GitHub][5])

#### Testing

* Golden specs: tiny SAT, tiny UNSAT (with unsat‑core), and a forced timeout.
* Snapshot tests for the JSON shape; property‑based tests over options (seeds/scopes).

---

## Architecture (concise)

**Local execution (default):**

* **Adapter** (this module) ⇄ **Runner** (embedded API only).
* The adapter normalizes results to JSON, enforces timeouts, and maps solver/tool versions.

**Remote execution (optional):**

* Same contract behind /execute with request/response DTOs modeled on **Alloy.Api**; no DB in v1; deployable as a stateless container with a single queue/replica. ([GitHub][5])

---

## Proposed GPT Function (draft)

json
{
  "name": "alloy.run",
  "description": "Execute an Alloy 6 specification and return results.",
  "parameters": {
    "type": "object",
    "properties": {
      "spec": { "type": "string", "description": "Primary Alloy module text (.als)" },
      "command": { "type": "string", "enum": ["check", "run"] },
      "target": { "type": "string", "description": "Name of the command/predicate/assertion" },
      "files": {
        "type": "array",
        "items": { "type": "object", "properties": { "path": {"type":"string"}, "content": {"type":"string"} }, "required": ["path","content"] }
      },
      "scope": {
        "type": "object",
        "properties": { "exact": { "type": "boolean" }, "bitwidth": { "type": "integer" }, "ints": { "type": "integer" }, "max_instances": { "type": "integer" } }
      },
      "options": {
        "type": "object",
        "properties": {
          "timeout_ms": { "type": "integer" },
          "solver": { "type": "string", "description": "e.g., minisat, sat4j; unsat-core requires a compatible solver" },
          "symmetry_breaking": { "type": "integer" },
          "seed": { "type": "integer" }
        }
      }
    },
    "required": ["spec", "command", "target"]
  }
}

### Result envelope (illustrative)

json
{
  "status": "ok | timeout | error",
  "satisfiable": true,
  "instances": [
    { "atoms": { "A": ["A0","A1"] }, "relations": { "R": [["A0","A1"]] }, "skolems": {}, "viz": { "dot": "digraph {...}" } }
  ],
  "unsat_core": { "highlights": ["line:col-..."], "note": "requires solver with unsat-core" },
  "statistics": { "solve_ms": 317, "vars": 123, "clauses": 456, "solver": "..." },
  "provenance": { "engine": "Alloy 6.2.0", "spec_sha256": "...", "mode": "local|remote" },
  "logs": "normalized runner output",
  "errors": []
}

---

## Deliverables

1. **Pre‑PoC** runnable (embedded API + minimal tests) and sample specs.
2. JSON schema for parameters/result + TypeScript types.
3. Container image definition with pinned Alloy 6 artifacts.
4. v1 backlog with estimates + risk register.

---

## Risks & Mitigations

* **Long‑running/NP‑hard cases** → strict timeouts; cap instances; surface partial logs; allow seeds and scope tuning.
* **Untrusted input** → run in locked‑down process/container (no net/FS); resource limits; sanitize include paths.
* **Version drift** → pin artifacts (e.g., Alloy 6.2.x CLI), record versions in results; reproducible builds. ([Maven Repository][2])
* **Feature mismatch across solvers** → detect solver capabilities at start; degrade gracefully (e.g., omit unsat‑core if unsupported). ([Alloy Docs][3])

---

## Timeline (indicative, \~2 weeks)

* **Days 1–3:** Finalize embedded runner integration; draft JSON contract; prepare Pre‑PoC.
* **Days 4–6:** Pre‑PoC implementation; timeout/kill; normalized logs; return first‑K instances.
* **Days 7–9:** Finalize function schema; containerize runner; add tests.
* **Days 10–14:** v1 requirements, backlog, optional remote worker sketch (Alloy.Api‑style), and approval.

**Exit Criteria:** Pre‑PoC demo succeeds; embedded‑only decision documented; schema frozen; path to v1 (local and optional remote) is approved.

---

## Milestones

### Milestone A: Foundation and direction

* **Cards**: [K-20250809-001], [K-20250809-010] (optionally [K-20250809-013])
* **Exit**: DTO schema aligned and backward compatible; CI builds container with pinned Alloy 6 and self-check; basic health/readiness available.

### Milestone B: Core execution semantics

* **Cards**: [K-20250809-002], [K-20250809-003]
* **Exit**: Per-request timeout enforced; deterministic seed plumbed; instances serialized in normalized JSON suitable for snapshots.

### Milestone C: Contract externalization

* **Cards**: [K-20250809-007], [K-20250809-008]
* **Exit**: OpenAPI for /v1/solve matches DTOs with examples; tests green (timeout, SAT/UNSAT snapshots, property-based scaffold).

### Milestone D: Safety and capability completeness

* **Cards**: [K-20250809-004], [K-20250809-011], [K-20250809-009], [K-20250809-005]
* **Exit**: Safe multi-module in-memory resolver; runtime caps and sandboxing enforced; solver capability detection and graceful degradation; provenance/statistics included; all tests pass.

### Milestone E: Documentation and release

* **Cards**: [K-20250809-012], [K-20250809-014]
* **Exit**: Docs synced with current API and examples; release workflow produces tagged images and changelog.

---

### Notes & references (key facts)

* Latest Alloy 6 release and feature set (including temporal logic), official site. ([Alloy Tools][1])
* Alloy distribution artifact includes native solvers for unsat‑core support when needed (e.g., MiniSatProverJNI). ([Maven Repository][2])
* Java 17+ requirement from the project README. ([GitHub][4])
* Unsat‑core support when using a compatible solver (e.g., *Minisat with Unsat Core*). ([Alloy Docs][3], [Alloy Tools][7])
* Alloy.Api repository (REST pattern, Swagger). ([GitHub][5])
* Background on Alloy 6’s relationship to Pardinus/Electrum (for later spikes). ([Nuno Macedo][6])

---

[1]: https://alloytools.org/?utm_source=chatgpt.com "Alloy Analyzer"
[2]: https://central.sonatype.com/artifact/org.alloytools/org.alloytools.alloy.dist?utm_source=chatgpt.com "org.alloytools:org.alloytools.alloy.dist - Maven Central"
[3]: https://alloy.readthedocs.io/en/latest/tooling/analyzer.html?utm_source=chatgpt.com "Analyzer - Alloy Documentation - Read the Docs"
[4]: https://github.com/AlloyTools/org.alloytools.alloy?utm_source=chatgpt.com "AlloyTools/org.alloytools.alloy - GitHub"
[5]: https://github.com/cmu-sei/Alloy.Api?utm_source=chatgpt.com "cmu-sei/Alloy.Api - GitHub"
[6]: https://nmacedo.github.io/pubs/JAR22.pdf?utm_source=chatgpt.com "[PDF] Pardinus: A temporal relational model finder - Nuno Macedo"
[7]: https://alloytools.org/quickguide/unsat.html?utm_source=chatgpt.com "unsatisfiable core - Alloy Analyzer"
