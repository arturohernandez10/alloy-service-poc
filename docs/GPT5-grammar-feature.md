# GPT‑5 for Alloy

This document is a discovery document for the GPT‑5 grammar feature. It's not intended to be a complete guide, but rather a starting point for refining the API design for the Alloy runner.

## What’s new (and relevant) in GPT‑5 for Alloy

* **Custom tools accept raw, plaintext payloads** (not JSON). GPT‑5 can call a tool of `"type": "custom"` and pass *free‑form text* (e.g., an Alloy bundle). This is distinct from classic JSON function‑calls. The Cookbook calls this *Free‑Form Function Calling* and shows the tool wiring. Also: the **custom tool type does not support parallel tool calling**. ([OpenAI Cookbook][1])
* **Attach a grammar to your custom tool**: GPT‑5 lets you constrain tool arguments with a **Context‑Free Grammar (CFG)**. Supported syntaxes are **Lark** and **regex**, enforced via LLGuidance under the hood; several Lark features are explicitly unsupported (lookarounds, lazy quantifiers, terminal priorities, templates, `%declare`, `%import` except `common`). The Cookbook shows how to embed the grammar on the tool. ([OpenAI Cookbook][1])
* **New request knobs** you can (optionally) use when you want deterministic, low‑latency “emit‑then‑run” loops:

  * `reasoning.effort: "minimal"` for very fast, low‑deliberation turns. ([OpenAI Cookbook][2])
  * `text.verbosity: "low" | "medium" | "high"` (Cookbook’s “verbosity parameter”) to keep emissions terse when you only want a bundle. ([OpenAI Cookbook][1])
* **Warm‑up / first‑call latency**: when you first use a new schema/grammar, OpenAI preprocesses it; first call may be slower, subsequent calls with the same grammar are fast. (This is documented for structured outputs and discussed for grammars; warm them at startup.) ([GitHub][3], [OpenAI Community][4], [everything.intellectronica.net][5])
* **Where JSON Schema still matters**: GPT‑5 still supports **Structured Outputs** to force the *model’s* messages to match a JSON Schema (useful for classic function‑calling). For Alloy, you’ll get *much better fidelity* by using CFG for the *input payload* (the `.als` text) and keeping your tool’s **return value JSON** minimal and stable. ([OpenAI Platform][6], [OpenAI][7])
* **Tool control**: you can still force tool selection with `tool_choice` and (for models that support it) disable parallel calls using `parallel_tool_calls:false`; Cookbook notes custom tools don’t support parallel calling anyway, but setting false prevents future surprises. ([OpenAI Platform][8], [OpenAI Cookbook][1])

## Alloy 6.2 essentials the grammar & runner could cover

* **Language surface**: modules, `open`, paragraphs (`sig`, `fact`, `pred`, `fun`, `assert`, `run`/`check` with scopes). Temporal Alloy (v6): unary `always`, `eventually`, `after`, `before`, `historically`, `once`; binary `until`, `releases`, `since`, `triggered`; primed next‑state (`field'`) for `var` fields. ([alloytools.org][9], [alloy.readthedocs.io][10])
* **Unsat cores & solvers**: enable **MiniSat with Unsat Core** (aka `MiniSatProverJNI`) to get cores; surface minimization and granularity options. In `A4Options`: `coreMinimization` (0..2), `coreGranularity` (0..3), `symmetry`, `unrolls`, and solver enum (SAT4J, MiniSatJNI, MiniSatProverJNI, ZChaffJNI…). ([alloytools.org][11], [alloy.readthedocs.io][12])
* **Core coordinates**: `A4Solution.highLevelCore()` and `.lowLevelCore()` return `Pos` objects; `Pos` invariants show **1‑based** line/column (>0). Use that in error/core envelopes. ([alloytools.org][13])

## Sample API shape

**Tool registration (Responses API)** — single custom tool that accepts *Alloy bundle text constrained by a Lark grammar*:

```json
{
  "type": "custom",
  "name": "alloy_exec",
  "description": "Accepts an Alloy 6.2 bundle (.als files) and executes all run/check commands. Returns SAT/UNSAT, instances, unsat cores, and artifacts.",
  "format": {
    "type": "grammar",
    "syntax": "lark",
    "definition": "<your Lark grammar text here>"
  }
}
```

Call pattern (sketch):

```json
{
  "model": "gpt-5",
  "input": "Use alloy_exec to emit a bundle containing 1–N .als files. Obey the grammar. Every bundle must include at least one run or check command.",
  "tools": [ { /* the tool above */ } ],
  "tool_choice": { "type": "tool", "name": "alloy_exec" },
  "parallel_tool_calls": false,
  "reasoning": { "effort": "minimal" },
  "text": { "verbosity": "low", "format": { "type": "text" } }
}
```

* **Why this wiring**: custom tools take plaintext; CFG keeps the emission valid; `parallel_tool_calls:false` keeps the bundle atomic; `minimal` + `verbosity:low` reduce latency/chatter. Cookbook shows free‑form tool calls and CFG attachment; it also notes custom tools don’t support parallel tool calling. ([OpenAI Cookbook][1])

**Runner behavior (server‑side):**

1. **Split & validate** bundle separators; parse each file with Alloy.
2. **Execute** each `run`/`check` with Alloy 6.2; set solver to MiniSat with Unsat Core when cores requested; expose **minimization** and **granularity** controls mapped to `A4Options`. ([alloy.readthedocs.io][12], [alloytools.org][11])
3. **Return JSON** (see below).
4. **Errors**: compile/type errors with `file:line:col` (1‑based), diagnostic message, and stderr; timeouts as `status:"timeout"` with elapsed ms. ([alloytools.org][14])

## `/v1/solve` sample response envelope (OpenAPI 3.1 / JSON Schema 2020‑12)

**Why 3.1**: OpenAPI 3.1 aligns with **JSON Schema 2020‑12**, so you use `type: ["T","null"]` for nullables (no `nullable:`). This plays nicely with GPT‑5 (you won’t *generate* this JSON with the model; the server does, but tooling/linting is better on 3.1). ([OpenAPI Initiative][15])

**Top‑level** (sketch):

```yaml
type: object
required: [status, results, artifacts]
properties:
  status:
    type: string
    enum: [ok, error, timeout]
  model_summary:
    type: object
    properties:
      files:
        type: array
        items:
          type: object
          required: [name, bytes]
          properties:
            name: { type: string }
            bytes: { type: integer, minimum: 0 }
  results:
    type: array
    items:
      $ref: '#/components/schemas/ExecutionResult'
  artifacts:
    type: array
    items: { type: string, format: uri }
```

**ExecutionResult** (SAT/UNSAT per command):

```yaml
ExecutionResult:
  type: object
  required: [cmd, sat, timings_ms]
  properties:
    cmd:
      type: string
      enum: [run, check]
    target: { type: string }
    scope:  { type: string }           # normalized (runner-defined)
    sat:    { type: boolean }
    instance:
      type: ["object","null"]          # present only if sat=true
      properties:
        atoms:
          type: object
          additionalProperties:
            type: array
            items: { type: string }
        relations:
          type: object
          additionalProperties:
            type: array
            items:
              type: array
              items: { type: string }  # tuple elements; arity varies by relation
              minItems: 1
    unsat_core:
      type: ["object","null"]          # present if sat=false and core available
      properties:
        available: { type: boolean }
        labels:
          type: array
          items: { type: string }
        minimized:
          type: string
          enum: [fast, medium, slow]
        positions:
          type: array
          items:
            type: object
            required: [file, line, col]
            properties:
              file: { type: string }
              line: { type: integer, minimum: 1 }  # A4 Pos is 1-based
              col:  { type: integer, minimum: 1 }
    timings_ms:
      type: object
      properties:
        parse: { type: integer, minimum: 0 }
        sat:   { type: integer, minimum: 0 }
```

**Notes & rationale**

* **Maps for atoms/relations**: using `additionalProperties` keeps payloads compact (keys are dynamic: qualified sig/field names). This is ergonomic for clients; there’s no downside since **the server produces the JSON** (we’re not asking GPT to generate it). Use tuple arrays for relations. (Best‑practice; not an OpenAI requirement.)
* **Nullability**: use `type: ["object","null"]` (OAS 3.1). Avoid `nullable:` which is 3.0‑only. ([OpenAPI Initiative][15])
* **Unsat core**: expose both label lists and **positions** (file, line, col) so clients can highlight source; coordinates are 1‑based per `Pos`. Minimization/ granularity strings map to `A4Options.coreMinimization` (0..2 ⇒ slow/medium/fast by your mapping) and `coreGranularity` (0..3). ([alloytools.org][11])
* **Status vs HTTP codes**: keep HTTP **200** for `ok`/`timeout` runs (work completed, even if no model found); use **4xx/5xx only for transport/server errors**. (Conventional for analysis services; Analyzer docs treat “no instance” as a *result*, not a transport error.) ([alloy.readthedocs.io][12])

## The Lark CFG for GPT‑5

Your “bundle + Alloy 6.2” Lark is on‑point for GPT‑5. Two guardrails from the Cookbook to keep it robust:

* **Greedy lexing**: terminals (UPPERCASE) get matched first, longest‑wins; don’t split free text across multiple terminals; bound them with explicit char‑classes. You already thread `_WS`; if `%ignore` is rejected, inline `_WS` (Cookbook shows this pattern). ([OpenAI Cookbook][1])
* **Unsupported Lark features**: avoid regex lookarounds, lazy quantifiers, terminal priorities, Lark templates/`%declare`, and `%import` (except `common`). Keep terminals bounded; avoid `.*`. ([OpenAI Cookbook][1])

## Minimal DTO→Schema mapping (what goes in `components/schemas`)

| DTO concept              | JSON Schema/OAS 3.1 pattern                      | Why it works well                                                                             |                                       |
| ------------------------ | ------------------------------------------------ | --------------------------------------------------------------------------------------------- | ------------------------------------- |
| `status`                 | `enum: [ok, error, timeout]`                     | Explicit, easy for clients and LLMs.                                                          |                                       |
| \`satisfiable: boolean   | null\`                                           | Remove this; use `sat: boolean` per result; if timeout, either no result or `status:timeout`. | Avoids tri‑state booleans in results. |
| `instance.atoms`         | `object` with `additionalProperties: string[]`   | Map keyed by qualified sig names.                                                             |                                       |
| `instance.relations`     | `object` with `additionalProperties: string[][]` | Each relation is an array of tuples (arrays).                                                 |                                       |
| `unsat_core.positions[]` | `{file, line>=1, col>=1}`                        | Matches `Pos`’s >0 invariant. ([alloytools.org][14])                                          |                                       |
| `timings_ms`             | integers ≥0                                      | Obvious.                                                                                      |                                       |

## Gotchas & tips (so the tool is reliable)

* **Disable parallel tool calls** when emitting bundles. Cookbook: the `"custom"` type doesn’t support parallel tool calling; setting `parallel_tool_calls:false` prevents accidental multi‑emission if APIs change. ([OpenAI Cookbook][1])
* **Warm the grammar** on boot (send a tiny no‑op bundle) to avoid first‑call latency spike from grammar preprocessing/caching. ([GitHub][3])
* **Keep grammar tight**: bounded tokens; explicit whitespace; avoid left recursion; list temporal operators explicitly (you did). ([OpenAI Cookbook][1])
* **Prefer CFG for input; JSON for output**: Treat JSON Schema “structured outputs” as optional here—use CFG for emitting `.als`; let your server be the source of truth for result JSON. (OpenAI Structured Outputs still exists for other cases.) ([OpenAI Platform][6])
* **Tool selection**: set `tool_choice` to `alloy_exec` on the emission turn so the model doesn’t wander. (Standard OpenAI tools guidance.) ([OpenAI Platform][8])

## Alloy specifics to expose as options (so clients aren’t surprised)

* **Solver selection**: `SAT4J` (default), `MiniSatJNI`, **`MiniSatProverJNI`** (for unsat core), `ZChaffJNI` (legacy). Expose a plain enum and a friendly default. ([alloytools.org][11])
* **Unsat‑core knobs**: `coreGranularity` (0..3), `coreMinimization` (0..2) as `fast|medium|slow` in the API and map to integers internally. ([alloytools.org][11])
* **Other useful options**: `symmetry` (default 20), `skolemDepth` (default 0), `unrolls` (loop/recursion bound). (Expose sparingly to reduce client overwhelm.) ([alloytools.org][11])

## Validation plan (quick probes you can script)

1. **Runner smoke tests**: feed tiny SAT/UNSAT/timeout/spec‑error models; verify envelope + 1‑based positions + artifacts link. (Analyzer & `Pos` behavior.) ([alloy.readthedocs.io][12], [alloytools.org][14])
2. **Grammar stress**: comments, multi‑file bundles, temporal ops, scopes (`for n but ...`, `exactly`), `var` with `field'` use. ([alloy.readthedocs.io][10])
3. **Unsat core**: confirm `MiniSatProverJNI` + minimization settings return positions via `highLevelCore`/`lowLevelCore`. ([alloytools.org][13])
4. **First‑call warm‑up**: hit a warm‑up endpoint on boot to cache the grammar. ([GitHub][3])

---

## Why these choices align with GPT‑5 and OpenAPI (summary)

* **CFG + custom tool** is the most reliable way to make the model emit *valid Alloy 6.2* text. You get strict conformance (via LLGuidance), plaintext payloads, and no ambiguity from JSON unions. ([OpenAI Cookbook][1])
* **OpenAPI 3.1** for your *server output* (not generated by the model) lets you use modern JSON Schema (2020‑12) — proper `null` type, `additionalProperties` maps, and tuple arrays — without fighting older `nullable:` semantics. ([OpenAPI Initiative][15])

---

## Sources & references you’ll actually use

* **GPT‑5 / Tools / CFG**

  * *Cookbook:* **GPT‑5 New Params and Tools** — custom tools (plaintext), CFG with Lark/regex, LLGuidance, unsupported Lark features, and the note that **custom tools don’t support parallel tool calling**. ([OpenAI Cookbook][1])
  * *Cookbook:* **GPT‑5 Prompting Guide** — `reasoning.effort` with `"minimal"`. ([OpenAI Cookbook][2])
  * *OpenAI docs:* **Function Calling** / **Using Tools** — forcing a tool via `tool_choice`. ([OpenAI Platform][8])
  * *OpenAI blog/docs:* **Structured Outputs** (background on JSON Schema mode) and its intro post. ([OpenAI Platform][6], [OpenAI][7])
  * *Latency notes:* Structured‑output first‑call preprocessing & caching. (Community/documentation notes; use warm‑ups.) ([GitHub][3], [OpenAI Community][4], [everything.intellectronica.net][5])

* **Alloy 6.2 language & tooling**

  * **Temporal Alloy** reference & operator list. ([alloy.readthedocs.io][10], [alloytools.org][9])
  * **Analyzer options** (Unsat core, solver settings). ([alloy.readthedocs.io][12])
  * **API Javadocs**: `A4Options` (solver, `coreGranularity`, `coreMinimization`, `symmetry`, `unrolls`); `A4Solution` cores; `Pos` (1‑based). ([alloytools.org][11])

* **OpenAPI / JSON Schema 2020‑12**

  * **OpenAPI 3.1 migration** — drop `nullable`, use `type: ["T","null"]`. ([OpenAPI Initiative][15])

[1]: https://cookbook.openai.com/examples/gpt-5/gpt-5_new_params_and_tools "GPT-5 New Params and Tools"
[2]: https://cookbook.openai.com/examples/gpt-5/gpt-5_prompting_guide?utm_source=chatgpt.com "GPT-5 prompting guide"
[3]: https://github.com/vercel/ai/discussions/3656?utm_source=chatgpt.com "Increased latency when using structuredOutputs on first"
[4]: https://community.openai.com/t/structured-output-caching-and-latency/904483?utm_source=chatgpt.com "Structured Output: Caching and Latency - API"
[5]: https://everything.intellectronica.net/p/structured-outputs-big-time?utm_source=chatgpt.com "Structured Outputs Big Time - by Eleanor Berger"
[6]: https://platform.openai.com/docs/guides/structured-outputs?utm_source=chatgpt.com "Structured model outputs - OpenAI API"
[7]: https://openai.com/index/introducing-structured-outputs-in-the-api/?utm_source=chatgpt.com "Introducing Structured Outputs in the API"
[8]: https://platform.openai.com/docs/guides/tools?utm_source=chatgpt.com "Using tools - OpenAI API"
[9]: https://alloytools.org/alloy6.html?utm_source=chatgpt.com "Alloy 6"
[10]: https://alloy.readthedocs.io/en/latest/language/time.html?utm_source=chatgpt.com "Time - Alloy Documentation - Read the Docs"
[11]: https://alloytools.org/documentation/alloy-api/edu/mit/csail/sdg/alloy4compiler/translator/A4Options.html "
A4Options
"
[12]: https://alloy.readthedocs.io/en/latest/tooling/analyzer.html?utm_source=chatgpt.com "Analyzer — Alloy Documentation documentation"
[13]: https://alloytools.org/documentation/alloy-api/edu/mit/csail/sdg/alloy4compiler/translator/A4Solution.html "
A4Solution
"
[14]: https://alloytools.org/documentation/alloy-api/edu/mit/csail/sdg/alloy4/Pos.html?utm_source=chatgpt.com "Pos"
[15]: https://www.openapis.org/blog/2021/02/16/migrating-from-openapi-3-0-to-3-1-0?utm_source=chatgpt.com "Migrating from OpenAPI 3.0 to 3.1.0"
