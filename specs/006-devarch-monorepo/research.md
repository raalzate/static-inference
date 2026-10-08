# Research: 006-devarch-monorepo

**Date**: 2026-10-08 | **Spec**: [spec.md](./spec.md)

## Evidencia levantada (solo lectura)

Fuentes: este repositorio (Static Inference, `main` = v1.2.0, commit `6af8e40`) y el worktree del monorepo `/Users/raul.alzate/Documents/maestria/maestria-devarch` (rama `feature/dev-arch-monorepo`, commit `a32d5f5` que importó `apps/dev-arch` tal cual).

| # | Hallazgo | Impacto en el diseño |
|---|----------|----------------------|
| E1 | `apps/dev-arch/` contiene `src/`, `docs/` (`DEVELOPERS.md`, `ENVIRONMENT.md`, `blueprint.md`), `package.json` (`nextn` 0.1.0, Next 15.3.8, scripts `dev --turbopack --port 9002`, `build`, `lint`, `typecheck`), `next.config.ts` (`ignoreBuildErrors`, `ignoreDuringBuilds`), `components.json`, `postcss.config.mjs`, `tailwind.config.ts`, `tsconfig.json`, `README.md`, `.gitignore`, **`java-dependency-extractor.jar` (15 MB)** y **`output_entrypoints.json`** | Todo salvo el jar y el JSON se mueve a `web/` (D1 literal: "`src,docs,config` → `web/`"); jar y JSON se eliminan (FR-004) |
| E2 | `src/app/api/analyze/route.ts`: `exec("java -jar " + jarPath + " " + projectRoot + " output.json")` con `jarPath = path.resolve(process.cwd(), 'java-dependency-extractor.jar')`; lee `output.json` y `output_architecture.json` de `process.cwd()` | Cambio mínimo: resolver la ruta del motor (env > `engine-dist/engine.jar`), usar `JAVA_HOME` si existe, `execFile` con argumentos (rutas con espacios, FR edge case). La lectura desde `cwd` se mantiene (deuda de 007) |
| E3 | `src/lib/types.ts` consume `proposal.metrics.tables: string[]`; el output v1.2.0 mantiene `metrics.tables` como lista de textos y añade `proposals[*].tables` (objetos), `meta`, `signals`, `clusters`, etc. | La web ignora campos nuevos sin fallar (FR-016); no hace falta adaptador. Verificación: escenario US3 + revisión de tipos |
| E4 | `.gitignore` raíz del monorepo ignora `package-lock.json`, `*.lock`, `*.zip`, `*.sql`, `*.db`, `build/`, `dist/`, `.env`; no existe `.github/` en el monorepo | Lockfile reincluido con negación en `apps/dev-arch/.gitignore` (no se toca el global); fixtures ZIP se generan en tiempo de prueba; no hay CI previa que romper, pero la nueva debe filtrarse por ruta |
| E5 | El motor versiona archivos que el `.gitignore` global ignora: `test-for-demo/jee-architecture-master/dump.sql` y 6 `*.lock` de `.metadata` | `git subtree add` usa `read-tree`: entran versionados igual; los cambios futuros siguen rastreados. Las pruebas que hacen copia hermética con `git ls-files` (`DemoOutputs.copyTracked`) siguen viéndolos |
| E6 | `.gitignore` del motor ignora `AGENTS.md`, `CLAUDE.md`, `.tessl/`, `.specify/`, `tessl.json`, `.mcp.json` (este último con ruta absoluta de la máquina) | El producto pasa a versionar `AGENTS.md`, `CLAUDE.md`, `tessl.json`, `.tessl/RULES.md`, `.mcp.json` (relativa) y `.specify/context.json`; ignora `.tessl/tiles/` y los generados de `.specify/` |
| E7 | Pruebas del motor que asumen la raíz del repo antiguo: Java `SchemaRegistry.repoRoot()` = padre de `schema/` (busca `schema/VERSION` subiendo desde `user.dir` o `-Dschema.dir`); `DemoOutputs`, `GovernanceSteps`, `GeneratedTypesSteps`, `SchemaCheckSteps`, `DotNetRawSteps`, `CiWorkflowSteps`, `BaselineArtifacts` (39 usos) mezclan rutas **del motor** (`pom.xml`, `target/`, `test-for-demo/`, `src/test/dotnet`, cwd de `mvn`/`dotnet`) y **del producto** (`schema/`, `specs/`, `docs/SCHEMA.md`, `skills/`, `MCP.md`, `.github/workflows/ci.yml`). .NET `ContractSchemas.RepoRoot` = padre de `schema/` y lo usa para fixtures `src/test/dotnet/...` y `target/schema-samples` | Promover `schema/` rompe estas rutas: se necesita una capa de localización (D3). `RunSchemaContractFeaturesTest` usa `@SelectDirectories("specs/005-json-schema-contract/tests/features")` relativo al basedir Maven y surefire fija `schema.dir`/`features.dir` a `${project.basedir}/...` |
| E8 | `scripts/schema-check.sh` usa `ROOT` = raíz del repo para `schema/`, `src/main/java/...SchemaVersion.java`, `src/main/dotnet/...`, `target/`; invoca `npm --prefix schema ci` (necesita `schema/package-lock.json`) | Se mueve a la raíz del producto con `ENGINE_DIR`/`SCHEMA_DIR`; con workspaces npm el lock pasa a la raíz, así que la instalación es `npm ci` en la raíz |
| E9 | `CiWorkflowSteps` (TS-039 de 005) lee `.github/workflows/ci.yml` en la raíz del repo y exige un job con `make schema-check`, setup JDK 17/.NET 8/Node 20 y sin `continue-on-error`/`|| true` | GitHub solo lee workflows de la raíz del monorepo: la definición pasa a `<monorepo>/.github/workflows/dev-arch.yml`; la prueba localiza el archivo por propiedad (D3). Las aserciones no cambian |
| E10 | Versión del motor declarada en `pom.xml` (`1.2.0`) y en `McpServerMain.SERVER_VERSION = "1.2.0"` (literal); `DotNetAnalyzer.csproj` no declara `<Version>`; web `0.1.0`; `schema/VERSION` = `1.0.0` (versión de **contrato**, independiente) | Fuente única `VERSION` + script de propagación + prueba de divergencia (D6) |
| E11 | Máquina de referencia: JDK por defecto 25 (`java -version`), JDK 17 instalado (`zulu-17`); Node v25; .NET **SDK 10.0.105** con runtimes 8.0.27 y 10.0.5, instalado por Homebrew (no en `/usr/local/share/dotnet`) | Prerrequisitos: JDK **17** vía `JAVA_HOME`; .NET SDK ≥ 8 **y** runtime `Microsoft.NETCore.App 8.x`; Node ≥ 20. El apphost del analizador necesita `DOTNET_ROOT` con .NET de Homebrew (mismo truco que `schema-check.sh`) |
| E12 | `DotNetToolLocator` busca `dotnet-analyzer` primero junto al jar, luego `DOTNET_ANALYZER_PATH`, luego `PATH` | Si `make build` deja `dotnet-analyzer` junto a `web/engine-dist/engine.jar`, el flujo .NET de la web funciona sin configuración |
| E13 | Historia del motor: 48 commits en `main` | SC-004 verificable: `git rev-list` del repo origen ⊆ historia de `apps/dev-arch/engine` |

## Decisiones

### D1. Importación del motor con historia: `git subtree add` sin `--squash`

- **Decision**: `git subtree add --prefix apps/dev-arch/engine <ruta local de static-inference> main` (sin `--squash`), tras mergear esta rama de spec a `main` del motor, seguido de un commit de reorganización con `git mv`.
- **Rationale**: Preserva todos los commits (SC-004) con `git log -- apps/dev-arch/engine/...` vía `--follow`/subtree; aprobado por el usuario.
- **Alternatives**: `--squash` (pierde historia, descartado); `git filter-repo` + merge (reescribe hashes, más frágil); submódulo (dos repos, contradice D1 del plan).

### D2. Reorganización tras la importación

- **Decision**: Desde `engine/` se mueven al nivel de producto: `specs/`, `CONSTITUTION.md`, `PREMISE.md`, `schema/` (→ `schema/`), `skills/`, `docs/SCHEMA.md` (→ `docs/`), `scripts/schema-check.sh` (→ `scripts/`), `CHANGELOG.md` (→ raíz del producto). Se eliminan del motor `Makefile` (reemplazado por el del producto), `.github/` (inerte en subcarpeta; reemplazado por el workflow del monorepo) y `.gitignore` (fusionado). Quedan en el motor: `pom.xml`, `mvnw*`, `.mvn/`, `src/`, `test-for-demo/`, `spotbugs-exclude.xml`, `README.md`, `ALGORITHMS.md`, `CONFIGURATION.md`, `MCP.md`, `demo*.json`, `mcp-config.example.json`.
- **Rationale**: Mínimo movimiento que cumple D1 del plan; la fusión de `ALGORITHMS/CONFIGURATION/MCP` en `docs/` es Fase 7.
- **Alternatives**: mover toda la documentación ya (rompe referencias de skills y de `GovernanceSteps` sin aportar valor en esta fase).

### D3. Localización de rutas en las pruebas del motor (`ProductLayout`)

- **Decision**: Nueva clase de soporte de pruebas Java `com.extractor.testsupport.ProductLayout` con `engineRoot()` (propiedad `engine.root`, por defecto `user.dir`), `productRoot()` (propiedad `product.root`, por defecto el primer ancestro con `schema/VERSION`), `ciWorkflow()` (propiedad `devarch.ci.workflow`, por defecto `<monorepo>/.github/workflows/dev-arch.yml`, con respaldo a `<productRoot>/.github/workflows/ci.yml`) y `find(relative)` (primer existente entre `productRoot` y `engineRoot`). `SchemaRegistry`, `DemoOutputs`, step definitions y el runner BDD de 005 usan esta clase; surefire fija `engine.root`, `product.root`, `schema.dir=${project.basedir}/../schema`, `features.dir=${project.basedir}/../specs/005-json-schema-contract/tests/features`. En .NET, `ContractSchemas` añade `EngineRoot` (primer ancestro con `pom.xml` y `src/test/dotnet`) y usa `EngineRoot` para fixtures y `target/`.
- **Rationale**: Cambia **dónde** se buscan los archivos, nunca **qué** se afirma (regla assertion-integrity; FR-019, SC-005). `find()` permite mantener literal la lista `REFERENCE_DOCUMENTS` (`skills/...`, `MCP.md`, `specs/...`).
- **Alternatives**: (a) no promover `schema/` y declarar el workspace `engine/schema` → cero cambios en pruebas, pero contradice D1 del plan y deja el contrato "propiedad" del motor; queda como **alternativa de reserva** si el usuario rechaza tocar el arnés de pruebas. (b) Symlink `engine/schema → ../schema` → frágil en Windows y en `git ls-files`.

### D4. Orquestación: `Makefile` + scripts bash en la raíz del producto

- **Decision**: `apps/dev-arch/Makefile` con objetivos `build`, `test`, `test-engine`, `test-web`, `test-acceptance`, `e2e` (marcador), `schema-check`, `dev`, `clean`, `doctor` (marcador), `version-sync`, `version-check`. La lógica vive en `scripts/*.sh` (`check-prereqs.sh`, `engine-build.sh`, `engine-stage.sh`, `version.sh`, `schema-check.sh`, `dev.sh`). Toda invocación de Maven usa `JAVA_HOME` (si está definido) y `./mvnw` si `mvn` no existe.
- **Rationale**: Continúa la convención de 005 (`make schema-check` + script); Make ya es prerrequisito del motor.
- **Alternatives**: Taskfile (nueva herramienta); scripts npm puros (Maven/dotnet fuera de su ecosistema natural, plan D1 dice "engine se orquesta vía Makefile").

### D5. Workspaces npm (`web`, `schema`) y lockfile único

- **Decision**: `apps/dev-arch/package.json` privado (`dev-arch`, versión = `VERSION`) con `"workspaces": ["web", "schema"]`, `engines.node >= 20`, scripts `test:acceptance`. Lockfile único `apps/dev-arch/package-lock.json`, reincluido con `!package-lock.json` en `apps/dev-arch/.gitignore`. Se elimina `schema/package-lock.json`. La web declara `"@static-inference/schema": "*"` (resuelto al workspace local, sin publicar). `schema-check.sh` y `GeneratedTypesSteps` instalan con `npm ci` en la raíz del producto (antes `npm --prefix schema ci`).
- **Rationale**: FR-012, FR-017; npm ya es la herramienta de ambos paquetes.
- **Alternatives**: pnpm (nueva herramienta, `*.lock`… ignorado globalmente); paquetes independientes con `file:` (dos lockfiles, sin workspace).

### D6. Versión única

- **Decision**: `apps/dev-arch/VERSION` = `1.2.0`. `scripts/version.sh sync` escribe la versión en `engine/pom.xml` (`project/version`), `engine/src/main/dotnet/DotNetAnalyzer/DotNetAnalyzer.csproj` (`<Version>`), `web/package.json` y `package.json` raíz; `scripts/version.sh check` compara las cuatro declaraciones y falla nombrando el archivo. `McpServerMain` deja de tener literal: lee la versión de un recurso filtrado por Maven (`static-inference-version.properties`, `version=${project.version}`), cubierto por una prueba JUnit que compara `serverInfo.version` con `project.version`. `schema/VERSION` sigue independiente.
- **Rationale**: FR-020..FR-022; una sola edición manual (SC-007).
- **Alternatives**: `${revision}` CI-friendly + flatten plugin (plugin nuevo); propagación en tiempo de build sin escribir archivos (los IDE y `mvn` aislado verían otra versión).

### D7. La web usa el motor construido localmente (cambio mínimo en `route.ts`)

- **Decision**: Nuevo módulo `web/src/server/engine/engine-path.ts` con `resolveEngine(env, cwd)` → `{ javaBin, jarPath }`: `ENGINE_JAR_PATH` si está definido, si no `<cwd>/engine-dist/engine.jar`; `javaBin = $JAVA_HOME/bin/java` si existe `JAVA_HOME`, si no `java`. Si el jar no existe: error `ENGINE_NOT_BUILT` con mensaje "Engine not built: run `make build` (or set ENGINE_JAR_PATH)". `route.ts` usa `execFile(javaBin, ['-jar', jarPath, projectRoot, 'output.json'])`. `make build` copia `engine/target/java-dependency-extractor.jar` → `web/engine-dist/engine.jar` y publica `dotnet-analyzer` (framework-dependent) en `web/engine-dist/` (junto al jar, E12). `web/engine-dist/` está ignorado.
- **Rationale**: FR-013..FR-016 sin adelantar el cliente MCP (007). `execFile` evita problemas con espacios.
- **Alternatives**: apuntar directamente a `../engine/target/` (acopla la web al layout de Maven; D5 del plan prefiere `engine-dist/`).

### D8. Integración continua: un workflow del monorepo con filtros por área

- **Decision**: `<monorepo>/.github/workflows/dev-arch.yml` con `on.push`/`on.pull_request` limitados a `paths: ['apps/dev-arch/**', '.github/workflows/dev-arch.yml']` y tags `dev-arch-v*`; job `changes` (`dorny/paths-filter@v3`) que calcula `engine-java`, `engine-dotnet`, `web`, `schema`; jobs `engine-java` (build+test+SpotBugs+Checkstyle ratchet 4763), `engine-dotnet` (build+test `Category!=Corpus`+`dotnet format`+security-scan report-only), `web` (`npm ci`, `test-web`, `build`), `schema` (`make schema-check` bloqueante, JDK 17/.NET 8/Node 20, + BDD 005 con `SCHEMA_CHECK_E2E`), `acceptance` (escenarios 006), `release` (tags `dev-arch-v*` o push a `main` con cambios del motor; matriz `linux-x64/osx-arm64/osx-x64/win-x64`, framework-dependent). `defaults.run.working-directory: apps/dev-arch`.
- **Rationale**: Un solo archivo permite `needs:` entre jobs (release depende de engine-java/engine-dotnet) y conserva TS-039 de 005; los filtros cumplen FR-023/FR-024; reglas de disparo del contrato: `schema/**` activa schema + engine-java + engine-dotnet + web.
- **Alternatives**: un workflow por área con `on.paths` (no permite `needs:` entre workflows; release no podría depender de los tests).

### D9. Pruebas de aceptación de 006: Cucumber-JS en la raíz del producto

- **Decision**: `@cucumber/cucumber` (devDependency del `package.json` raíz) con `tsx` como loader; features en `specs/006-devarch-monorepo/tests/features/`, step definitions en `tests/acceptance/steps/`. Escenarios largos (clon limpio + `make build && make test`, `next dev` + ZIP) etiquetados `@slow` y habilitados solo con `DEVARCH_E2E=true` (patrón de `SCHEMA_CHECK_E2E` en 005), con guarda anti-recursión `DEVARCH_TEST_RUNNING=1`. Pruebas unitarias de la web (`engine-path.ts`, `route.ts` con `execFile` simulado) con `node:test` + `tsx` (ya usados por `schema/`), sin framework nuevo.
- **Rationale**: Las verificaciones son de producto (Make, npm, git, HTTP) y no del motor: un runner Node evita meter pruebas de la web en la suite Maven. Phase-discipline exige runner BDD presente.
- **Alternatives**: step definitions en Cucumber-JVM del motor (acopla la suite del motor a la web y al monorepo); Vitest (dependencia nueva; Fase 6 la evaluará).

### D10. Configuración unificada

- **Decision**: Un `.gitignore` en `apps/dev-arch/` (fusión de motor + web + `web/engine-dist/`, `.tessl/tiles/`, `.specify/{dashboard.html,score-history.json,active-feature}`, `!package-lock.json`); se eliminan `engine/.gitignore` y `web/.gitignore`. `.editorconfig` nuevo (UTF-8, LF, 4 espacios Java/C#, 2 espacios TS/JSON/YAML, tabs en Makefile). `.mcp.json` con `tessl` y `dev-arch-engine` (`java -jar engine/target/java-dependency-extractor.jar --mcp`, relativo a la raíz del producto). `AGENTS.md`/`CLAUDE.md`/`tessl.json`/`.tessl/RULES.md` del motor pasan a la raíz del producto y se versionan.
- **Rationale**: FR-026..FR-029.
- **Alternatives**: mantener `.gitignore` por área (contradice "único conjunto").

## Tessl Tiles

No se instalan tiles nuevos: la feature no introduce librerías de runtime. Herramientas nuevas de desarrollo (`@cucumber/cucumber`, `dorny/paths-filter`) no tienen tile relevante; la ya instalada `tessl-labs/intent-integrity-kit` cubre el flujo.

## Riesgos

| Riesgo | Mitigación |
|--------|------------|
| Cambios de arnés de pruebas percibidos como alteración de aserciones | D3 limita el cambio a localización; los `.feature` y `context.json` de 001–005 no se tocan; `tasks.md` lo explicita; confirmación del usuario pendiente |
| El bucle Maven en curso en este árbol y el `subtree add` desde `main` | La importación se hace desde el worktree del monorepo leyendo el repo origen; no se ejecuta nada en `target/` de este árbol |
| `.NET` de Homebrew sin `DOTNET_ROOT` → apphost no arranca desde la web | `scripts/dev.sh` y `engine-stage.sh` exportan `DOTNET_ROOT` derivado de `dotnet --list-sdks` |
| El jar histórico de 15 MB sigue en la historia del monorepo | Fuera de alcance (reescritura de historia); FR-004 aplica al árbol versionado |
