# Changelog

Formato basado en [Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/); versionado [SemVer](https://semver.org/lang/es/).

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
