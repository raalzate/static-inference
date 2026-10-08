# Contract: paquete `schema/` (layout, `$id`, entradas)

**Version**: 1.0.0 | **FR**: FR-001, FR-002, FR-004..FR-007, FR-017, FR-018

## Layout

```text
schema/
  VERSION                                  # "1.0.0" — fuente de verdad de la versión
  1.0.0/
    common.schema.json                     # $defs: SchemaVersion, DocumentMeta, GraphMeta, Component,
                                           #   PackageGroup, CodeIssue, Edge, ApiContracts, ApiEndpoint,
                                           #   Parameter, ApiSchema, ApiSchemaProperty, ProjectMetadata,
                                           #   PackageDependencyInfo, SecretLocation, Proposal, ProposalMetrics,
                                           #   ProposalSignals, ProposalTable, LegacyEntrypoint, SupportLibrary
    dependency-graph.schema.json           # output.json
    architecture.schema.json               # output_architecture.json
    entrypoints.schema.json                # output_entrypoints.json
    mcp/
      analyze_project.response.schema.json
      get_dependency_graph.response.schema.json      # $ref ../dependency-graph.schema.json
      get_architecture_proposal.response.schema.json # $ref ../architecture.schema.json
      get_api_contracts.response.schema.json         # $ref ../entrypoints.schema.json
      get_component_metrics.response.schema.json     # $ref ../common.schema.json#/$defs/Component
  gen/
    generate.mjs                           # schema -> generated/ (determinista)
    check.mjs                              # regenera a tmp, diff vs generated/, valida fixtures + samples
  generated/                               # VERSIONADO en git, nunca editado a mano
    types.ts                               # json-schema-to-typescript
    validators.ts                          # json-schema-to-zod (+ export parseX(doc) por documento)
    index.ts
  fixtures/
    valid/java-standar/{output,output_architecture,output_entrypoints}.json
    valid/dotnet-ecommerce/{output,output_architecture,output_entrypoints}.json
    valid/mcp/analyze_project.json
    invalid/<caso>.json                    # una mutación por archivo, nombre = motivo
  test/
    validators.test.ts                     # node:test + tsx
  package.json                             # "private": true, scripts: generate, check, test
  package-lock.json
  README.md
```

## `$id` y referencias

- Base: `https://static-inference.dev/schema/1.0.0/`. Cada archivo declara `"$schema": "https://json-schema.org/draft/2020-12/schema"` y `"$id": "<base><ruta relativa>"`.
- Referencias siempre relativas (`"$ref": "common.schema.json#/$defs/Component"`), resolubles offline mapeando la base a `schema/1.0.0/` en los tres validadores.
- `meta.schema_version` usa `"const": "1.0.0"`; el segmento de versión de `$id` y `schema/VERSION` deben coincidir (verificado por `schema-check`).

## Reglas de esquema

- Objetos cerrados: `"additionalProperties": false` (o `unevaluatedProperties: false` donde haya `allOf`).
- Anulable: `"type": ["string", "null"]`; opcional = ausente de `required`.
- Obsoleto: `"deprecated": true` en `Component.interface`.
- `EntrypointsDocument` = `allOf: [ {$ref ApiContracts}, {required: ["meta"]} ]` + `unevaluatedProperties: false`.
- Cada `$def` lleva `title` (nombre del tipo TS generado) y `description`.

## Scripts npm (`schema/package.json`)

| Script | Efecto | Exit ≠0 si |
|--------|--------|-----------|
| `npm run generate` | Escribe `generated/` desde `1.0.0/` | error de generación |
| `npm run check` | Regenera en tmp y compara byte a byte con `generated/`; valida `fixtures/valid/**` (deben pasar), `fixtures/invalid/**` (deben fallar) y `../target/schema-samples/**` si existe | diff, fixture inválida aceptada, válida rechazada, `VERSION` ≠ `$id` |
| `npm run check -- --file <ruta> --kind <dependency-graph\|architecture\|entrypoints\|mcp/<tool>>` | Valida un solo documento con los validadores generados | documento inválido |
| `npm test` | `node --import tsx --test test/` | fallo de prueba |

## Consumo previsto (Fase 2, fuera de alcance)

`import { parseDependencyGraph, type DependencyGraph } from "@static-inference/schema"` desde la web; reemplaza `web/src/lib/types.ts`.
