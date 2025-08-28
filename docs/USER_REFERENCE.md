## Alloy Service — User Reference (HTTP)

This guide shows how to run Alloy models over HTTP and how to read the results. It focuses on the user-facing API and the Alloy domain mapping. For operators and deployment details, see the project README.

### Overview

- **Service**: Runs an Alloy 6 model (run/check) and returns results as JSON.
- **Endpoint**: `POST /v1/solve` (JSON). Health probe: `GET /healthz`.
- **Spec source of truth**: `docs/openapi.yaml` (kept in sync with the code/tests).

### Quickstart

Run locally (Java 17+):

```bash
mvn -q -DskipTests package
java -jar target/alloy-service-poc-0.1.0.jar
# Health check
curl -s http://localhost:8080/healthz
```

### Minimal usage

SAT example (find one instance):

```bash
curl -s http://localhost:8080/v1/solve \
  -H 'Content-Type: application/json' \
  -d @- << 'JSON'
{
  "spec": "sig A {}\npred show {}\nrun show for 3\n",
  "command": "run",
  "target": "show",
  "scope": { "max_instances": 1 },
  "options": { "solver": "SAT4J", "timeout_ms": 2000 }
}
JSON
```

UNSAT example (no instance exists):

```bash
curl -s http://localhost:8080/v1/solve \
  -H 'Content-Type: application/json' \
  -d @- << 'JSON'
{
  "spec": "sig A {}\nassert noA { no A }\ncheck noA for exactly 0 A\n",
  "command": "check",
  "target": "noA",
  "options": { "solver": "SAT4J", "timeout_ms": 2000 }
}
JSON
```

### Request (what you send)

- **spec** (string, required): Alloy module text.
- Selecting the command (any one of):
  - **command** + **target** (e.g., `"run"` + `"show"`), or
  - **commandIndex** (integer index into parsed commands).
- **scope** (optional):
  - `max_instances` (cap how many instances to enumerate; default 1; hard-capped by service).
  - `exactScopes` (advanced): per-signature exact cardinalities by label.
- **options** (optional):
  - `solver` (default `SAT4J` for portability; use `minisat.prover` to request unsat cores when native libs are available)
  - `timeout_ms` (per-request time limit; default ~20s).
  - `solver` (e.g., `SAT4J`. For unsat cores, prefer `minisat.prover` when available).
  - `seed` (best-effort determinism), `symmetry_breaking`, `coreGranularity`, `coreMinimization`, `recordKodkod` (advanced).
- **requestId** (optional): echoed back.

Notes:
- Multi-module specs via `files[]` are not yet active; `open` statements are ignored in single-module mode (a warning is returned).

### Response (what you get)

- **status**: `ok` | `timeout` | `error` (transport-independent envelope).
- **satisfiable**: `true` | `false` | `null` (null when undetermined or on errors).
- **instances** (when SAT): array of normalized Alloy instances:
  - `atoms`: map `SignatureLabel -> [AtomId, ...]`.
  - `relations`: map `SignatureLabel.FieldLabel -> [[a,b,...], ...]` tuples.
  - Built-ins like `univ`, `Int`, `seq/Int`, `String`, `iden`, `none` are omitted.
- **unsat_core** (when UNSAT and solver supports cores):
  - `must`, `maybe`, `low`: arrays of source ranges with `file` and `startLine/Col`..`endLine/Col`.
  - `snippetByRangeId`: optional map from range id to source snippet.
- **command**: stringified Alloy command actually executed.
- **warnings**: non-fatal notices (e.g., caps applied, ignored fields).
- **debug**: minimal diagnostics (e.g., `solverId`, `durationMs`, `modelHash`, `seed`).
- **error**: present when `status=error` (e.g., `type: bad_request|solver_error|parse_error|timeout|oom|busy`).

Example SAT response (shortened):

```json
{
  "status": "ok",
  "satisfiable": true,
  "instances": [
    {
      "atoms": { "A": ["A$0", "A$1"] },
      "relations": {}
    }
  ],
  "debug": { "solverId": "sat4j", "durationMs": 37 }
}
```

Example UNSAT response (cores may require a prover solver):

```json
{
  "status": "ok",
  "satisfiable": false,
  "unsat_core": {
    "must": [],
    "maybe": [],
    "low": []
  },
  "debug": { "solverId": "minisat.prover", "durationMs": 42 }
}
```

### Interpreting instances (Alloy → JSON)

- Each top-level signature becomes a key under `atoms` with its atom ids.
- Each field becomes a key under `relations` as `Signature.Field` with tuple arrays.
- Atom identifiers are abstract and stable within a single response.

### Limits and current behavior

- Model size capped (~200k chars). Instance enumeration capped (default 1, hard limit enforced).
- Timeout defaults to ~20s; override per request via `options.timeout_ms`.
- Unsat cores depend on chosen solver and native availability; if unsupported, the field is omitted.
- Built-in signatures are not listed.

### OpenAPI

- The full contract and examples are defined in `docs/openapi.yaml`. Use this file for code generation or validation.


