# Contract: `schema-check` (punto de entrada único de verificación)

**FR**: FR-012..FR-016 | **US**: US5

## Invocación

```bash
make schema-check            # Makefile raíz → scripts/schema-check.sh
bash scripts/schema-check.sh # equivalente directo
```

Variables opcionales: `JAVA_HOME`, `DOTNET_ROOT`, `SCHEMA_CHECK_SKIP_BUILD=1` (reutiliza artefactos ya compilados).

## Etapas (en orden; todas se ejecutan aunque una falle, el resultado agrega)

| # | Frente | Acción | FAIL si | NOT VERIFIED si |
|---|--------|--------|---------|-----------------|
| 1 | `version` | Compara `schema/VERSION`, segmento de `$id`, `const` de `schema_version`, `SchemaVersion.CURRENT` (Java) y `SchemaVersion.Current` (.NET) | alguna difiere | — |
| 2 | `java` | `mvn -B -q test -Dgroups=schema-contract` (tests `@Tag("schema-contract")`: artefactos Java, respuestas MCP, paridad CLI/MCP) | algún test falla | no hay JDK 17 |
| 3 | `dotnet` | `dotnet test src/test/dotnet/DotNetAnalyzer.Tests --filter Category=SchemaContract` | algún test falla | no hay `dotnet` SDK |
| 4 | `ts` | `npm --prefix schema ci && npm --prefix schema run check && npm --prefix schema test` | diff en `generated/`, fixture mal clasificada, prueba falla | no hay Node ≥ 20 |

## Salida

Una línea por frente, más un resumen:

```text
[schema-check] version: OK (1.0.0)
[schema-check] java:    OK
[schema-check] dotnet:  FAIL — $.components[3].layer: "Servicio" is not one of [...]
[schema-check] ts:      NOT VERIFIED — node not found
[schema-check] RESULT:  FAIL (1 failed, 1 not verified)
```

- Exit `0` solo si los 4 frentes son `OK`.
- Exit `1` si algún frente es `FAIL` o `NOT VERIFIED` (FR-015, Clarification de spec).
- Los mensajes de fallo incluyen la ruta JSON Pointer/JSONPath del campo y el frente.

## CI

Job `schema-check` en `.github/workflows/ci.yml`: `actions/setup-java` (17), `actions/setup-dotnet` (8.0.x), `actions/setup-node` (20), `make schema-check`. Bloqueante.
