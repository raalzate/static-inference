# Feature Specification: Contrato de datos único y versionado

**Feature Branch**: `005-json-schema-contract`
**Created**: 2026-10-07
**Status**: Draft
**Input**: User description: "Fase 1 de PLAN-INTEGRACION-DEV-ARCH.md — Contrato de datos único (`schema/`): esquema formal de los tres artefactos (`output.json`, `output_architecture.json`, `output_entrypoints.json`) y de las 5 respuestas MCP; `meta.schema_version` obligatorio en las salidas de los analizadores Java y .NET; tests de contrato en ambos analizadores y en la paridad CLI/MCP; generación automática de tipos y validadores para el consumidor web; política de versionado documentada; verificación `schema-check` que falla si Java, .NET o los tipos generados divergen del contrato."

## Contexto

Static Inference emite tres artefactos JSON (grafo de dependencias, arquitectura consolidada y puntos de entrada) por dos caminos (CLI y servidor MCP) y desde dos analizadores (Java y .NET). La Constitución (principio I) los declara **contratos públicos**, pero hoy el contrato solo existe como prosa repartida en varios documentos que ya divergen del output real. Durante el levantamiento (2026-10-07) se compararon las salidas reales de `test-for-demo/java-standar` y `test-for-demo/dotnet-ecommerce` con la documentación y se encontraron, entre otras, estas divergencias:

- `code_issues[*]` se emite como `{type, severity, message, line, pattern}`; los contratos de las features 002/003 documentan `{rule, severity, message, location}`.
- `meta.source` toma los valores `spoon`, `roslyn` y `roslyn-fallback`; la referencia de la skill documenta `dotnet`.
- `project_metadata.components_with_secrets` es un número; la referencia lo documenta como lista. `secrets_locations` es una lista de objetos `{path, line, kind, redacted}`; la referencia lo documenta como mapa. `shared_domain` es texto, no objeto.
- `proposals[*].tables` es una lista de objetos `{name, component, source}`, no una lista de textos.
- `edges[*].type` puede ser compuesto (p. ej. `call,use,injection-constructor`).
- `components[*]` emite campos no documentados (`complexity_avg`, `complexity_max`) y el indicador de interfaz duplicado (`interface` e `is_interface`).
- Ninguna salida declara a qué versión del contrato pertenece.

La futura aplicación web (Fase 2 del plan) necesita consumir estos artefactos de forma tipada; si el contrato no es formal, verificable y versionado, cada consumidor reinterpretará la prosa y las divergencias seguirán apareciendo.

## User Stories *(mandatory)*

### User Story 1 — Contrato formal de los tres artefactos (Priority: P1)

Como integrador que consume los artefactos de Static Inference (agente LLM, aplicación web o script), quiero un contrato formal y legible por máquina para el grafo de dependencias, la arquitectura consolidada y los puntos de entrada, que describa fielmente lo que el motor emite hoy, de modo que pueda validar cualquier salida sin leer el código fuente del motor.

**Why this priority**: Es la base de todo lo demás: sin el contrato no hay nada contra qué validar ni de dónde generar tipos.

**Independent Test**: Tomar las salidas reales de los proyectos de demostración Java y .NET y validarlas contra el contrato: todas deben ser aceptadas; una salida alterada (campo requerido eliminado, tipo cambiado, valor fuera de catálogo) debe ser rechazada con un mensaje que indique la ruta del campo.

**Acceptance Scenarios**:

1. **Given** el grafo de dependencias producido por el analizador Java sobre un proyecto de demostración, **When** se valida contra el contrato del grafo, **Then** la validación es exitosa sin errores
2. **Given** el grafo de dependencias producido por el analizador .NET sobre un proyecto de demostración, **When** se valida contra el contrato del grafo, **Then** la validación es exitosa sin errores
3. **Given** la arquitectura consolidada y los puntos de entrada producidos para el mismo proyecto, **When** se validan contra sus contratos respectivos, **Then** ambas validaciones son exitosas
4. **Given** un grafo válido al que se le elimina un campo obligatorio de un componente (p. ej. `id`), **When** se valida, **Then** la validación falla e indica la ruta del componente y el campo faltante
5. **Given** un grafo válido con un componente cuya `layer` no pertenece al catálogo de capas, **When** se valida, **Then** la validación falla indicando el valor no permitido
6. **Given** un grafo válido con un campo no declarado en un objeto de estructura cerrada, **When** se valida, **Then** la validación falla indicando el campo desconocido

---

### User Story 2 — Versión del contrato en cada salida (Priority: P1)

Como consumidor de los artefactos, quiero que cada salida declare la versión del contrato con la que fue producida, de modo que pueda detectar incompatibilidades antes de interpretar los datos y aplicar migraciones cuando cambie la versión.

**Why this priority**: Sin versión declarada no es posible evolucionar el contrato sin romper silenciosamente a los consumidores (principio I).

**Independent Test**: Ejecutar el análisis de un proyecto Java y de uno .NET y comprobar que los tres artefactos declaran `meta.schema_version` = `1.0.0` y que dos ejecuciones consecutivas siguen produciendo salidas byte-idénticas.

**Acceptance Scenarios**:

1. **Given** cualquier análisis Java, **When** se generan los artefactos, **Then** cada uno de los tres artefactos contiene `meta.schema_version` con el valor `1.0.0`
2. **Given** cualquier análisis .NET (C# o VB), **When** el analizador .NET emite el grafo, **Then** el grafo contiene `meta.schema_version` con el valor `1.0.0` antes de cualquier reprocesamiento por el motor principal
3. **Given** una salida sin `meta.schema_version` o con un valor que no es una versión semántica, **When** se valida contra el contrato, **Then** la validación falla
4. **Given** dos ejecuciones consecutivas sobre el mismo proyecto sin activar la marca de tiempo opcional, **When** se comparan las salidas, **Then** son byte-idénticas (principio IV)
5. **Given** la marca de tiempo opcional activada, **When** se valida la salida, **Then** `meta.collected_at` es aceptado si es una fecha-hora ISO 8601 y su ausencia también es aceptada

---

### User Story 3 — Contrato de las respuestas de las herramientas MCP (Priority: P1)

Como agente LLM que invoca las herramientas MCP, quiero que las respuestas exitosas de `analyze_project`, `get_dependency_graph`, `get_architecture_proposal`, `get_api_contracts` y `get_component_metrics` estén descritas por el mismo contrato que los artefactos de disco, de modo que el modo CLI y el modo MCP sean verificablemente equivalentes.

**Why this priority**: El principio I exige que CLI y MCP produzcan lo mismo; el principio IV exige que las respuestas MCP coincidan con esquemas documentados. Hoy esa equivalencia solo se prueba por comparación, no contra un contrato.

**Independent Test**: Invocar las 5 herramientas sobre un proyecto de demostración y validar cada respuesta exitosa contra su definición en el contrato; la prueba de paridad CLI/MCP valida además ambas salidas.

**Acceptance Scenarios**:

1. **Given** una invocación exitosa de `get_dependency_graph`, **When** se valida la respuesta, **Then** cumple el contrato del grafo de dependencias
2. **Given** una invocación exitosa de `get_architecture_proposal` (con y sin filtro de viabilidad), **When** se valida la respuesta, **Then** cumple el contrato de la arquitectura consolidada
3. **Given** una invocación exitosa de `get_api_contracts`, **When** se valida la respuesta, **Then** cumple el contrato de los puntos de entrada
4. **Given** una invocación exitosa de `get_component_metrics`, **When** se valida la respuesta, **Then** cumple la definición de componente del contrato del grafo
5. **Given** una invocación exitosa de `analyze_project`, **When** se valida la respuesta, **Then** cumple el contrato del resumen de análisis (conteos, archivos generados y resumen)
6. **Given** el mismo proyecto analizado por CLI y por MCP, **When** se ejecuta la verificación de paridad, **Then** ambas salidas son válidas contra el contrato y además equivalentes entre sí

---

### User Story 4 — Tipos y validadores generados para consumidores tipados (Priority: P2)

Como desarrollador de la futura aplicación web, quiero tipos y validadores en tiempo de ejecución generados automáticamente desde el contrato, de modo que no tenga que mantener tipos escritos a mano que se desactualizan (deuda 1.2-5 del plan).

**Why this priority**: Habilita la Fase 2 (monorepo) y elimina la principal fuente de divergencia del consumidor web, pero no bloquea la validación de los productores.

**Independent Test**: Ejecutar la generación desde el contrato, validar con los validadores generados las salidas reales de demostración (aceptadas) y una salida alterada (rechazada); regenerar sin cambios en el contrato no produce diferencias.

**Acceptance Scenarios**:

1. **Given** el contrato en su versión actual, **When** se ejecuta la generación, **Then** se producen definiciones de tipos y validadores para los tres artefactos y las 5 respuestas MCP
2. **Given** las salidas reales de demostración Java y .NET, **When** se validan con los validadores generados, **Then** son aceptadas
3. **Given** una salida alterada que viola el contrato, **When** se valida con los validadores generados, **Then** es rechazada
4. **Given** el contrato sin cambios, **When** se regenera, **Then** los artefactos generados son idénticos a los existentes (generación determinista)
5. **Given** un cambio en el contrato sin regenerar, **When** se ejecuta la verificación global, **Then** falla indicando que los tipos generados están desactualizados

---

### User Story 5 — Verificación única de divergencia (`schema-check`) (Priority: P2)

Como mantenedor, quiero un único punto de entrada que verifique de una vez que el analizador Java, el analizador .NET y los tipos generados están alineados con el contrato, de modo que cualquier divergencia se detecte antes de integrar un cambio.

**Why this priority**: Es el criterio de salida de la Fase 1 del plan; convierte el contrato en una garantía y no solo en documentación.

**Independent Test**: Ejecutar la verificación en el estado alineado (pasa); introducir de forma controlada una divergencia en cada uno de los tres frentes (Java, .NET, tipos generados) y comprobar que la verificación falla en cada caso señalando el frente que diverge.

**Acceptance Scenarios**:

1. **Given** Java, .NET y los tipos generados alineados con el contrato, **When** se ejecuta la verificación, **Then** termina con éxito
2. **Given** una salida Java que no cumple el contrato, **When** se ejecuta la verificación, **Then** falla e identifica el frente Java y el campo divergente
3. **Given** una salida .NET que no cumple el contrato, **When** se ejecuta la verificación, **Then** falla e identifica el frente .NET y el campo divergente
4. **Given** tipos generados desactualizados respecto del contrato, **When** se ejecuta la verificación, **Then** falla e identifica el frente de tipos generados
5. **Given** una salida que declara una `schema_version` distinta de la versión del contrato vigente, **When** se ejecuta la verificación, **Then** falla indicando ambas versiones
6. **Given** un entorno sin el SDK .NET disponible, **When** se ejecuta la verificación, **Then** informa explícitamente que el frente .NET no se verificó y termina con fallo (no se reporta éxito parcial como éxito)

---

### User Story 6 — Política de versionado y documentación alineada (Priority: P3)

Como mantenedor o consumidor, quiero una política de versionado escrita y una documentación de referencia que coincida con el contrato formal, de modo que sepa qué cambios son compatibles, cuándo subir la versión y cómo migrar.

**Why this priority**: Cierra el ciclo de gobernanza del contrato (principio I y "Development Workflow"), pero el valor técnico principal ya lo entregan las historias anteriores.

**Independent Test**: Revisar el documento de política y comprobar que define reglas de cambio mayor/menor/parche con ejemplos y formato de nota de migración; comprobar que las referencias de documentación existentes ya no contradicen el contrato en los puntos listados en Contexto.

**Acceptance Scenarios**:

1. **Given** el documento de política de versionado, **When** un mantenedor planea eliminar o renombrar un campo, **Then** el documento indica que es un cambio mayor y exige nota de migración
2. **Given** el documento de política, **When** un mantenedor planea añadir un campo opcional, **Then** el documento indica que es un cambio menor
3. **Given** las referencias de documentación del contrato (contratos de features previas y referencias de las skills de agente), **When** se comparan con el contrato formal, **Then** no contradicen ninguna de las divergencias listadas en Contexto o remiten al contrato formal como fuente de verdad

---

### Edge Cases

- **Campos con valores abiertos**: el método de un punto de entrada (`GET`, `POST`, ... y tipos no HTTP como `STRUTS_ACTION`, `LISTENER`), el tipo de arista (incluidos los compuestos separados por coma) y el identificador de patrón de un `code_issue` son conjuntos abiertos; el contrato valida tipo y formato pero no un catálogo cerrado.
- **Mapas con claves dinámicas**: `api_contracts.schemas`, `project_metadata.external_dependencies`, `project_metadata.package_dependencies` y `meta.microservice_candidates` tienen claves arbitrarias; el contrato valida la forma de los valores, no los nombres de las claves.
- **Indicador de interfaz duplicado**: `interface` e `is_interface` se siguen emitiendo ambos (compatibilidad); el contrato declara `interface` como obsoleto.
- **Grafo vacío**: un proyecto sin componentes produce listas vacías válidas y sigue declarando `schema_version`.
- **Fidelidad reducida .NET**: `meta.source = roslyn-fallback` es un valor válido.
- **Arquitectura sin propuestas o sin secretos**: `proposals` vacío, `project_metadata` sin `secrets_locations` o con `{}` es válido.
- **`legacy_entrypoint` nulo o de tipo `internal`**: válido, con los campos opcionales nulos/vacíos.
- **Respuestas de error MCP**: no forman parte del contrato de datos (son mensajes de texto con indicador de error, ya cubiertos por la feature 001).
- **Proyecto VB legacy**: produce el mismo contrato de grafo que C#; no se introducen campos específicos de VB.
- **Integridad referencial**: que `edges[*].from/to`, `calls_in/out` o `component_id` apunten a componentes existentes no se valida en esta feature; el contrato es estructural.
- **Contrato y salida de versión mayor distinta**: un consumidor que valida una salida con otra versión mayor debe obtener un rechazo explícito por versión, no un error de campo arbitrario.

## Requirements *(mandatory)*

### Functional Requirements

**Contrato formal**

- **FR-001**: El proyecto MUST disponer de un contrato formal, legible por máquina y expresado en un estándar abierto de esquemas, para cada uno de los tres artefactos: grafo de dependencias, arquitectura consolidada y puntos de entrada.
- **FR-002**: El contrato MUST describir las respuestas exitosas de las 5 herramientas MCP (`analyze_project`, `get_dependency_graph`, `get_architecture_proposal`, `get_api_contracts`, `get_component_metrics`), reutilizando las definiciones de los artefactos cuando la respuesta sea un artefacto o una parte de él (sin definiciones duplicadas).
- **FR-003**: La versión 1.0.0 del contrato MUST describir el output que el motor emite hoy (estado del tag v1.1.0) y no el de la documentación en prosa; donde ambos difieran, prevalece el output real y la documentación se corrige (FR-020).
- **FR-004**: El contrato MUST rechazar campos no declarados en los objetos de estructura conocida (componente, arista, meta, propuesta, métricas, señales, punto de entrada, parámetro, `legacy_entrypoint`, `code_issue`, ubicación de secreto, tabla de propuesta, librería de soporte), y MUST aceptar claves arbitrarias en los mapas listados en Edge Cases.
- **FR-005**: El contrato MUST restringir a un catálogo cerrado los campos cuyo dominio es cerrado: capa (`Controlador`, `Negocio`, `Datos`, `Persistencia`, `Dominio`, `Transferencia`, `Web`, `Compartida`), severidad de `code_issue` (`INFO`, `WARNING`, `ERROR`, `CRITICAL`), tipo de `code_issue` (`BUG_PATTERN`, `CODE_STYLE`, `SECURITY`, `PERFORMANCE`), viabilidad (`Alta`, `Media`, `Baja`), tipo de `legacy_entrypoint` (`rest`, `messaging`, `service`, `internal`) y `meta.source` (`spoon`, `roslyn`, `roslyn-fallback`).
- **FR-006**: El contrato MUST declarar como obligatorios los campos que el motor emite siempre y como anulables los que pueden ser nulos, de acuerdo con el output real de ambos analizadores.
- **FR-007**: El contrato MUST marcar como obsoleto el campo duplicado `interface` del componente, manteniéndolo aceptado.

**Versión del contrato en las salidas**

- **FR-008**: Cada uno de los tres artefactos MUST contener `meta.schema_version` con la versión semántica del contrato con que fue producido; para esta feature el valor MUST ser `1.0.0`. Los artefactos que hoy no tienen objeto `meta` (arquitectura consolidada y puntos de entrada) MUST incorporarlo con al menos `schema_version` (cambio aditivo).
- **FR-009**: El analizador .NET MUST emitir `meta.schema_version` en el grafo que entrega al motor principal, con el mismo valor que el analizador Java.
- **FR-010**: `meta.collected_at` MUST seguir siendo opcional (opt-in según CHANGELOG 1.1.0); cuando esté presente MUST ser una fecha-hora ISO 8601. Su ausencia por defecto MUST preservar salidas byte-idénticas entre ejecuciones (principio IV).
- **FR-011**: Añadir `schema_version` MUST NOT alterar ningún otro campo, orden de serialización ni valor de las salidas existentes (principio I).
- **FR-022**: Si el analizador .NET entrega un grafo sin versión o con una versión distinta de la del motor principal, el motor MUST registrar una advertencia con ambas versiones (principio V) y MUST emitir en sus artefactos la versión del motor principal.

**Verificación**

- **FR-012**: Las pruebas automatizadas del analizador Java MUST validar los tres artefactos producidos sobre los proyectos de demostración Java contra el contrato.
- **FR-013**: Las pruebas automatizadas del analizador .NET MUST validar el grafo emitido por el analizador .NET (C# y, cuando el corpus esté disponible, VB) contra el contrato.
- **FR-014**: La prueba de paridad CLI/MCP MUST validar contra el contrato tanto la salida CLI como las respuestas de las 5 herramientas MCP, además de comprobar su equivalencia.
- **FR-015**: El proyecto MUST ofrecer un único punto de entrada de verificación (`schema-check`) que falle con código distinto de cero si el analizador Java, el analizador .NET o los tipos generados divergen del contrato, identificando el frente y el campo divergente; si algún frente no puede verificarse por falta de herramientas, MUST fallar y decirlo explícitamente.
- **FR-016**: La verificación MUST comprobar que la `schema_version` emitida por cada productor es igual a la versión declarada por el contrato vigente.

**Consumidores tipados**

- **FR-017**: El proyecto MUST generar automáticamente, desde el contrato, definiciones de tipos y validadores en tiempo de ejecución para los tres artefactos y las 5 respuestas MCP, destinados al consumidor web; los tipos MUST NOT editarse a mano.
- **FR-018**: La generación MUST ser determinista: regenerar sin cambios en el contrato MUST producir artefactos idénticos.

**Documentación y gobernanza**

- **FR-019**: El proyecto MUST documentar la política de versionado del contrato: versión semántica; cambio mayor para cualquier modificación que pueda hacer que un consumidor que validaba la versión anterior rechace o malinterprete una salida (eliminar/renombrar campo, cambiar tipo, añadir valor a un catálogo cerrado, volver obligatorio un campo de entrada); cambio menor para añadir campos opcionales; parche para aclaraciones sin efecto en la validación. Todo cambio mayor MUST incluir nota de migración.
- **FR-020**: Las referencias de documentación existentes del contrato (contratos de las features 001/002/003 y referencias `json-schema` de las skills de agente Claude y Kiro) MUST alinearse con el contrato formal en las divergencias listadas en Contexto, o remitir explícitamente al contrato formal como fuente de verdad.
- **FR-021**: La incorporación del contrato MUST NOT añadir dependencias de ejecución al artefacto desplegable del motor ni requisitos de infraestructura en tiempo de ejecución (principio VI); las herramientas de validación y generación son solo de desarrollo y prueba.

### Key Entities

- **Contrato (Schema)**: conjunto versionado de definiciones formales; tiene una versión semántica única que aplica a todos los artefactos y respuestas.
- **Grafo de dependencias (DependencyGraph)**: componentes, aristas, contratos de API embebidos y meta (fuente, versión del contrato, marca de tiempo opcional, métricas de precisión opcionales).
- **Componente (Component)**: unidad de análisis (clase/tipo) con archivos, LOC, capa, tablas, llamadas entrantes/salientes, dependencias, `code_issues`, métricas de acoplamiento/cohesión/complejidad y roles web/mensajería.
- **Arquitectura consolidada (ConsolidatedArchitecture)**: propuestas de microservicio con métricas, señales, viabilidad, tablas y punto de entrada legado; metadatos del proyecto; librerías de soporte; resumen.
- **Puntos de entrada (ApiContracts)**: lista de endpoints con parámetros y esquemas de petición/respuesta, y mapa de esquemas de datos referenciados.
- **Respuesta MCP**: resultado exitoso de cada herramienta; cuatro reutilizan las entidades anteriores y `analyze_project` devuelve un resumen de análisis.
- **Política de versionado**: reglas que asignan a cada tipo de cambio un incremento mayor/menor/parche y exigen nota de migración.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100% de las salidas generadas sobre los proyectos de demostración disponibles en el repositorio (Java: `java-standar`, `jee-architecture-master`, `legacy-erp`; .NET: `dotnet-ecommerce`, `dotnet-legacy-clinic`, `dotnet-unstructured`; 3 artefactos por proyecto) se validan contra el contrato sin errores.
- **SC-002**: El 100% de las respuestas exitosas de las 5 herramientas MCP sobre un proyecto de demostración se validan contra el contrato.
- **SC-003**: El 100% de los artefactos emitidos por Java y .NET declaran `meta.schema_version = 1.0.0`, y dos ejecuciones consecutivas siguen produciendo salidas byte-idénticas.
- **SC-004**: Ante una divergencia introducida deliberadamente en cada uno de los tres frentes (Java, .NET, tipos generados), la verificación `schema-check` falla en 3 de 3 casos e identifica el frente correcto.
- **SC-005**: Cero tipos de consumidor escritos a mano para los artefactos: el 100% de los tipos y validadores provienen de la generación.
- **SC-006**: Las 7 divergencias de documentación listadas en Contexto quedan resueltas (0 contradicciones entre referencias documentales y contrato formal).
- **SC-007**: Todas las pruebas existentes de Java y .NET siguen pasando tras la incorporación de la versión del contrato (0 regresiones).
- **SC-008**: El tamaño del artefacto desplegable del motor no aumenta por dependencias de validación (0 dependencias nuevas de ejecución).
- **SC-009**: La verificación `schema-check` completa en menos de 10 minutos en el entorno de integración continua.

## Clarifications

### Session 2026-10-07

- Q: ¿El contrato 1.0.0 describe el output real o la documentación en prosa cuando difieren? -> A: El output real (estado v1.1.0); la documentación se corrige. Cambiar el output para ajustarlo a la prosa sería un cambio breaking sin justificación (principio I). (default elegido por agente — confirmado por usuario 2026-10-07) [FR-003, FR-020]
- Q: ¿Objetos cerrados (rechazan campos no declarados) o abiertos? -> A: Cerrados para objetos de estructura conocida, abiertos solo en mapas de claves dinámicas; es la opción que permite detectar divergencias (criterio de salida de la fase). (default elegido por agente — confirmado por usuario 2026-10-07) [FR-004]
- Q: ¿Qué campos tienen catálogo cerrado? -> A: Solo capa, severidad y tipo de `code_issue`, viabilidad, tipo de `legacy_entrypoint` y `meta.source`; método HTTP, tipo de arista y patrón de `code_issue` quedan abiertos porque sus productores los extienden con frecuencia. (default elegido por agente — confirmado por usuario 2026-10-07) [FR-005]
- Q: ¿Cómo declaran la versión los artefactos sin `meta` (arquitectura y puntos de entrada)? -> A: Se añade un objeto `meta` con `schema_version` (aditivo, coherente con "`meta.schema_version` obligatorio" del plan y con el criterio global "schema_version presente en todas las salidas"). (default elegido por agente — confirmado por usuario 2026-10-07) [FR-008]
- Q: ¿Deben `get_component_metrics` y `analyze_project` incluir `schema_version` en su respuesta? -> A: No en esta feature: se validan contra el contrato pero no cambian de forma (un componente aislado y el resumen de análisis no son artefactos). Minimiza cambios en respuestas existentes. (default elegido por agente — confirmado por usuario 2026-10-07) [FR-002, FR-008]
- Q: ¿Añadir un valor a un catálogo cerrado es cambio mayor o menor? -> A: Mayor (conservador: un consumidor con tratamiento exhaustivo del catálogo se rompería). (default elegido por agente — confirmado por usuario 2026-10-07) [FR-019]
- Q: ¿La validación contra el contrato se ejecuta también en tiempo de ejecución del motor? -> A: No; solo en pruebas y en `schema-check` (principio VI, sin dependencias de ejecución nuevas). (default elegido por agente — confirmado por usuario 2026-10-07) [FR-021]
- Q: ¿Qué hace el motor si el analizador .NET declara otra versión del contrato? -> A: Advierte con ambas versiones y emite la del motor (la salida final la firma el motor principal). (default elegido por agente — confirmado por usuario 2026-10-07) [FR-022]
- Q: ¿Se valida integridad referencial entre ids? -> A: No en esta feature; contrato estructural. (default elegido por agente — confirmado por usuario 2026-10-07) [Edge Cases]
- Q: ¿`schema-check` sin SDK .NET disponible puede reportar éxito parcial? -> A: No; falla indicando el frente no verificado, para que el criterio de salida no se cumpla por omisión. (default elegido por agente — confirmado por usuario 2026-10-07) [FR-015]
