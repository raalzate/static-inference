# Research: 005-json-schema-contract

**Date**: 2026-10-07 | **Spec**: [spec.md](./spec.md)

## Evidencia levantada

Salidas reales generadas el 2026-10-07 con el JAR v1.1.0 (`mvn -q -o package -DskipTests`):

- Java: `java -jar target/java-dependency-extractor.jar test-for-demo/java-standar <tmp>/out.json`
- .NET vía puente: mismo comando sobre `test-for-demo/dotnet-ecommerce` (con `DOTNET_ANALYZER_PATH`).
- .NET crudo: `dotnet-analyzer test-for-demo/dotnet-ecommerce` (stdout).

Hallazgos que condicionan el diseño:

| # | Hallazgo | Impacto en el contrato |
|---|----------|------------------------|
| E1 | El analizador .NET **omite** las propiedades nulas (`description`, `request_body_schema`, `domain`, `ejb_type`, `web_role`, `web_type`, `dependency_accuracy`, `decomposition_accuracy`) y no emite `interface`; el puente Java las re-emite como `null` | Los campos anulables se modelan como **opcionales y anulables** (no requeridos); el conjunto requerido es la intersección de lo que emiten ambos productores |
| E2 | `DotNetBridgeAnalyzer` deserializa con `FAIL_ON_UNKNOWN_PROPERTIES=false` | Si .NET emite `schema_version` y `DependencyGraph.Meta` (Java) no lo declara, el valor se pierde silenciosamente: Java debe declarar el campo y fijarlo él mismo |
| E3 | `code_issues[*]` = `{type, severity, message, line, pattern}`; `type` ∈ `BUG_PATTERN, CODE_STYLE, SECURITY, PERFORMANCE` (enum Java) | Contrato según output real; contratos 002/003 (`rule`, `location`) quedan obsoletos |
| E4 | `meta.source` ∈ `spoon`, `roslyn`, `roslyn-fallback` (`ProjectAnalyzer.cs:157`) | Catálogo cerrado (FR-005) |
| E5 | `edges[*].type` compuesto (`call,use,injection-constructor`); tipos .NET `call, implements, injection, registration` | Patrón `^[a-z-]+(,[a-z-]+)*$`, no enum |
| E6 | `project_metadata.components_with_secrets` es `int`; `secrets_locations` es lista de `{path,line,kind,redacted}`; `shared_domain` es `string`; `ProjectMetadata` usa `NON_NULL` | Ajustar referencias de skills (FR-020) |
| E7 | `proposals[*].tables` = lista de `{name, component, source}` | Ídem |
| E8 | `legacy_entrypoint` usa `NON_NULL`: los campos opcionales pueden faltar | Solo `type` requerido |
| E9 | `ConsolidatedArchitecture` y `ApiContracts` no tienen `meta` | Se añade `meta` (FR-008) |
| E10 | Los writers CLI (`MicroserviceInferenceMain`) y MCP (`AnalyzeProjectTool`, tools `Get*`) serializan los mismos objetos de dominio con `ObjectMapper` | Añadir el campo en el modelo garantiza paridad CLI/MCP sin tocar adaptadores (principio II) |
| E12 | `test-for-demo/dotnet-unstructured` se detecta como Java (`meta.source=spoon`) y produce un grafo vacío | Grafo vacío debe ser válido; la detección es un hallazgo fuera de alcance (Fase 4) |
| E11 | No existe `Makefile` en la raíz; CI (`.github/workflows/ci.yml`) tiene jobs Java y .NET separados, sin Node | `schema-check` como script + Makefile; nuevo job CI con Node 20 |

## Decisiones

### D1. Dialecto: JSON Schema draft 2020-12

- **Decision**: JSON Schema 2020-12, un archivo por documento raíz y un archivo `common` con `$defs` compartidos, referenciados por `$ref` relativos con `$id` absolutos bajo `https://static-inference.dev/schema/1.0.0/`.
- **Rationale**: Mandato del plan (D3). Es el dialecto que soportan los tres validadores elegidos y `json-schema-to-typescript`.
- **Alternatives**: OpenAPI 3.1 components (añade envoltorio sin valor: no hay API HTTP); draft-07 (más soporte en herramientas viejas pero no cumple D3).

### D2. Validador Java: `com.networknt:json-schema-validator` (scope `test`)

- **Decision**: `com.networknt:json-schema-validator` 1.5.x, solo en scope `test`; los esquemas se cargan desde `${project.basedir}/schema/` mapeando el prefijo `$id` al sistema de archivos.
- **Rationale**: Mandato del plan; soporta 2020-12 y usa Jackson (ya presente). Scope `test` ⇒ no entra en el fat JAR (principio VI, FR-021).
- **Alternatives**: `everit-json-schema` (sin 2020-12); `json-sKema` (menos maduro).

### D3. Validador .NET: `JsonSchema.Net` (solo en el proyecto de tests)

- **Decision**: paquete `JsonSchema.Net` (json-everything) en `DotNetAnalyzer.Tests.csproj` únicamente.
- **Rationale**: soporte completo de 2020-12 sobre `System.Text.Json` (el serializador que ya usa el analizador). No afecta al binario `dotnet-analyzer`.
- **Alternatives**: `NJsonSchema` (soporte 2020-12 incompleto); `Newtonsoft.Json.Schema` (licencia comercial por encima de cuota).

### D4. Generación TS: `json-schema-to-typescript` + `json-schema-to-zod`

- **Decision**: paquete npm privado `schema/` (`@static-inference/schema`, `private: true`) con `json-schema-to-typescript` (tipos `.d.ts`/`.ts`) y `json-schema-to-zod` (validadores zod), ejecutados por `schema/gen/generate.mjs`. Salida en `schema/generated/` **versionada en git** para poder detectar desactualización por diff. Pruebas con `node:test` + `tsx` (sin framework adicional).
- **Rationale**: Mandato del plan (D3). Versionar la salida permite que `schema-check` detecte "contrato cambiado sin regenerar" (US5-AS4) y que la web (Fase 2) consuma sin build previo.
- **Alternatives**: `quicktype` (genera desde muestras; no respeta el esquema como fuente); escribir zod a mano (prohibido por FR-017).

### D5. Estrictitud

- **Decision**: `additionalProperties: false` / `unevaluatedProperties: false` en objetos de estructura conocida; `additionalProperties: {<schema del valor>}` en mapas dinámicos (`api_contracts.schemas`, `external_dependencies`, `package_dependencies`, `microservice_candidates`, `ApiSchema.properties`).
- **Rationale**: es la única forma de detectar que un productor añadió un campo sin actualizar el contrato (criterio de salida). Ver Clarifications de spec.
- **Alternatives**: objetos abiertos (no detecta divergencia aditiva).

### D6. Opcionalidad de campos anulables (E1)

- **Decision**: un campo es `required` solo si ambos productores (Java directo y .NET crudo) lo emiten siempre. Los anulables se tipan `["<tipo>", "null"]` y no son requeridos.
- **Rationale**: no exige cambios de serialización en .NET (principio I: no tocar otros campos, FR-011).

### D7. Ubicación de `schema_version` y constante única

- **Decision**: `meta.schema_version` en `DependencyGraph.Meta` (Java y .NET), en un nuevo `meta` de `ConsolidatedArchitecture` y en un nuevo `meta` de `ApiContracts` (Java; en .NET el `meta` de `api_contracts` es opcional y no se emite). Constante en un único lugar por lenguaje (`com.extractor.model.SchemaVersion.CURRENT`, `DotNetAnalyzer.Model.SchemaVersion.Current`) y fuente de verdad en `schema/VERSION`. El esquema fija el valor con `const` en `meta.schema_version` y `schema-check` comprueba que las tres fuentes coinciden (FR-016).
- **Rationale**: el valor es una constante → no rompe determinismo (principio IV). Añadirlo al modelo (no a los adaptadores) mantiene la paridad CLI/MCP (principio II).
- **Ordering**: `schema_version` se serializa inmediatamente después de `source` en `meta` del grafo; en los nuevos `meta` es el único campo. El resto del orden no cambia (FR-011).
- **Puente**: `DotNetBridgeAnalyzer` conserva el `schema_version` recibido; si difiere de la constante Java, añade un warning (principio V) y sobrescribe con la constante Java (la salida final la firma el motor principal).

### D8. Respuestas MCP

- **Decision**: `schema/1.0.0/mcp/` con un esquema por tool: `get_dependency_graph` → `$ref` grafo; `get_architecture_proposal` → `$ref` arquitectura; `get_api_contracts` → `$ref` entrypoints; `get_component_metrics` → `$ref common#/$defs/Component`; `analyze_project` → objeto propio `{componentCount, edgeCount, proposalCount, endpointCount, filesGenerated, summary}` (camelCase, tal como se emite hoy).
- **Rationale**: FR-002 sin duplicación; `analyze_project` y `get_component_metrics` no cambian de forma (Clarification de spec).

### D9. `schema-check`

- **Decision**: `scripts/schema-check.sh` (bash, POSIX-friendly) + target `schema-check` en un nuevo `Makefile` raíz que lo invoca. Pasos: (1) coherencia de versión `schema/VERSION` ↔ Java ↔ .NET ↔ `$id`; (2) `mvn -B -q test -Dgroups=schema-contract`; (3) `dotnet test --filter Category=SchemaContract`; (4) `npm --prefix schema ci && npm --prefix schema run check` (regenera en temporal y compara con `schema/generated/`, valida fixtures con zod). Cada paso imprime `[schema-check] <frente>: OK|FAIL|NOT VERIFIED` y el script termina ≠0 si algún frente es FAIL o NOT VERIFIED (FR-015).
- **Windows**: se documenta ejecución vía Git Bash/WSL; un `.ps1` equivalente queda fuera de alcance (no hay requisito de Windows para mantenedores).
- **CI**: nuevo job `schema-check` en `ci.yml` (setup JDK 17 + .NET 8 + Node 20) que ejecuta `make schema-check`.

### D10. Fixtures TS

- **Decision**: `schema/fixtures/valid/{java-standar,dotnet-ecommerce}/` con las salidas reales (rutas absolutas normalizadas a relativas) y `schema/fixtures/invalid/` con mutaciones mínimas (campo requerido eliminado, `layer` fuera de catálogo, propiedad desconocida, `schema_version` ausente/no semver). Las pruebas Java y .NET escriben sus salidas validadas en `target/schema-samples/` y el chequeo TS las valida también si existen, para cubrir la cadena productor→consumidor sin depender solo de fixtures congeladas.

### D11. Ejecución de los `.feature` (regla phase-discipline: step definitions + runner BDD)

- **Decision**: runner BDD **Cucumber JVM** (`io.cucumber:cucumber-java`, `cucumber-junit-platform-engine`, scope `test`) en la suite Maven, con step definitions en `src/test/java/com/extractor/bdd/` que leen los 6 `.feature` de `specs/005-json-schema-contract/tests/features/`. Los pasos que dependen de .NET o Node invocan el binario `dotnet-analyzer`, `dotnet test` o `npm --prefix schema` como procesos y se etiquetan `@requires-dotnet` / `@requires-node` (omitidos con aviso si falta la herramienta; `schema-check` los ejecuta siempre). Para que CI vea los `.feature`, se añade la excepción `!specs/005-json-schema-contract/` en `.gitignore` (coherente con D1 del plan de integración: `specs/` deja de ignorarse). Las pruebas nativas (JUnit, xUnit, `node:test`) llevan el ID `TS-XXX` en su nombre/`@DisplayName` como en features 001–004.
- **Rationale**: la regla `phase-discipline` exige step definitions y runner BDD antes de commitear código cuando existen `.feature`; las features previas no lo cumplían (deuda §1.1 del plan). Scope `test` ⇒ principio VI intacto.
- **Alternatives**: solo pruebas nativas con IDs TS (viola la regla); Cucumber en .NET (Reqnroll) y en Node además del JVM (triplica step definitions sin valor adicional).
- **Pendiente confirmar**: des-ignorar parcialmente `specs/` antes de la Fase 2 (default elegido por agente — confirmado por usuario 2026-10-07).

## Tessl Tiles

`tessl` CLI está instalado pero las herramientas MCP de Tessl no están disponibles en esta sesión; no se buscaron ni instalaron tiles.

### Technologies Without Tiles

- networknt json-schema-validator, JsonSchema.Net, json-schema-to-typescript, json-schema-to-zod, zod: no verificado (sin acceso a `mcp__tessl__search`)
