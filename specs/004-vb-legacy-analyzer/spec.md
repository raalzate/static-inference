# Feature Specification: VB.NET Legacy Analyzer — Soporte de proyectos Visual Basic .NET Framework 4.x

**Feature Branch**: `004-vb-legacy-analyzer`
**Created**: 2026-05-08
**Reconstructed**: 2026-10-07 (el directorio original se perdió porque `specs/` está en `.gitignore`; esta versión se reconstruye desde los commits T001–T016, el fix `da3f0cc` y el chore `3627dfb`)
**Status**: Complete (alcance cerrado — ver sección *Deferred*)

## Contexto

El analizador .NET solo entendía proyectos C#. Los sistemas legacy objetivo de la maestría (perfil "AmigosPlus": aplicaciones de escritorio .NET Framework 4.8 escritas en Visual Basic, con decenas de proyectos `.vbproj` en formato pre-SDK) quedaban fuera del alcance: el análisis devolvía grafos vacíos o fallaba. Esta feature habilita el análisis estructural de esos proyectos con el mismo contrato de salida que C#.

**Perfil objetivo**: VB.NET legacy de escritorio sobre .NET Framework 4.x (formularios, módulos, fachadas de negocio con sufijo `Controller`, objetos de acceso a datos con sufijo `Adapter`). **No** es objetivo el VB moderno sobre SDK-style / .NET 6+ ni ASP.NET Core en VB.

## User Stories *(mandatory)*

### User Story 1 — Analizar un proyecto VB.NET legacy (Priority: P1)

Como arquitecto que evalúa un monolito VB.NET legacy, quiero entregar un `.vbproj`, una solución o un directorio que contenga proyectos VB al analizador y obtener el grafo de componentes y dependencias, de modo que pueda aplicar el mismo proceso de descomposición en microservicios que ya uso para Java y C#.

**Why this priority**: Sin carga de proyectos VB no existe ningún valor de la feature; es la brecha que bloquea el uso de la herramienta sobre el sistema legacy objetivo.

**Independent Test**: Analizar un proyecto VB mínimo de fixture y comprobar que el grafo contiene el tipo esperado, con `meta.source` informado.

**Acceptance Scenarios**:

1. **Given** la ruta a un `.vbproj` legacy, **When** se analiza, **Then** el grafo contiene un componente por cada clase, módulo, estructura o interfaz declarada en el proyecto
2. **Given** un directorio que contiene un `.vbproj` (y opcionalmente `.csproj`), **When** se analiza el directorio, **Then** los proyectos se descubren automáticamente y se procesan todos
3. **Given** un directorio raíz con un `.vbproj` entregado al motor principal (CLI/MCP), **When** se detecta el tipo de proyecto, **Then** se clasifica como proyecto .NET y se delega al analizador .NET
4. **Given** cualquier análisis VB, **When** se emite el resultado, **Then** `meta.source` indica si se usó la carga completa o la carga de fidelidad reducida

---

### User Story 2 — Excluir código autogenerado (Priority: P1)

Como usuario del análisis, quiero que los archivos generados por diseñadores (formularios, referencias de servicios, recursos, settings) y los tipos sintetizados por el compilador VB no aparezcan como componentes, de modo que las métricas y el grafo reflejen solo el código escrito por personas.

**Why this priority**: En proyectos de escritorio legacy los archivos de diseñador pueden superar en volumen al código real; incluirlos distorsiona LOC, acoplamiento y propuestas de extracción.

**Independent Test**: Analizar un fixture que contenga un `*.Designer.vb` y comprobar que ningún componente referencia ese archivo.

**Acceptance Scenarios**:

1. **Given** un proyecto con `MainForm.Designer.vb`, **When** se analiza, **Then** ningún componente lista ese archivo en `files`
2. **Given** archivos `Reference.vb`, `Resources.Designer.vb`, `Settings.Designer.vb` o `Application.Designer.vb` (cualquier capitalización, cualquier separador de ruta), **When** se analiza, **Then** se excluyen
3. **Given** un archivo llamado literalmente `Designer.vb` o un `.vb` regular, **When** se analiza, **Then** NO se excluye
4. **Given** un proyecto VB con tipos sintetizados por el compilador (namespace `My`), **When** se analiza, **Then** esos tipos no aparecen como componentes

---

### User Story 3 — Relaciones entre componentes VB (Priority: P2)

Como arquitecto, quiero ver las llamadas entre métodos, las inyecciones por constructor y las implementaciones de interfaces entre componentes VB, de modo que el cálculo de cohesión/acoplamiento y la inferencia de clusters funcionen igual que en C#.

**Why this priority**: Sin aristas el grafo es una lista plana de clases y la inferencia de microservicios no tiene evidencia.

**Independent Test**: Analizar un proyecto VB con una clase que llama a otra y recibe una dependencia por constructor, y comprobar las aristas resultantes.

**Acceptance Scenarios**:

1. **Given** un método VB que invoca un método de otra clase del proyecto, **When** se analiza, **Then** existe una arista de llamada entre ambos componentes
2. **Given** un `Sub New(dep As IServicio)`, **When** se analiza, **Then** existe una arista de inyección hacia el tipo del parámetro si es un componente conocido
3. **Given** una clase VB que `Implements` una interfaz del proyecto, **When** se analiza, **Then** existe la relación de implementación
4. **Given** un proyecto VB sin mensajería (sin MassTransit/Azure Service Bus), **When** se ejecuta la detección de mensajería, **Then** termina sin errores y sin roles espurios

---

### User Story 4 — Métricas de tamaño y capa correctas en VB (Priority: P2)

Como usuario que revisa el resultado sobre el corpus real, quiero que cada componente VB reporte su LOC real y una capa coherente con su función, de modo que las fachadas de negocio no se confundan con controladores HTTP y los objetos de acceso a datos queden en la capa de datos.

**Why this priority**: Defecto visible en la primera ejecución sobre el corpus real (todos los componentes con `loc: 1` y todas las fachadas `*Controller` como "Controlador"); invalida las recomendaciones.

**Independent Test**: Analizar un proyecto `*.Controller` y un proyecto `*.Adapters` del corpus de referencia y verificar distribución de capas y LOC.

**Acceptance Scenarios**:

1. **Given** una clase VB de varias líneas, **When** se analiza, **Then** `loc` cuenta el cuerpo completo excluyendo líneas en blanco y comentarios (`'` y `REM`)
2. **Given** una clase cuyo nombre termina en `Controller` sin ninguna marca HTTP (atributos de ruta/verbo ni herencia de controlador web), **When** se clasifica, **Then** su capa es "Negocio"
3. **Given** una clase cuyo nombre termina en `Adapter`, **When** se clasifica, **Then** su capa es "Datos"
4. **Given** un controlador HTTP real (C#) con atributo de ruta/verbo o que hereda de un controlador web, **When** se clasifica, **Then** su capa sigue siendo "Controlador"

---

### User Story 5 — Degradación controlada fuera de Windows (Priority: P3)

Como desarrollador que trabaja en macOS/Linux, quiero que el análisis de un `.vbproj` legacy complete aunque no tenga el entorno de compilación completo de Windows, con una advertencia clara de la fidelidad reducida, de modo que pueda iterar sin una máquina Windows.

**Why this priority**: Los `.vbproj` pre-SDK requieren el entorno de compilación de Visual Studio para la carga completa; sin fallback el análisis devolvería vacío en cualquier otro sistema operativo.

**Independent Test**: Ejecutar el análisis del fixture VB en macOS y comprobar grafo no vacío, `meta.source = "roslyn-fallback"` y advertencia en stderr.

**Acceptance Scenarios**:

1. **Given** un sistema no Windows, **When** se carga un `.vbproj`, **Then** se emite una advertencia indicando fidelidad reducida
2. **Given** que la carga completa del proyecto produce una compilación vacía, **When** se analiza, **Then** se usa la carga de fidelidad reducida basada en los archivos fuente y el análisis continúa
3. **Given** un archivo `.vb` que no se puede parsear, **When** se analiza, **Then** se emite una advertencia y el resto de archivos se procesa
4. **Given** un directorio vacío o una ruta inexistente, **When** se intenta la carga de fidelidad reducida, **Then** no se lanza excepción

---

### Edge Cases

- Directorio con `.csproj` y `.vbproj` mezclados → se procesan ambos lenguajes
- Directorio con `.sln` → la solución tiene precedencia sobre los proyectos sueltos
- `.vbproj` pre-SDK (formato 2003/ToolsVersion 4.0) sin imports de compilación disponibles → fallback de fidelidad reducida
- Archivo literalmente llamado `Designer.vb` → no se considera autogenerado
- Separadores `\` en rutas al ejecutar en macOS/Linux → la detección de autogenerados funciona igual
- Proyecto VB sin endpoints, tablas ni issues detectables por analizadores aún no portados → campos en valores vacíos válidos del contrato (ver *Deferred*)

## Requirements *(mandatory)*

> Numeración: los IDs FR-020, FR-021, FR-026, FR-030, FR-032, SC-002, SC-004 y SC-008 se conservan porque aparecen citados en los commits y en los tests de la rama. El resto se reconstruye alrededor de ellos; los huecos son intencionales.

### Functional Requirements

**Carga de proyectos**

- **FR-001**: El sistema DEBE aceptar como entrada la ruta a un archivo `.vbproj`
- **FR-002**: El sistema DEBE descubrir automáticamente proyectos `.vbproj` dentro de un directorio de entrada, junto con los `.csproj`, procesando directorios mixtos de extremo a extremo
- **FR-003**: Cuando el directorio contiene una solución, la solución DEBE tener precedencia y sus proyectos VB DEBEN procesarse
- **FR-004**: El sistema DEBE soportar `.vbproj` en formato legacy (pre-SDK, .NET Framework 4.x)
- **FR-005**: Cuando la carga completa de un proyecto VB produce una compilación vacía, el sistema DEBE recurrir a una carga de fidelidad reducida basada en los archivos `.vb` del directorio del proyecto, sin abortar
- **FR-006**: El campo `meta.source` DEBE indicar si el resultado proviene de la carga completa (`roslyn`) o de la carga de fidelidad reducida (`roslyn-fallback`)
- **FR-007**: La detección automática del tipo de proyecto del motor principal DEBE reconocer un directorio con `.vbproj` como proyecto .NET
- **FR-008**: El mensaje de uso del CLI .NET DEBE documentar el soporte de `.vbproj` y el requisito de plataforma para fidelidad completa

**Exclusión de autogenerados**

- **FR-010**: El sistema DEBE excluir de todo el análisis los archivos `*.Designer.vb`, `Reference.vb`, `Resources.Designer.vb`, `Settings.Designer.vb` y `Application.Designer.vb`
- **FR-011**: La exclusión DEBE ser insensible a mayúsculas y reconocer separadores de ruta `/` y `\` en cualquier sistema operativo; un archivo llamado exactamente `Designer.vb` NO DEBE excluirse
- **FR-012**: Los tipos sintetizados por el compilador VB (sin declaración en código fuente) y los tipos declarados únicamente en archivos autogenerados NO DEBEN emitirse como componentes

**Tipos y relaciones**

- **FR-013**: El sistema DEBE emitir un componente por cada clase, módulo, estructura e interfaz VB declarada en código fuente
- **FR-014**: El sistema DEBE emitir aristas de invocación entre componentes VB cuando un método invoca a un miembro de otro componente del proyecto (sin auto-llamadas)
- **FR-015**: El sistema DEBE emitir aristas de inyección por constructor para parámetros de `Sub New` cuyo tipo es un componente conocido
- **FR-016**: El sistema DEBE emitir relaciones de implementación de interfaces para componentes VB
- **FR-017**: La detección de mensajería DEBE ejecutarse sobre compilaciones VB sin errores y sin producir roles espurios cuando no hay mensajería

**Capa y tamaño**

- **FR-020**: Un componente DEBE clasificarse en la capa "Controlador" solo si presenta evidencia HTTP: atributos de controlador/ruta/verbo HTTP o herencia de un tipo base de controlador web
- **FR-021**: Un componente con sufijo `Controller` sin evidencia HTTP DEBE clasificarse como "Negocio"; un componente con sufijo `Adapter` DEBE clasificarse como "Datos"
- **FR-025**: El `loc` de un componente VB DEBE contar el cuerpo completo del tipo, no solo su línea de declaración
- **FR-026**: Las líneas en blanco y los comentarios VB (`'` y `REM`) DEBEN contribuir cero al `loc`

**Contrato, compatibilidad y determinismo**

- **FR-027**: La salida para proyectos VB DEBE cumplir el mismo esquema JSON que la salida C#, sin campos nuevos
- **FR-028**: El análisis de proyectos C# existentes NO DEBE regresionar (misma suite de tests en verde)
- **FR-029**: Todas las listas serializadas (componentes, aristas, endpoints y listas por componente) DEBEN emitirse en orden determinístico y sin duplicados

**Degradación controlada**

- **FR-030**: Un fallo al leer o parsear un archivo `.vb` individual DEBE emitir una advertencia y NO DEBE abortar la carga del resto del proyecto
- **FR-031**: Un directorio sin fuentes VB o una ruta inexistente en la carga de fidelidad reducida NO DEBE lanzar excepción
- **FR-032**: Al cargar un `.vbproj` en un sistema no Windows, el sistema DEBE emitir una advertencia indicando fidelidad reducida
- **FR-033**: Los analizadores aún no portados a VB (ver *Deferred*) NO DEBEN fallar sobre compilaciones VB; sus campos quedan en los valores vacíos/por defecto que permite el contrato

### Key Entities

- **Proyecto VB legacy**: unidad de entrada (`.vbproj`) con su conjunto de archivos fuente `.vb`; puede formar parte de una solución o de un directorio mixto
- **Archivo autogenerado**: archivo fuente producido por un diseñador o herramienta (formularios, referencias de servicio, recursos, settings) que no representa código escrito por personas
- **Componente VB**: clase, módulo, estructura o interfaz declarada en código fuente; mismo modelo de componente que C#
- **Modo de carga**: carga completa (entorno de compilación disponible) o carga de fidelidad reducida (solo archivos fuente); se refleja en `meta.source`
- **Corpus de referencia**: conjunto externo de 48 proyectos `.vbproj` legacy con perfil AmigosPlus usado para validación; no redistribuible

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El proyecto VB mínimo de fixture produce un grafo no vacío que contiene el tipo esperado y ningún archivo de diseñador
- **SC-002**: En el proyecto `*.Model.Common.Adapters` del corpus de referencia, al menos el 80% de los componentes `*Adapter` quedan en la capa "Datos" (observado: 127/130, 98%)
- **SC-003**: En el proyecto `*.Accounting.Controller` del corpus de referencia, más de la mitad de los componentes reportan `loc > 1`
- **SC-004**: En los proyectos `*.Controller` del corpus de referencia hay cero componentes clasificados como "Controlador" (observado: 22/22 en "Negocio")
- **SC-005**: La suite de tests C# preexistente (97 tests) permanece 100% en verde; la suite completa al cierre es 146/146 (144 sin la categoría `Corpus`)
- **SC-006**: En macOS/Linux sin entorno de compilación Windows, el análisis del fixture VB completa con `meta.source = "roslyn-fallback"` y la advertencia de plataforma
- **SC-007**: La detección de autogenerados acierta en el 100% de los casos de la matriz de pruebas (23 casos positivos/negativos)
- **SC-008**: Dos ejecuciones consecutivas sobre la misma entrada VB producen listas de componentes y aristas idénticas (excluyendo `meta.collected_at`)

## Assumptions

- La fidelidad completa (resolución semántica de referencias a ensamblados de .NET Framework) requiere Windows con el entorno de compilación de Visual Studio y la carga de trabajo VB. Fuera de Windows se acepta resolución semántica reducida.
- El corpus de referencia `test-for-demo/vb-demo-legacy/` es externo y no redistribuible; los criterios SC-002, SC-003 y SC-004 se verificaron localmente el 2026-05-08 y sus tests pertenecen a la categoría opt-in `Corpus`.
- Los proyectos del perfil objetivo no usan inyección de dependencias de ASP.NET Core ni brokers de mensajería; por eso la detección de registros de contenedor DI permanece solo C#.

## Deferred *(alcance cerrado — se trasladan a la feature 009)*

Alcance cerrado de esta feature: **VB: carga (VbProjectLoader + fallback Adhoc) + filtro de autogenerados + tipos + invocaciones + DI + messaging + LOC + layer.**

Quedan **DIFERIDOS a la feature 009 — "Cierre de VB.NET legacy"** (PLAN-INTEGRACION-DEV-ARCH.md, Fase 4):

| Ítem diferido | Efecto actual sobre proyectos VB | Destino |
|---|---|---|
| Extracción de endpoints en VB (WebForms/ASMX/WCF según corpus) | `endpoints` vacío | 009 |
| Extracción de tablas en VB (ADO.NET, DataSets tipados, SQL literal) | `tables_used` vacío | 009 |
| Métricas de complejidad/acoplamiento en VB | métricas sintácticas en valores por defecto | 009 |
| Detección de secretos en VB y en `app.config`/`web.config` (parser de configuración de aplicación) | `sensitive_data` sin hallazgos de configuración | 009 |
| Detección de code issues VB (`On Error Resume Next`, `Option Strict Off`, `GoTo`, late binding) | `code_issues` vacío | 009 |
| Clasificador de capas con capas de escritorio legacy (Forms, Módulos, DataAccess, Reportes) | solo heurísticas generales + reglas FR-020/FR-021 | 009 |
| Registros de contenedor DI en VB | no aplica al perfil objetivo | 009 (si el corpus lo justifica) |
| Sustituir o volver opt-in el corpus de referencia en CI | tests `Corpus` solo locales | 009 |
| Documentar limitaciones fuera de Windows en `meta.warnings` y `docs/` | solo advertencia en stderr | 009 |

## Clarifications

### Session 2026-10-07 (reconstrucción)

- Q: ¿El cambio de FR-020 afecta a C#? → A: Sí. Una clase C# con sufijo `Controller` sin atributos HTTP ni herencia de controlador web pasa de "Controlador" a "Negocio". Se acepta como corrección de falso positivo; el test C# afectado se invirtió en `da3f0cc`.
- Q: ¿Por qué faltan IDs de tareas entre T016 y T046? → A: El `tasks.md` original (perdido) planificaba fases por historia de usuario para los analizadores ahora diferidos. Ver `tasks.md`.

- 2026-10-07: Cambio de clasificación C# `*Controller` sin atributos HTTP → `Negocio` aceptado por el usuario como corrección versionada (v1.1.0, CHANGELOG "Cambiado"), cumpliendo principio I mediante versionado explícito.
