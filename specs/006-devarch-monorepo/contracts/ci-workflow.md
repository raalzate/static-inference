# Contract: CI del producto (`<monorepo>/.github/workflows/dev-arch.yml`)

```yaml
on:
  push:
    branches: [main]
    tags: ['dev-arch-v*']
    paths: ['apps/dev-arch/**', '.github/workflows/dev-arch.yml']
  pull_request:
    branches: [main]
    paths: ['apps/dev-arch/**', '.github/workflows/dev-arch.yml']
defaults:
  run:
    working-directory: apps/dev-arch
```

| Job | `if` | Setup | Pasos clave | Tolerancia a fallo |
|-----|------|-------|-------------|--------------------|
| `changes` | siempre | — | `dorny/paths-filter@v3` → salidas `engine-java`, `engine-dotnet`, `web`, `schema`, `acceptance` | no |
| `engine-java` | `needs.changes.outputs.engine-java == 'true'` | JDK 17 temurin | `make version-check`; `mvn -B clean verify` (engine); SpotBugs con `spotbugs-exclude.xml`; Checkstyle `maxAllowedViolations=4763` | no |
| `engine-dotnet` | `...engine-dotnet == 'true'` | .NET 8.0.x | build; `dotnet test --filter "Category!=Corpus"`; `dotnet format --verify-no-changes`; security-scan (report-only, igual que hoy) | no (salvo security-scan, como hoy) |
| `web` | `...web == 'true'` | Node 20, cache `apps/dev-arch/package-lock.json` | `npm ci`; `make test-web`; `npm run build -w web` | no |
| `schema` | `...schema == 'true'` | JDK 17, .NET 8.0.x, Node 20 | `make schema-check`; BDD 005 (`SCHEMA_CHECK_E2E=true mvn -B test -Dtest=RunSchemaContractFeaturesTest` en engine) | **no** (TS-039 de 005) |
| `acceptance` | `...acceptance == 'true'` | JDK 17, .NET 8.0.x, Node 20 | `make build`; `DEVARCH_E2E=true make test-acceptance` | no |
| `release` | push a `main` con `engine-*` o tag `dev-arch-v*` | JDK 17, .NET 8.0.x | `make build-engine`; matriz `linux-x64, osx-arm64, osx-x64, win-x64`, `--self-contained false`; artefacto `dev-arch-engine-<tag>-<rid>` | — |

Cada job de área (`engine-java`, `engine-dotnet`, `web`, `schema`, `acceptance`) declara `timeout-minutes: 20` (SC-009).

Reglas de filtro (relativas a `apps/dev-arch/`): ver `data-model.md` → CiJob. Un cambio en `schema/**` activa `schema`, `engine-java`, `engine-dotnet` y `web`. Un cambio fuera de `apps/dev-arch/**` no dispara el workflow.
