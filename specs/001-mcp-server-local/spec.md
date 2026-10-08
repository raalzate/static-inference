# Feature Specification: MCP Server — Instalación Local

**Feature Branch**: `001-mcp-server-local`
**Created**: 2026-03-25
**Status**: Draft
**Input**: "convierte esta herramienta en una herramienta MCP con capacidad de generar los
archivos, esta herramienta se instala de manera local"

## User Stories *(mandatory)*

### User Story 1 — Invocar análisis completo desde un agente LLM (Priority: P1)

Un arquitecto de software usa Claude Code (u otro agente LLM) y quiere analizar un proyecto
Java monolítico para obtener recomendaciones de descomposición en microservicios sin salir
de su flujo conversacional. Le indica al agente el path del proyecto y recibe directamente
el grafo de dependencias, la propuesta de arquitectura y los contratos API.

**Why this priority**: Es el caso de uso central. Sin esta capacidad, el MCP server no
aporta valor diferencial sobre el CLI existente.

**Independent Test**: Puede probarse configurando el servidor MCP localmente, apuntándolo
a un proyecto Java de ejemplo, y verificando que el agente LLM recibe los tres conjuntos
de resultados y los archivos en disco.

**Acceptance Scenarios**:

1. **Given** el servidor MCP está corriendo localmente, **When** el agente invoca el tool
   `analyze_project` con un path válido a un proyecto Java, **Then** recibe un resultado
   estructurado con grafo de dependencias, propuesta de microservicios y contratos API, y
   los archivos correspondientes se generan en disco.

2. **Given** el servidor MCP está corriendo, **When** el agente invoca `analyze_project`
   con un path que no existe o no contiene fuentes Java, **Then** recibe un mensaje de
   error claro y accionable indicando la causa del fallo.

3. **Given** el servidor está iniciado, **When** el análisis tarda más de 30 segundos,
   **Then** el servidor mantiene la conexión activa y notifica progreso al agente.

---

### User Story 2 — Consultar un tipo específico de análisis (Priority: P2)

Un desarrollador quiere obtener únicamente los contratos API de un proyecto sin recibir
toda la información de arquitectura, para no saturar el contexto del agente LLM con datos
innecesarios.

**Why this priority**: La granularidad en las consultas reduce el ruido y mejora la
utilidad práctica en conversaciones enfocadas.

**Independent Test**: Puede probarse invocando cada tool individual de forma aislada y
verificando que cada uno retorna solo la información pertinente.

**Acceptance Scenarios**:

1. **Given** el servidor MCP está corriendo, **When** el agente invoca `get_api_contracts`
   con un path válido, **Then** recibe únicamente los endpoints y schemas REST/messaging
   del proyecto, sin información de clustering ni métricas de calidad.

2. **Given** el servidor MCP está corriendo, **When** el agente invoca
   `get_architecture_proposal` con un umbral de viabilidad mínimo (ej. "Alta"),
   **Then** recibe solo las propuestas que superan ese umbral.

3. **Given** el servidor MCP está corriendo, **When** el agente invoca
   `get_component_metrics` con un identificador de componente,
   **Then** recibe las métricas de ese componente específico (CBO, LCOM, capa, tablas).

---

### User Story 3 — Instalar y configurar el servidor localmente (Priority: P1)

Un desarrollador quiere instalar y activar el servidor MCP en su máquina local para usarlo
con su agente LLM. Debe poder descargarlo como un archivo ejecutable único, configurar su
agente para apuntar al servidor, y verificar que funciona sin infraestructura adicional.

**Why this priority**: Sin instalación simple, la herramienta no llega a los usuarios.
P1 porque es co-dependiente con la funcionalidad principal.

**Independent Test**: Puede probarse ejecutando el JAR con el flag MCP, configurando el
archivo de integración del agente, y verificando que el agente lista los tools disponibles.

**Acceptance Scenarios**:

1. **Given** el usuario tiene instalado un entorno de ejecución compatible, **When**
   ejecuta el JAR con el argumento de modo MCP, **Then** el servidor inicia y queda
   disponible para recibir invocaciones via transporte stdio.

2. **Given** el servidor está iniciado, **When** un agente LLM solicita la lista de tools
   disponibles, **Then** el servidor responde con los 5 tools y sus schemas de parámetros
   en formato MCP estándar.

3. **Given** el usuario configura el archivo de integración del agente (ej. `.mcp.json`),
   **When** el agente LLM inicia, **Then** descubre automáticamente el servidor y sus
   capacidades sin configuración adicional.

---

### User Story 4 — Modo CLI preservado tras la actualización (Priority: P2)

Un usuario existente del CLI no quiere que su flujo de trabajo actual se rompa al
actualizar la herramienta a la versión con soporte MCP.

**Why this priority**: La constitución establece backward compatibility como
NON-NEGOTIABLE. El CLI debe seguir funcionando exactamente igual.

**Independent Test**: Puede probarse ejecutando el JAR actualizado con los argumentos
CLI originales y verificando que produce los mismos archivos que la versión anterior.

**Acceptance Scenarios**:

1. **Given** el JAR actualizado, **When** se ejecuta con los argumentos CLI originales
   `<project-path> <output-file>`, **Then** produce los mismos archivos JSON con el
   mismo contenido que la versión anterior del CLI.

2. **Given** el JAR actualizado, **When** se ejecuta sin argumentos o con `--help`,
   **Then** muestra las instrucciones de uso tanto para modo CLI como para modo MCP.

---

### Edge Cases

- ¿Qué ocurre cuando el proyecto Java es muy grande y el análisis demora varios minutos?
  El servidor debe mantener la conexión activa y comunicar progreso.
- ¿Cómo se maneja un proyecto con fuentes parcialmente inválidas o sin compilar?
  El análisis parcial debe continuar con advertencias, no fallar completamente.
- ¿Qué pasa si dos agentes invocan el servidor simultáneamente con paths diferentes?
  Cada invocación debe ser independiente y no interferir con las demás.
- ¿Qué ocurre si el path del proyecto contiene espacios o caracteres especiales?
  Los paths deben manejarse correctamente en todos los sistemas operativos.
- ¿Qué pasa si el directorio de salida para los archivos generados no existe?
  El sistema debe crearlo o reportar el error claramente.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST exponer un modo de ejecución MCP activable mediante un
  argumento de línea de comandos, independiente del modo CLI existente.

- **FR-002**: El servidor MCP MUST implementar el transporte stdio como mecanismo
  de comunicación con agentes LLM.

- **FR-003**: El servidor MUST exponer un tool `analyze_project` que reciba un path
  de proyecto Java y retorne el análisis completo (grafo, arquitectura, contratos),
  y genere los archivos de salida en disco.

- **FR-004**: El servidor MUST exponer un tool `get_dependency_graph` que retorne
  únicamente el grafo de dependencias del proyecto.

- **FR-005**: El servidor MUST exponer un tool `get_architecture_proposal` que retorne
  únicamente las propuestas de descomposición en microservicios, con filtrado opcional
  por nivel de viabilidad mínima.

- **FR-006**: El servidor MUST exponer un tool `get_api_contracts` que retorne
  únicamente los contratos REST y messaging del proyecto.

- **FR-007**: El servidor MUST exponer un tool `get_component_metrics` que reciba un
  identificador de componente y retorne sus métricas individuales.

- **FR-008**: Todos los tools MUST retornar respuestas en formato MCP estándar con
  mensajes de error accionables cuando el análisis falla, sin exponer stack traces.

- **FR-009**: El sistema MUST mantener el modo CLI existente completamente funcional
  con comportamiento idéntico al actual para los mismos argumentos de entrada.

- **FR-010**: El sistema MUST distribuirse como un único artefacto ejecutable que
  soporte ambos modos de operación sin dependencias externas más allá del entorno
  de ejecución.

- **FR-011**: El servidor MCP MUST responder a la solicitud de listado de tools con
  los schemas completos de parámetros y descripción de cada tool.

- **FR-012**: La lógica de análisis MUST residir en una capa core compartida, sin
  duplicación entre los adaptadores CLI y MCP.

- **FR-013**: El servidor MUST proporcionar un archivo de configuración de ejemplo
  para integración con agentes LLM compatibles con MCP.

### Key Entities

- **McpServer**: Componente que gestiona el protocolo de comunicación, registra los
  tools disponibles y delega la ejecución al core de análisis.

- **AnalysisTool**: Capacidad expuesta por el servidor con nombre, descripción,
  schema de parámetros de entrada y formato de respuesta esperado.

- **AnalysisRequest**: Solicitud de análisis que incluye el path del proyecto y
  opciones opcionales (umbral de viabilidad, id de componente, directorio de salida).

- **AnalysisResult**: Resultado estructurado que contiene los datos del análisis
  solicitado más metadatos (duración, componentes procesados, advertencias).

- **AnalysisService**: Capa core compartida que encapsula toda la lógica de análisis,
  invocable tanto desde el adaptador CLI como desde el adaptador MCP.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un agente LLM puede invocar `analyze_project` sobre un proyecto Java de
  referencia y recibir resultados estructurados en menos de 5 minutos para proyectos
  de hasta 200 clases.

- **SC-002**: Los 5 tools MCP están disponibles con schemas de parámetros completos
  que permiten al agente LLM invocarlos sin instrucciones manuales adicionales.

- **SC-003**: El modo CLI produce resultados idénticos antes y después de la integración
  MCP para el mismo proyecto de entrada.

- **SC-004**: El artefacto ejecutable único permite iniciar el servidor MCP con un solo
  comando sin instalar dependencias adicionales más allá del entorno de ejecución.

- **SC-005**: Los archivos de salida en disco generados via MCP tienen el mismo
  contenido que los producidos por el modo CLI para el mismo proyecto.

- **SC-006**: Errores en análisis (path inválido, proyecto vacío, fuentes no parseables)
  producen mensajes de error MCP estructurados y accionables.
