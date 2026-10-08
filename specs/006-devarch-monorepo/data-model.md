# Data Model: 006-devarch-monorepo

Esta feature no introduce datos de dominio; sus "entidades" son el layout del producto, la versión, los comandos y la resolución del motor.

## ProductLayout

| Campo | Valor | Notas |
|-------|-------|-------|
| `monorepoRoot` | raíz de `maestria-ia` | contiene `.github/workflows/dev-arch.yml` |
| `productRoot` | `apps/dev-arch` | contiene `VERSION`, `Makefile`, `package.json`, `schema/VERSION` |
| `engineRoot` | `apps/dev-arch/engine` | basedir Maven; contiene `pom.xml`, `test-for-demo/`, `target/` |
| `schemaDir` | `apps/dev-arch/schema` | `VERSION` (contrato, 1.0.0), `1.0.0/`, `gen/`, `generated/` |
| `webDir` | `apps/dev-arch/web` | app Next.js |
| `engineDist` | `apps/dev-arch/web/engine-dist` | generado; `engine.jar` + `dotnet-analyzer*` |
| `areas` | `web`, `engine`, `schema`, `skills`, `docs`, `docker` | FR-001 (exactamente seis) |

**Reglas de validación**
- Las seis áreas existen como directorios; `CONSTITUTION.md`, `PREMISE.md`, `specs/` existen en `productRoot`.
- Ningún archivo versionado bajo `productRoot` coincide con `*.jar` ni con `dotnet-analyzer`/`dotnet-analyzer.exe` ni con `output*.json` fuera de las excepciones de 005 (`schema/fixtures/**/output*.json`, `engine/src/test/resources/baseline-v1.1.0/**/output*.json`) y `engine/demo*.json`.
- Localización para pruebas (Java `ProductLayout`, .NET `ContractSchemas.EngineRoot`): propiedad explícita > búsqueda ascendente (`schema/VERSION` para producto; `pom.xml` + `src/test/dotnet` para motor) > error con mensaje que nombra la propiedad.

## ProductVersion

| Campo | Tipo | Regla |
|-------|------|-------|
| `source` | archivo `apps/dev-arch/VERSION` | una línea, semver `MAJOR.MINOR.PATCH`, inicial `1.2.0` |
| `declarations` | lista | `engine/pom.xml` → `/project/version`; `engine/src/main/dotnet/DotNetAnalyzer/DotNetAnalyzer.csproj` → `<Version>`; `web/package.json` → `.version`; `package.json` → `.version` |
| `derived` | lista | `McpServerMain` (serverInfo.version) ← recurso filtrado `${project.version}`; `dotnet-analyzer` AssemblyInformationalVersion ← `<Version>` |
| `contractVersion` | `schema/VERSION` | independiente; no lo toca `version.sh` |

**Transiciones**: editar `VERSION` → `make version-sync` → todas las `declarations` = `source` → `make version-check` OK. Cualquier `declaration` ≠ `source` → `version-check` FAIL `<archivo>: <declarada> != <VERSION>`.

## ProductCommand (objetivos Make)

| Objetivo | Depende de | Efecto | Éxito |
|----------|------------|--------|-------|
| `build` | `prereqs` | `engine-build` (mvn package -DskipTests) → `dotnet publish` → `engine-stage` → `npm ci` → `npm run build -w web` | jar + analizador en `web/engine-dist/`, `web/.next/` |
| `test` | `prereqs` | `version-check`, `test-engine`, `schema-check`, `test-web`, `test-acceptance` | todos exit 0 |
| `test-engine` | `prereqs` | `mvn -B verify` (engine) + `dotnet test --filter "Category!=Corpus"` | exit 0 |
| `test-web` | — | `npm test -w web` (`node:test` + `tsx`) | exit 0 |
| `test-acceptance` | — | `npx cucumber-js` (sin `@slow` salvo `DEVARCH_E2E=true`) | exit 0 |
| `schema-check` | — | `bash scripts/schema-check.sh` (formato de 005) | `RESULT: OK` |
| `dev` | `prereqs` | build del motor si falta/obsoleto, `npm run dev -w web` con `JAVA_HOME`/`DOTNET_ROOT` | web en `:9002` |
| `clean` | — | `mvn clean`, borra `engine/target`, `web/.next`, `web/engine-dist`, `target/` de pruebas de aceptación | `git status --porcelain --ignored` sin generados |
| `e2e`, `doctor` | — | marcador informativo | exit 0 |
| `version-sync`, `version-check` | — | `scripts/version.sh sync|check` | ver ProductVersion |

## EngineResolution (web)

| Entrada | Regla |
|---------|-------|
| `ENGINE_JAR_PATH` | si está definido y no vacío: ruta del jar (absoluta o relativa a `cwd`) |
| defecto | `<cwd>/engine-dist/engine.jar` (`cwd` = `web/` con `npm run dev`) |
| `JAVA_HOME` | si está definido: `javaBin = $JAVA_HOME/bin/java`; si no, `java` |
| `DOTNET_ANALYZER_PATH`, `DOTNET_ROOT` | pasan sin cambios al proceso del motor (lookup de `DotNetToolLocator`) |

**Estados**: `RESOLVED {javaBin, jarPath}` → ejecuta; `ENGINE_NOT_BUILT {jarPath}` → HTTP 500 con `error` que contiene "Engine not built", la ruta probada y "make build".

## CiJob

| Job | Filtro (`apps/dev-arch/` implícito) | Needs | Bloqueante |
|-----|-------------------------------------|-------|------------|
| `changes` | — | — | sí |
| `engine-java` | `engine/pom.xml`, `engine/src/main/java/**`, `engine/src/test/java/**`, `engine/src/test/resources/**`, `schema/**`, `VERSION` | changes | sí |
| `engine-dotnet` | `engine/src/main/dotnet/**`, `engine/src/test/dotnet/**`, `schema/**`, `VERSION` | changes | sí |
| `web` | `web/**`, `schema/**`, `package.json`, `package-lock.json`, `VERSION` | changes | sí |
| `schema` | `schema/**`, `engine/src/**`, `scripts/schema-check.sh` | changes | sí |
| `acceptance` | `Makefile`, `scripts/**`, `tests/acceptance/**`, `specs/006-*/tests/**`, `package.json` | changes | sí |
| `release` | push `main` con cambios de motor, o tag `dev-arch-v*` | engine-java, engine-dotnet | — |
