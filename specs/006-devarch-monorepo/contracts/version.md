# Contract: versión única del producto

Fuente: `apps/dev-arch/VERSION` — una línea `^[0-9]+\.[0-9]+\.[0-9]+$`, inicial `1.2.0`.

`scripts/version.sh sync` escribe la versión en:

| Archivo | Ubicación |
|---------|-----------|
| `engine/pom.xml` | `/project/version` (solo el del proyecto, nunca dependencias/plugins) |
| `engine/src/main/dotnet/DotNetAnalyzer/DotNetAnalyzer.csproj` | `<PropertyGroup><Version>` (se crea si falta) |
| `web/package.json` | `.version` |
| `package.json` (producto) | `.version` |

Derivados (sin edición manual): `McpServerMain` reporta `serverInfo.version` desde `static-inference-version.properties` (`version=${project.version}`, filtrado por Maven); `dotnet-analyzer` hereda `<Version>` en su versión de ensamblado.

`scripts/version.sh check`: exit 0 si las cuatro declaraciones = `VERSION`; si no, una línea `[version] <archivo>: <declarada> != <VERSION>` por divergencia y exit 1. No lee ni escribe `schema/VERSION` (versión de contrato, independiente).
