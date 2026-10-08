# Data Model: RoslynCodeAnalyzer — .NET Code Issues Detection

**Feature**: 003-dotnet-code-issues | **Date**: 2026-04-07

## Entidades

### CodeIssue (existente, sin cambios de schema)

Representa un hallazgo de análisis estático en un componente .NET.

| Campo | Tipo | Obligatorio | Descripción |
|-------|------|-------------|-------------|
| `rule` | string | Sí | Identificador único del patrón. Snake_case en mayúsculas. Ej: `"EMPTY_CATCH_BLOCK"` |
| `severity` | string (enum) | Sí | Nivel de criticidad: `"INFO"` \| `"WARNING"` \| `"ERROR"` \| `"CRITICAL"` |
| `message` | string | Sí | Descripción legible del problema para el desarrollador |
| `location` | string? | Sí (nullable) | Referencia al origen: formato `"path/to/File.cs:42"` (ruta relativa al proyecto + número de línea) |

**Notas**:
- El tipo `CodeIssue` en `Component.cs:109-122` ya tiene esta estructura — no se modifica el modelo
- La serialización JSON usa `JsonPropertyName` existente: `rule`, `severity`, `message`, `location`

### Severidades por patrón

| Regla | Severidad | Justificación |
|-------|-----------|---------------|
| `EMPTY_CATCH_BLOCK` | WARNING | Silencia errores; no siempre crítico pero problemático |
| `CATCH_GENERIC_EXCEPTION` | INFO | Discutible; no siempre un bug |
| `CONSOLE_LOGGING` | INFO | Malo en producción pero no rompe funcionalidad |
| `STRING_EQUALITY_OPERATOR` | WARNING | Posible bug de comparación por referencia |
| `HARDCODED_SECRET_IN_CODE` | CRITICAL | Riesgo de seguridad severo |
| `DEPRECATED_THREAD_USAGE` | ERROR | `Thread.Abort()` lanza `PlatformNotSupportedException` en .NET 6+ |
| `MISSING_SWITCH_DEFAULT` | INFO | Buena práctica, no necesariamente un bug |

### RoslynCodeAnalyzer (nueva clase)

Clase de análisis sin estado (stateless) que acepta una `Compilation` y la lista de `Component`s y mutates `Component.CodeIssues` en-place, igual que `SecretsDetector` y `MessagingDetector`.

**Responsabilidades**:
1. Construir diccionario `filePath → SyntaxTree` para lookup O(1)
2. Por cada `Component`, por cada archivo en `Component.Files`:
   - Obtener el `SyntaxTree` correspondiente
   - Crear un `BugPatternWalker` (inner class o nested class)
   - Visitar el AST, colectando `CodeIssue`s
3. Ordenar `component.CodeIssues` por `location` para determinismo

**No responsabilidades** (fuera de scope):
- No modifica otros campos del `Component`
- No hace análisis semántico complejo (usa sintaxis primero, SemanticModel solo si es imprescindible)
- No duplica lógica de `SecretsDetector` (los secrets hardcodeados son un subconjunto detectado por regex de nombre, no por el detector de configuración)

## Relaciones

```
ProjectAnalyzer
    └── RoslynCodeAnalyzer (Pass 11)
          └── BugPatternWalker : CSharpSyntaxWalker
                └── Component.CodeIssues : List<CodeIssue>
```

## State Transitions

`Component.CodeIssues` empieza como `[]` (inicializado en constructor de `Component`).
Después de Pass 11: `[]` si no se detectan patrones, o `[CodeIssue, ...]` ordenados por location.

El campo es inmutable después de Pass 11 — ningún pass posterior lo modifica.
