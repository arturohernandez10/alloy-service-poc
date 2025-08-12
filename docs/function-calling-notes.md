## AI function-call contract notes (delta vs PROJECT and Alloy.Api)

### Scope and intent

- Summarize current API behavior relevant to AI tool-calls and list deltas to address in a later task.

### Current envelope and instance shape

- Status: `ok | timeout | error`
- Determination: `satisfiable: true | false | null`
- Instances: array of objects with:
  - `atoms`: Map<sigLabel, String[]> (user-defined sigs only; labels normalized, e.g., `this/` removed)
  - `relations`: Map<sigLabel.fieldLabel, String[][]> (tuples sorted for determinism)
- Unsat core (prover only): `{ must: Range[], maybe: Range[], low: Range[], snippetByRangeId: Map }`
- Debug: `{ solverId, durationMs, modelHash, originalFile, bitwidth?, maxSeq?, seed?, kodkod? }`

### Notable differences vs PROJECT.md (AI-facing)

- UNKNOWN result
  - PROJECT mentions `SAT | UNSAT | UNKNOWN`. Our runner exposes `status: ok|timeout|error` plus `satisfiable`.
  - For AI clients, “unknown” should be inferred when satisfiable is not determined due to runtime conditions.

- Multi-module inputs
  - PROJECT function allows `files` and `open`. Current runner is single-module: warns on `open`, ignores `files`.

- Convenience for K=1
  - Instances are always an array; many AI prompts assume one instance. Consider an alias or documented convention.

- Unsat core shape
  - PROJECT example was illustrative. We use `must/maybe/low` ranges with `snippetByRangeId`.

- Stats/provenance naming
  - PROJECT: `statistics`/`provenance`. We expose `debug` with similar information but different keys.

- Error surface
  - PROJECT showed `errors` array and `logs`. We expose a single `error` object with `type`/`message`.

### Proposed AI-facing classification for UNKNOWN

- Treat as UNKNOWN when:
  - `status == "timeout"` (solve exceeded budget)
  - `status == "error"` and `error.type` in:
    - `solver_error` (engine/solver failure)
    - `oom` or memory-related failures
    - `canceled`/`interrupted` (thread interrupted)
    - `busy` (queue overflow)
    - `unsupported_feature` (e.g., higher-order skolemization failure)
- Do not treat as UNKNOWN when:
  - `error.type == "bad_request"` (invalid request/spec; caller should fix input)

### Optional convenience fields (client‑friendly)

- Add derived `conclusion: "sat" | "unsat" | "unknown"` alongside existing fields.
- Add `unknown_reason` with values: `timeout | interrupted | oom | solver_error | busy | unsupported_feature`.
- Document “use `instances[0]` when `scope.max_instances == 1`”; optionally add `instance` alias.

### Multi-module input plan

- P0 (document): Single-module only. `open` ignored; `files` not resolved.
- P1 (implement): In-memory module resolver honoring `files` and mapping `open` to provided modules.

### Alignment options with PROJECT.md

- Keep current envelope; add derived `conclusion` for AI simplicity.
- Optionally alias `debug.durationMs -> statistics.solve_ms`, `debug.modelHash -> provenance.spec_sha256`, and expose `provenance.engine`/`mode`.

### Action items

1) Add derived fields: `conclusion`, `unknown_reason` (non-breaking).
2) Document UNKNOWN mapping in README (+ client example).
3) Add convenience alias for single instance or doc guidance.
4) Expose minimal `statistics`/`provenance` aliases (names only; same data).
5) Plan and implement in-memory multi-module resolver for `files`/`open`.
6) Add snapshot tests for a model with relations; assert normalized atoms/relations.

### Reference files consulted (useful excerpts)

- `reference/org.alloytools.alloy.cli/src/main/java/org/alloytools/alloy/cli/Evaluator.java`: Usage of `A4Solution` for evaluation, states/traces navigation, and Skolem exposure; informs optional `skolems`/trace ideas.
- `reference/org.alloytools.api/src/main/java/org/alloytools/alloy/dto/SolutionDTO.java`: Solution container shape (instances, duration, loop state); helps map stats/provenance naming.
- `reference/org.alloytools.api/src/main/java/org/alloytools/alloy/dto/InstanceDTO.java`: Instance value layout (sig→field→tuples) and Skolem representation; guides relation tuple normalization.
- `reference/org.alloytools.api/src/main/java/org/alloytools/alloy/dto/TuplesDTO.java`: Tuple set representation (`arity`, `data`); parallels our `relations` structure.
- `reference/org.alloytools.api/src/main/java/org/alloytools/alloy/dto/ExecutionDTO.java`: Execution metadata knobs (solver, symmetry, unrolls, overflow flags); informs what to expose in `debug`/statistics.
- `reference/org.alloytools.api/src/main/java/org/alloytools/alloy/dto/CommandDTO.java`: Command aggregation and solution listing; context for external API result envelopes.
- `reference/org.alloytools.alloy.core/src/main/java/edu/mit/csail/sdg/translator/TranslateAlloyToKodkod.java`: Where SAT/UNSAT callbacks occur and exceptions raised (e.g., higher‑order not skolemized) — helps classify `unsupported_feature` into UNKNOWN.
- `reference/org.alloytools.alloy.core/src/main/java/edu/mit/csail/sdg/translator/A4Solution.java`: Accessors and metadata including original file/command; aligns with `debug.originalFile` and provenance ideas.
- `reference/org.alloytools.alloy.core/src/main/java/edu/mit/csail/sdg/translator/Simplifier.java`: Mentions “unknowns” during CNF simplification (theoretical, not a third result); supports treating engine failures (not SAT/UNSAT) as UNKNOWN at service layer.
