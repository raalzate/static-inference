# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/); versionado [SemVer](https://semver.org/lang/es/).

## [Unreleased]

### Añadido
- **Contrato de datos formal y versionado 1.0.0** (feature 005): JSON Schema 2020-12 en `schema/1.0.0/` para `output.json`, `output_architecture.json`, `output_entrypoints.json` y las 5 respuestas MCP; versión en `schema/VERSION`; política y notas de migración en `docs/SCHEMA.md`.
- `meta.schema_version` (`"1.0.0"`) en el grafo de Java y .NET, serializado justo después de `meta.source` (cambio aditivo).
- Objeto `meta` (`{ "schema_version": "1.0.0" }`) como primera clave de `output_architecture.json`, `output_entrypoints.json` y de `api_contracts` en el grafo (aditivo; `analyze_project` y `get_component_metrics` no cambian de forma).
- Advertencia del puente .NET cuando el analizador .NET declara otra versión del contrato (o ninguna); se emite la versión del motor.
- Paquete privado `schema/` (`@static-inference/schema`) con tipos TypeScript y validadores zod generados (`schema/generated/`).
- `make schema-check` (`scripts/schema-check.sh`): verificación única de Java, .NET, tipos generados y coherencia de versión; job CI bloqueante `schema-check`.
- Runner Cucumber JVM para los escenarios `.feature` de la feature 005 (solo scope `test`; sin dependencias nuevas en el fat JAR ni en `dotnet-analyzer`).

## [1.1.0] - 2026-10-07

### Añadido
- Soporte VB.NET legacy (.NET Framework 4.8 desktop): carga de `.vbproj` (MSBuild + fallback Adhoc fuera de Windows), filtro de archivos autogenerados, tipos, invocaciones, DI, mensajería, LOC y capa (feature 004).
- `ProjectTypeDetector` reconoce `*.vbproj` como proyecto .NET.
- Variable `STATIC_INFERENCE_COLLECTED_AT=true` para emitir `meta.collected_at` (opt-in).

### Cambiado (comportamiento)
- **`meta.collected_at` ya no se emite por defecto** (Java y .NET). Salidas repetidas son byte-idénticas (principio IV). Consumidores que lo lean deben tratarlo como opcional o activar el opt-in.
- **Clasificación de capa C#:** una clase cuyo nombre termina en `Controller` pero sin atributos HTTP ni clase base de controlador web se clasifica `Negocio` en lugar de `Controlador` (antes era falso positivo). Aplica a C# y VB.
- Nombres de propuestas deterministas: precedencia fija de palabras de rol (antes variaba entre ejecuciones en clases con dos roles, p. ej. `OrderServiceHandler`).

### Corregido
- Puente Java↔.NET: lectura concurrente de stdout/stderr (sin deadlock) y timeout efectivo.
- `DotNetToolLocator` encuentra `dotnet-analyzer.exe` en Windows.
- Escritura de JSON con UTF-8 explícito (antes dependía del encoding por defecto de la plataforma).

### Infraestructura
- CI: SpotBugs, Checkstyle y `dotnet format` bloqueantes con baseline; tests de corpus VB excluidos en CI (`Category!=Corpus`).
- `obj/` y salidas generadas fuera de git.
- `ALGORITHMS.md` y `CONFIGURATION.md` alineados con el código.

## [1.0.0]
- Versión inicial: análisis Java (Spoon) y .NET C# (Roslyn), CLI y servidor MCP.
