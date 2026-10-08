# Feature Specification: RoslynCodeAnalyzer — .NET Code Issues Detection

**Feature Branch**: `003-dotnet-code-issues`
**Created**: 2026-04-07
**Status**: Draft

## User Stories *(mandatory)*

### User Story 1 — Detectar patrones de bugs en C# (Priority: P1)

Como herramienta de análisis estático, cuando proceso un proyecto .NET/C#, quiero detectar automáticamente patrones de código problemáticos en cada componente y reportarlos en el campo `code_issues`, de modo que los usuarios obtengan la misma calidad de información que ya reciben para proyectos Java.

**Why this priority**: El campo `code_issues` existe en el contrato de salida pero siempre retorna `[]` para proyectos .NET. Esta es la brecha de paridad más visible entre Java y .NET.

**Independent Test**: Se puede verificar ejecutando el análisis sobre un proyecto C# que contenga al menos uno de los patrones detectables y comprobando que `code_issues` contiene entradas con `rule`, `severity`, `message` y `location` no vacíos.

**Acceptance Scenarios**:

1. **Given** un archivo C# con un bloque `catch (Exception e) { }` vacío, **When** se analiza el proyecto, **Then** `code_issues` del componente contiene una entrada con `rule: "EMPTY_CATCH_BLOCK"` y `severity: "WARNING"`
2. **Given** un archivo C# que usa `Console.WriteLine` en vez de un logger, **When** se analiza el proyecto, **Then** `code_issues` contiene una entrada con `rule: "CONSOLE_LOGGING"` y `severity: "INFO"`
3. **Given** un archivo C# que compara strings con `==` sobre variables de tipo `object` o `dynamic`, **When** se analiza el proyecto, **Then** `code_issues` contiene una entrada con `rule: "STRING_EQUALITY_OPERATOR"` y `severity: "WARNING"`
4. **Given** un archivo C# sin ningún patrón problemático, **When** se analiza el proyecto, **Then** `code_issues` del componente es una lista vacía `[]`

---

### User Story 2 — Esquema de CodeIssue unificado y documentado (Priority: P2)

Como integrador del MCP Server, quiero que el modelo `CodeIssue` en .NET tenga un esquema canónico claro y documentado, de modo que pueda construir herramientas que consuman el output de forma predecible independientemente del lenguaje analizado.

**Why this priority**: El modelo .NET actual (`rule/location`) diverge del Java (`type/line/pattern`) sin documentación del porqué. Esto rompe la predictibilidad para consumidores del MCP.

**Independent Test**: El contrato de schema debe definir los campos obligatorios y opcionales de `CodeIssue` con ejemplos JSON para proyectos .NET.

**Acceptance Scenarios**:

1. **Given** el schema documentado, **When** se analiza un proyecto .NET, **Then** el output de `code_issues` cumple exactamente los campos definidos en el schema
2. **Given** el schema documentado, **When** un consumidor valida el output JSON, **Then** no hay campos inesperados ni campos requeridos ausentes
3. **Given** cualquier proyecto .NET analizado, **When** `code_issues` contiene entradas, **Then** cada entrada tiene exactamente los campos `rule`, `severity`, `message` y `location`

---

### User Story 3 — Tolerancia a fallos por archivo (Priority: P3)

Como herramienta que analiza proyectos reales con código heterogéneo, quiero que un error al analizar un archivo individual no interrumpa el análisis del resto del proyecto, de modo que siempre se entregue un resultado parcial con indicación de qué fue omitido.

**Why this priority**: Requisito de la constitución (Principio V: Graceful Degradation). Proyectos reales tienen archivos con syntax errors o constructs no soportados.

**Independent Test**: Se puede verificar introduciendo un archivo C# con syntax error deliberado en un proyecto de prueba y comprobando que el análisis completa con los demás componentes populados y un warning en el output.

**Acceptance Scenarios**:

1. **Given** un proyecto con un archivo C# inparseable, **When** se analiza el proyecto, **Then** el análisis completa exitosamente para los demás componentes y se registra un warning indicando qué archivo fue omitido
2. **Given** un proyecto donde Roslyn no puede resolver la semántica de un símbolo, **When** se detectan patrones, **Then** se reportan solo los patrones que no requieren información semántica y los que sí la requieren son omitidos silenciosamente

---

### Edge Cases

- ¿Qué pasa si un componente tiene cero archivos `.cs` asociados? → `code_issues` retorna `[]`
- ¿Cómo se mapea `location` cuando el patrón ocurre en archivos generados (`.g.cs`)? → Se incluye con la ruta real del archivo
- ¿Qué pasa cuando un mismo patrón ocurre múltiples veces en el mismo archivo? → Se genera una entrada por cada ocurrencia

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema DEBE detectar al menos los siguientes patrones en archivos C#: `EMPTY_CATCH_BLOCK`, `CATCH_GENERIC_EXCEPTION`, `CONSOLE_LOGGING`, `STRING_EQUALITY_OPERATOR`, `HARDCODED_SECRET_IN_CODE`, `DEPRECATED_THREAD_USAGE`, `MISSING_SWITCH_DEFAULT`
- **FR-002**: Cada `code_issue` detectado DEBE incluir los campos: `rule` (identificador del patrón), `severity` (INFO | WARNING | ERROR | CRITICAL), `message` (descripción legible), `location` (archivo y número de línea)
- **FR-003**: El análisis de code issues DEBE ejecutarse como parte del pipeline de análisis de proyectos .NET sin requerir pasos manuales adicionales
- **FR-004**: El campo `code_issues` en el output JSON DEBE ser una lista (vacía o con entradas) para todos los componentes .NET analizados
- **FR-005**: Un fallo al analizar un componente individual NO DEBE abortar el análisis del proyecto completo
- **FR-006**: El contrato de schema (`dependency-graph-schema.md`) DEBE documentar los campos canónicos de `CodeIssue` con ejemplos JSON
- **FR-007**: El output de `code_issues` para proyectos .NET DEBE ser determinístico: la misma entrada siempre produce la misma salida

### Key Entities

- **CodeIssue**: Representa un hallazgo de análisis estático en un componente. Campos: `rule` (identificador del patrón), `severity` (nivel de criticidad), `message` (descripción legible del problema), `location` (referencia al archivo y línea donde ocurre)
- **RoslynCodeAnalyzer**: Componente responsable de recorrer el AST de C# con Roslyn y detectar patrones problemáticos, produciendo instancias de CodeIssue
- **BugPattern**: Regla de detección con identificador único, descripción, severidad predeterminada y lógica de detección sobre nodos del AST

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Al analizar el proyecto de prueba `test-for-demo/dotnet-ecommerce`, el campo `code_issues` contiene al menos 1 hallazgo real (no vacío)
- **SC-002**: Los 7 patrones declarados en FR-001 están implementados y verificados con tests unitarios individuales
- **SC-003**: El output de `code_issues` para la misma entrada es idéntico en 3 ejecuciones consecutivas (determinismo)
- **SC-004**: El análisis completo de un proyecto .NET con code issues tiene un overhead menor a 5 segundos respecto al análisis sin code issues
- **SC-005**: El schema de `CodeIssue` está documentado en `dependency-graph-schema.md` con al menos un ejemplo JSON completo
