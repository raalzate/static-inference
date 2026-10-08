# Quickstart: 006-devarch-monorepo

Prerrequisitos: JDK 17 (exportar `JAVA_HOME`, p. ej. `export JAVA_HOME=$(/usr/libexec/java_home -v 17)`), .NET SDK ≥ 8 con runtime 8, Node ≥ 20, GNU Make, git.

## 1. Clon limpio → build → test (criterio de salida, SC-001)

```bash
git clone https://github.com/raalzate/maestria-ia.git && cd maestria-ia
git checkout feature/dev-arch-monorepo
cd apps/dev-arch
make build && make test
git status --porcelain            # vacío (SC-003)
```

## 2. Web con motor local (criterio de salida, SC-002)

```bash
cd apps/dev-arch/web && npm run dev          # o: make dev (desde apps/dev-arch)
# en otra terminal, con un ZIP generado del proyecto demo:
(cd ../engine/test-for-demo && zip -qr /tmp/java-standar.zip java-standar)
curl -s -F file=@/tmp/java-standar.zip http://localhost:9002/api/analyze | jq '.componentsData.meta.schema_version'   # "1.0.0"
```

Sin motor construido (`rm -rf web/engine-dist`): la misma llamada responde 500 con "Engine not built ... make build".

## 3. Historia del motor (SC-004)

```bash
git log --oneline -- apps/dev-arch/engine/src/main/java/com/extractor/service/AnalysisService.java | tail -1
git -C <static-inference> rev-list main | while read c; do git cat-file -e "$c^{commit}" || echo MISSING $c; done   # sin MISSING
```

## 4. Contrato y versión (SC-006, SC-007)

```bash
make schema-check                   # [schema-check] RESULT: OK
make version-check                  # sin divergencias
echo 1.2.1 > VERSION && make version-sync && make version-check && git checkout -- .
```

## 5. Pruebas de aceptación de 006

```bash
make test-acceptance                       # escenarios rápidos
DEVARCH_E2E=true make test-acceptance      # incluye @slow (clon limpio, web + ZIP)
```
