# CodeIssue Schema Contract

Este documento define el schema canónico del campo `code_issues` en el output JSON del analizador .NET.

## Schema de CodeIssue

```json
{
  "rule": "string (identificador del patrón, SCREAMING_SNAKE_CASE)",
  "severity": "string (INFO | WARNING | ERROR | CRITICAL)",
  "message": "string (descripción legible del problema)",
  "location": "string | null (formato: 'path/to/File.cs:42')"
}
```

## Reglas de validación

1. `rule` DEBE ser uno de los valores definidos en la tabla de patrones (ver abajo)
2. `severity` DEBE ser uno de: `"INFO"`, `"WARNING"`, `"ERROR"`, `"CRITICAL"`
3. `message` NO DEBE estar vacío
4. `location` usa ruta relativa al directorio del proyecto + número de línea 1-based separados por `:`

## Patrones implementados

| rule | severity | Descripción |
|------|----------|-------------|
| `EMPTY_CATCH_BLOCK` | WARNING | Bloque catch vacío que silencia excepciones |
| `CATCH_GENERIC_EXCEPTION` | INFO | Catch de `Exception` o `SystemException` sin tipo específico |
| `CONSOLE_LOGGING` | INFO | Uso de `Console.Write` o `Console.WriteLine` en lugar de un logger |
| `STRING_EQUALITY_OPERATOR` | WARNING | Uso de `ReferenceEquals` para comparar strings (siempre es un bug) |
| `HARDCODED_SECRET_IN_CODE` | CRITICAL | Literal string asignado a variable con nombre que sugiere secreto (password, apikey, secret, token) |
| `DEPRECATED_THREAD_USAGE` | ERROR | Llamada a `Thread.Abort()` removido en .NET 6+ |
| `MISSING_SWITCH_DEFAULT` | INFO | Switch statement sin sección `default` |

## Ejemplo de output JSON

```json
{
  "id": "SampleProject.Services.OrderService",
  "code_issues": [
    {
      "rule": "EMPTY_CATCH_BLOCK",
      "severity": "WARNING",
      "message": "Empty catch block detected. Exceptions should not be swallowed.",
      "location": "Services/OrderService.cs:45"
    },
    {
      "rule": "CONSOLE_LOGGING",
      "severity": "INFO",
      "message": "Direct use of Console.Write/WriteLine. Use a logger (ILogger) instead.",
      "location": "Services/OrderService.cs:78"
    }
  ]
}
```

## Ejemplo de componente sin issues

```json
{
  "id": "SampleProject.Models.Order",
  "code_issues": []
}
```

## Relación con el schema global

Este schema extiende la definición de `CodeIssue` en `specs/002-dotnet-roslyn-analyzer/contracts/dependency-graph-schema.md`. La línea `"code_issues": [CodeIssue]` en el schema raíz referencia esta definición.
