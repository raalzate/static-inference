# @static-inference/schema

Formal, versioned contract (JSON Schema draft 2020-12) of the Static Inference outputs:

| Document | File | Schema |
|----------|------|--------|
| Dependency graph | `output.json` / MCP `get_dependency_graph` | `1.0.0/dependency-graph.schema.json` |
| Consolidated architecture | `output_architecture.json` / MCP `get_architecture_proposal` | `1.0.0/architecture.schema.json` |
| Entrypoints (API contracts) | `output_entrypoints.json` / MCP `get_api_contracts` | `1.0.0/entrypoints.schema.json` |
| MCP responses | `analyze_project`, `get_*` | `1.0.0/mcp/*.response.schema.json` |

`VERSION` is the single source of truth of the contract version. Every schema `$id` lives under
`https://static-inference.dev/schema/<VERSION>/` and resolves offline to `schema/<VERSION>/`.

## Layout

- `1.0.0/` — the schemas (hand-written, reviewed like code).
- `generated/` — TypeScript types and zod validators **generated** from the schemas. Never edit by hand.
- `gen/generate.mjs` — regenerates `generated/` deterministically.
- `gen/check.mjs` — verifies `generated/` is up to date, validates fixtures and producer samples.
- `fixtures/valid/`, `fixtures/invalid/` — real outputs (accepted) and minimal mutations (rejected).
- `test/` — `node:test` suite run through `tsx`.

## Usage

```bash
npm --prefix schema ci
npm --prefix schema run generate   # after editing 1.0.0/*.schema.json
npm --prefix schema run check      # generated/ up to date + fixtures + target/schema-samples
npm --prefix schema run check -- --file out.json --kind dependency-graph
npm --prefix schema test
```

```ts
import { parseDependencyGraph, type DependencyGraph } from "@static-inference/schema";
```

Versioning policy and migration notes: [`docs/SCHEMA.md`](../docs/SCHEMA.md).
The whole chain (Java, .NET, generated types, version) is verified by `make schema-check`.
