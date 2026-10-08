# Research: RoslynCodeAnalyzer — .NET Code Issues Detection

**Feature**: 003-dotnet-code-issues | **Date**: 2026-04-07

## Decisión 1: CSharpSyntaxWalker vs DiagnosticAnalyzer

**Pregunta**: ¿Usar `CSharpSyntaxWalker` (inline) o Roslyn `DiagnosticAnalyzer` (análisis de diagnósticos)?

**Opciones consideradas**:

| Opción | Pros | Contras |
|--------|------|---------|
| `CSharpSyntaxWalker` (visitor inline) | Mismo patrón que Spoon `CtScanner`. Sin dependencias extra. Código en la misma clase. Ya usado por otros analyzers. | Menos "idiomático" para análisis de largo plazo |
| `DiagnosticAnalyzer` (Roslyn API) | Estándar de la industria para analyzers de Roslyn. Reutilizable como paquete NuGet. | Requiere `AnalyzerReference` y pipeline de compilación diferente. Overhead de setup. No sigue el patrón existente del proyecto. |

**Decisión**: `CSharpSyntaxWalker` inline.

**Rationale**: Todos los analyzers existentes (`SecretsDetector`, `MessagingDetector`, `InvocationAnalyzer`) usan el mismo patrón de visitor sobre la `SyntaxTree`. Introducir `DiagnosticAnalyzer` rompería la consistencia arquitectónica (Principio II) y añadiría complejidad sin valor adicional para 7 reglas simples. `CSharpSyntaxWalker` es suficientemente potente y no requiere nuevas dependencias (Principio VI).

---

## Decisión 2: Análisis sintáctico vs semántico

**Pregunta**: ¿Qué patrones requieren `SemanticModel` y cuáles son puramente sintácticos?

**Análisis por regla**:

| Regla | Necesita SemanticModel | Razón |
|-------|----------------------|-------|
| `EMPTY_CATCH_BLOCK` | No | Se puede detectar contando statements en `CatchClause.Block` |
| `CATCH_GENERIC_EXCEPTION` | No | El nombre del tipo es legible en el nodo de sintaxis |
| `CONSOLE_LOGGING` | No | El texto `Console.Write*` es estático en el AST |
| `STRING_EQUALITY_OPERATOR` | Sí | Necesita saber el tipo en tiempo de compilación del operando (ReferenceEquals con string) |
| `HARDCODED_SECRET_IN_CODE` | No | Nombre de variable + string literal son suficientes |
| `DEPRECATED_THREAD_USAGE` | No | `Thread.Abort()` es texto literal en el AST |
| `MISSING_SWITCH_DEFAULT` | No | Se puede contar `DefaultSwitchLabel` en los `SwitchSection`s |

**Decisión**: Implementar reglas sintácticas sin `SemanticModel`. Para `STRING_EQUALITY_OPERATOR`, detectar `ReferenceEquals(a, b)` donde uno de los argumentos involucra una llamada a string method o literal — detección sintáctica aproximada pero safe (sin falsos negativos graves).

**Rationale**: El `SemanticModel` puede fallar cuando la compilación tiene errores (AdhocWorkspace fallback). Usar solo sintaxis garantiza que los patrones funcionan en todos los modos de compilación (MSBuild y AdhocWorkspace). Constitución Principio V: Graceful Degradation.

---

## Decisión 3: Mapeo de `location` en CodeIssue

**Pregunta**: ¿Qué formato usar para el campo `location`?

**Opciones**:

| Formato | Ejemplo | Pros | Contras |
|---------|---------|------|---------|
| `filepath:line` | `Services/OrderService.cs:42` | Legible, navegable en IDEs | Necesita normalizar la ruta |
| Solo `line` (int) | `42` | Simple | Sin contexto del archivo |
| Objeto `{file, line}` | `{"file": "...", "line": 42}` | Más estructurado | Cambiaría el tipo del campo de string? a objeto |

**Decisión**: `filepath:line` como string. Ej: `"Services/OrderService.cs:42"`.

**Rationale**: El campo `location` en `Component.cs` ya es `string?`. Cambiar el tipo a objeto requeriría versionar el schema (Principio I). El formato `file:line` es el estándar de facto para reportes de análisis estático (GCC, ESLint, Checkstyle) y es parseable por consumers sin cambios de schema.

---

## Decisión 4: Asociación componente → archivos fuente

**Pregunta**: ¿Cómo asociar el `SyntaxTree` correcto a cada `Component` durante el análisis?

**Contexto**: `Component.Files` contiene rutas relativas de archivos (ej: `["Services/OrderService.cs"]`). El `Compilation` tiene `SyntaxTree`s con rutas absolutas.

**Decisión**: Comparar `Path.GetFullPath(file)` contra `syntaxTree.FilePath` con normalización de separadores (`/` → `\` en Windows). Construir un diccionario `filePath → SyntaxTree` al inicio del análisis para O(1) lookup.

---

## Decisión 5: Ordenamiento de issues para determinismo

**Pregunta**: ¿Cómo garantizar que `code_issues` produce el mismo orden en todas las ejecuciones?

**Decisión**: Ordenar la lista final de `CodeIssues` por `location` string (que incluye filepath + line number) antes de retornar. Si dos issues tienen el mismo `location`, ordenar por `rule`.

**Rationale**: El filesystem no garantiza orden de archivos. `Path.GetFiles()` puede variar entre OS. Ordenar explícitamente satisface FR-007 y SC-003.

---

## Tessl Tiles

No se identificaron tiles relevantes para este feature. Las APIs de Roslyn están documentadas en Microsoft Docs.
