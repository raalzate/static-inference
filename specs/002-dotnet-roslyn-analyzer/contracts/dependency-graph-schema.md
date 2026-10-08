# DependencyGraph JSON Schema Contract

> **OBSOLETO desde 005-json-schema-contract (2026-10-07).** Este documento ya no es normativo. La **fuente de verdad** del contrato de salida es `schema/1.0.0/` (JSON Schema 2020-12; versión en `schema/VERSION`; política y notas de migración en `docs/SCHEMA.md`). Divergencias conocidas de este texto frente al output real, resueltas en el contrato formal: `code_issues[*]` = `{type, severity, message, line, pattern}` (no `rule`/`location`); `meta.source` ∈ `spoon` \| `roslyn` \| `roslyn-fallback`; `meta.schema_version` obligatorio; `project_metadata.components_with_secrets` es entero, `secrets_locations` es lista de `{path, line, kind, redacted}` y `shared_domain` es texto; `proposals[*].tables` es lista de `{name, component, source}`; `edges[*].type` puede ser compuesto (`call,use,injection-constructor`); `complexity_avg`/`complexity_max` existen; `interface` está obsoleto (usar `is_interface`).

This is the shared contract between the Java analyzer (Spoon) and the .NET analyzer (Roslyn). Both MUST produce JSON conforming to this schema.

## Root Object

```json
{
  "components": [Component],
  "edges": [Edge],
  "api_contracts": ApiContracts,
  "meta": Meta
}
```

## Component

```json
{
  "id": "string (fully qualified type name)",
  "files": ["string (relative file path)"],
  "loc": "integer (lines of code)",
  "tables_used": ["string (table name)"],
  "sensitive_data": "boolean",
  "domain": "string | null",
  "layer": "string (Controlador|Negocio|Datos|Persistencia|Dominio|Transferencia|Web|Compartida)",
  "calls_out": ["string (target component id)"],
  "calls_in": ["string (source component id)"],
  "ejb_type": "string | null (Java-specific, null for .NET)",
  "uses_jndi": "boolean (Java-specific, false for .NET)",
  "annotations": ["string (annotation/attribute name without @/[])"],
  "is_interface": "boolean",
  "secrets_references": ["string"],
  "external_dependencies": ["string (package/NuGet name)"],
  "package_dependencies": [PackageGroup],
  "code_issues": [CodeIssue],  // see CodeIssue definition below
  "extends": "string | null (parent class)",
  "implements": ["string (interface name)"],
  "messaging_type": "string | null",
  "messaging_role": "string | null",
  "web_type": "string | null",
  "web_role": "string | null",
  "cbo": "integer | null (Coupling Between Objects)",
  "lcom": "number | null (Lack of Cohesion 0.0-1.0)"
}
```

## Edge

```json
{
  "from": "string (source component id)",
  "to": "string (target component id)",
  "weight": "number",
  "type": "string"
}
```

## ApiContracts

```json
{
  "endpoints": [ApiEndpoint],
  "schemas": {"string": ApiSchema}
}
```

## ApiEndpoint

```json
{
  "method": "string (GET|POST|PUT|DELETE|PATCH)",
  "path": "string (route template)",
  "controller": "string (controller class name)",
  "operationId": "string | null",
  "parameters": [Parameter],
  "requestBody": "string | null (type name)",
  "responseType": "string | null (type name)"
}
```

## Meta

```json
{
  "source": "string (spoon|roslyn)",
  "collected_at": "string (ISO 8601 timestamp)",
  "microservice_candidates": {"string": "string"},
  "dependency_accuracy": "object | null",
  "decomposition_accuracy": "object | null"
}
```

## CodeIssue

```json
{
  "rule": "string (pattern identifier, SCREAMING_SNAKE_CASE, e.g. EMPTY_CATCH_BLOCK)",
  "severity": "string (INFO | WARNING | ERROR | CRITICAL)",
  "message": "string (human-readable description of the issue)",
  "location": "string | null (format: 'path/to/File.cs:42' — relative path + 1-based line number)"
}
```

### Defined Rules

| rule | severity | Description |
|------|----------|-------------|
| `EMPTY_CATCH_BLOCK` | WARNING | Empty catch block that swallows exceptions |
| `CATCH_GENERIC_EXCEPTION` | INFO | Catch of `Exception` or `SystemException` without a specific type |
| `CONSOLE_LOGGING` | INFO | Use of `Console.Write` or `Console.WriteLine` instead of a logger |
| `STRING_EQUALITY_OPERATOR` | WARNING | Use of `ReferenceEquals` to compare strings |
| `HARDCODED_SECRET_IN_CODE` | CRITICAL | String literal assigned to a variable whose name suggests a secret |
| `DEPRECATED_THREAD_USAGE` | ERROR | Call to `Thread.Abort()` removed in .NET 6+ |
| `MISSING_SWITCH_DEFAULT` | INFO | Switch statement without a `default` section |

### Example

```json
{
  "code_issues": [
    {
      "rule": "EMPTY_CATCH_BLOCK",
      "severity": "WARNING",
      "message": "Empty catch block detected. Exceptions should not be swallowed.",
      "location": "Services/OrderService.cs:45"
    }
  ]
}
```

## Validation Rules

1. All `id` fields MUST be fully qualified type names
2. `calls_out` and `calls_in` MUST reference valid component `id` values
3. `edges[].from` and `edges[].to` MUST reference valid component `id` values
4. `layer` MUST be one of the defined layer values
5. `meta.source` MUST be `"spoon"` for Java analysis or `"roslyn"` for .NET analysis
6. JSON property names MUST use snake_case (matching existing Java Jackson annotations)
