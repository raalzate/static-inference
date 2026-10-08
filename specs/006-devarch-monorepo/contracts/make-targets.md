# Contract: comandos del producto (`apps/dev-arch/Makefile`)

Invocación: `make <objetivo>` con cwd = `apps/dev-arch`. Variables de entorno respetadas: `JAVA_HOME`, `DOTNET_ROOT`, `MVN_ARGS`, `DEVARCH_E2E`, `SCHEMA_CHECK_SKIP_BUILD`, `SCHEMA_CHECK_VERBOSE`.

| Objetivo | Exit 0 cuando | Exit ≠ 0 cuando | Salida observable |
|----------|---------------|-----------------|-------------------|
| `build` | jar en `engine/target/java-dependency-extractor.jar`, `web/engine-dist/engine.jar`, `web/engine-dist/dotnet-analyzer`, `web/.next/` | prerrequisito ausente (antes de compilar) o fallo de cualquier etapa | líneas `[prereqs] ...`, `[build] <etapa>: OK|FAIL` |
| `test` | `version-check`, `test-engine`, `schema-check`, `test-web`, `test-acceptance` OK | cualquiera falla | `[test] <subconjunto>: OK|FAIL` y resumen final |
| `test-engine` | `mvn -B verify` y `dotnet test --filter "Category!=Corpus"` OK | fallo de pruebas | logs Maven/xUnit |
| `test-web` | `npm test -w web` OK | fallo | salida TAP de `node:test` |
| `test-acceptance` | Cucumber-JS OK (`@slow` omitidos sin `DEVARCH_E2E=true`) | fallo de escenario | resumen Cucumber |
| `schema-check` | idéntico a 005: `[schema-check] RESULT: OK` | idéntico a 005 | formato de líneas de 005 sin cambios |
| `dev` | servidor en `http://localhost:9002` con motor local | prerrequisito ausente | log de Next |
| `clean` | sin artefactos generados | — | — |
| `e2e` | siempre | — | `[e2e] not implemented yet (feature 011)` |
| `doctor` | siempre | — | `[doctor] not implemented yet (feature 012)` |
| `version-sync` | declaraciones actualizadas | `VERSION` no semver | `[version] <archivo>: <antes> -> <después>` |
| `version-check` | todas iguales a `VERSION` | alguna difiere | `[version] <archivo>: <declarada> != <VERSION>` |

## Prerrequisitos (`scripts/check-prereqs.sh`)

| Herramienta | Regla | Mensaje FAIL |
|-------------|-------|--------------|
| JDK | `$JAVA_HOME/bin/java` (o `java`) major == 17 | `[prereqs] jdk: FAIL — JDK 17 required (found <n>|none); set JAVA_HOME to a JDK 17` |
| .NET | SDK ≥ 8 y runtime `Microsoft.NETCore.App 8.x` | `[prereqs] dotnet: FAIL — .NET SDK >= 8 and .NET 8 runtime required (found <...>)` |
| Node | major ≥ 20 y `npm` presente | `[prereqs] node: FAIL — Node 20+ required (found <n>|none)` |

Termina con exit 2 en el primer FAIL; no ejecuta ninguna compilación.
