# Feature Specification: Soporte .NET/C# — Analizador con Roslyn

**Feature Branch**: `002-dotnet-roslyn-analyzer`
**Created**: 2026-04-07
**Status**: Draft
**Input**: "Extender el sistema de analisis estatico para soportar proyectos .NET/C# usando Roslyn, manteniendo backward compatibility total"

## User Stories *(mandatory)*

### User Story 1 — Analizar un proyecto .NET monolitico desde un agente LLM (Priority: P1)

Un arquitecto de software usa un agente LLM y quiere analizar un proyecto .NET/C# monolitico
para obtener recomendaciones de descomposicion en microservicios. Apunta al directorio del
proyecto .NET (que contiene archivos `.sln` o `.csproj`) y recibe el grafo de dependencias,
la propuesta de arquitectura y los contratos API, igual que con proyectos Java.

**Why this priority**: Es el caso de uso central. Sin esta capacidad, el feature no aporta
valor. Los usuarios de .NET necesitan el mismo nivel de analisis que los usuarios de Java.

**Independent Test**: Puede probarse apuntando la herramienta a un proyecto .NET de ejemplo
con controllers, servicios, repositorios y Entity Framework, y verificando que se obtienen
componentes, edges y propuestas de microservicios coherentes.

**Acceptance Scenarios**:

1. **Given** un proyecto .NET/C# con archivos `.sln` o `.csproj`, **When** el usuario
   invoca `analyze_project` apuntando a ese directorio, **Then** recibe un resultado
   estructurado con el mismo schema (`DependencyGraph`) que el analisis Java, incluyendo
   componentes, edges y contratos API.

2. **Given** un proyecto .NET con controllers ASP.NET Core decorados con `[ApiController]`,
   `[HttpGet]`, `[HttpPost]`, etc., **When** se ejecuta el analisis, **Then** los endpoints
   REST aparecen en la seccion `api_contracts` del resultado.

3. **Given** un proyecto .NET con Entity Framework (DbContext, DbSet), **When** se ejecuta
   el analisis, **Then** los componentes incluyen las tablas de base de datos referenciadas
   en sus campos `tables_used`.

---

### User Story 2 — Deteccion automatica del tipo de proyecto (Priority: P1)

Un usuario apunta la herramienta a un directorio sin indicar explicitamente si es Java o
.NET. El sistema detecta automaticamente el tipo de proyecto basandose en los archivos
presentes y aplica el analizador correcto.

**Why this priority**: P1 porque la deteccion automatica es esencial para una experiencia
fluida. Sin ella, el usuario tendria que especificar manualmente el lenguaje, lo cual rompe
la simplicidad del flujo actual.

**Independent Test**: Puede probarse creando directorios de ejemplo con archivos marcadores
de cada tipo (pom.xml, .csproj, .sln, build.gradle) y verificando que se selecciona el
analizador correcto en cada caso.

**Acceptance Scenarios**:

1. **Given** un directorio que contiene archivos `*.sln` o `*.csproj`, **When** se invoca
   el analisis, **Then** el sistema usa automaticamente el analizador .NET.

2. **Given** un directorio que contiene `pom.xml` o `build.gradle`, **When** se invoca
   el analisis, **Then** el sistema usa automaticamente el analizador Java (comportamiento
   actual preservado).

3. **Given** un directorio que contiene tanto archivos .NET como Java, **When** se invoca
   el analisis, **Then** el sistema reporta la ambiguedad y solicita al usuario que
   especifique el lenguaje objetivo.

4. **Given** un directorio sin archivos marcadores reconocidos, **When** se invoca el
   analisis, **Then** el sistema retorna un error claro indicando que no pudo detectar
   el tipo de proyecto.

---

### User Story 3 — Analisis de dependencias de inyeccion en .NET (Priority: P2)

Un arquitecto necesita que el grafo de dependencias refleje las relaciones de inyeccion
de dependencias tipicas de .NET (constructor injection, `IServiceCollection`, interfaces
implementadas) para obtener propuestas de microservicios precisas.

**Why this priority**: La inyeccion de dependencias es el mecanismo principal de
acoplamiento en .NET. Sin detectarla, el grafo seria incompleto y las propuestas
de clustering imprecisas.

**Independent Test**: Puede probarse con un proyecto .NET que tenga servicios inyectados
via constructor y verificando que los edges correspondientes aparecen en el grafo.

**Acceptance Scenarios**:

1. **Given** una clase .NET que recibe dependencias via constructor (constructor injection),
   **When** se ejecuta el analisis, **Then** se generan edges desde esa clase hacia cada
   dependencia inyectada.

2. **Given** una clase que implementa una interfaz y otra clase que depende de esa interfaz,
   **When** se ejecuta el analisis, **Then** el grafo refleja la relacion de implementacion
   y la dependencia a traves de la interfaz.

3. **Given** un proyecto con registro de servicios en `Startup.cs` o `Program.cs`
   (`services.AddScoped<IFoo, Foo>()`), **When** se ejecuta el analisis, **Then** las
   relaciones interfaz-implementacion se detectan correctamente.

---

### User Story 4 — Metricas de calidad para componentes .NET (Priority: P2)

Un desarrollador quiere evaluar la calidad del codigo .NET con las mismas metricas
disponibles para Java: CBO, LCOM, lineas de codigo, y clasificacion por capas
(Controller, Service, Repository, Shared).

**Why this priority**: Las metricas son esenciales para el scoring de viabilidad de
microservicios. Sin metricas, el motor de inferencia no puede calcular cohesion ni
acoplamiento de los clusters.

**Independent Test**: Puede probarse analizando un componente .NET conocido y verificando
que CBO, LCOM y LOC se calculan correctamente.

**Acceptance Scenarios**:

1. **Given** un componente .NET analizado, **When** se consultan sus metricas via
   `get_component_metrics`, **Then** se obtiene CBO, LCOM, LOC, capa y tablas usadas
   con el mismo schema que los componentes Java.

2. **Given** un controller ASP.NET Core, **When** se clasifica por capa, **Then** se
   asigna a la capa "Controlador". **Given** un servicio de negocio, **Then** se asigna
   a "Negocio". **Given** un repositorio/DbContext, **Then** se asigna a "Datos".

---

### User Story 5 — Backward compatibility total del analisis Java (Priority: P1)

Un usuario existente que analiza proyectos Java no debe experimentar ningun cambio en el
comportamiento, formato de salida, ni rendimiento del sistema tras la introduccion del
soporte .NET.

**Why this priority**: La constitucion establece backward compatibility como NON-NEGOTIABLE.
Este feature no debe afectar en absoluto a los usuarios existentes de Java.

**Independent Test**: Puede probarse ejecutando el analisis sobre el mismo proyecto Java
de referencia antes y despues del cambio, y verificando que los resultados son identicos
byte a byte.

**Acceptance Scenarios**:

1. **Given** un proyecto Java analizado con la version actual, **When** se analiza con la
   version que incluye soporte .NET, **Then** el resultado es identico.

2. **Given** el modo CLI con argumentos existentes, **When** se ejecuta con la nueva
   version, **Then** el comportamiento es identico al actual.

3. **Given** los 5 tools MCP existentes invocados sobre un proyecto Java, **When** se
   usan con la nueva version, **Then** las respuestas son identicas.

---

### User Story 6 — Deteccion de patrones de messaging y configuracion .NET (Priority: P3)

Un arquitecto quiere que el analisis detecte patrones de messaging (RabbitMQ, Azure
Service Bus, MassTransit) y referencias a configuracion/secretos en proyectos .NET,
al igual que en Java.

**Why this priority**: Son capacidades complementarias que enriquecen el analisis pero
no son esenciales para el MVP. Se pueden agregar incrementalmente.

**Independent Test**: Puede probarse con un proyecto .NET que use MassTransit o
Azure Service Bus y verificando que los patrones de messaging aparecen en los componentes.

**Acceptance Scenarios**:

1. **Given** un proyecto .NET que usa MassTransit o Azure Service Bus, **When** se ejecuta
   el analisis, **Then** los componentes con patrones de messaging se marcan con
   `messaging_type` correspondiente.

2. **Given** un proyecto .NET con `appsettings.json` o `IConfiguration`, **When** se
   ejecuta el analisis, **Then** las referencias a configuracion y secretos se detectan.

---

### Edge Cases

- Que ocurre cuando el proyecto .NET tiene multiples `.sln` en subdirectorios?
  El sistema debe analizar el `.sln` raiz o reportar la ambiguedad.
- Como se maneja un proyecto .NET que no compila o tiene dependencias NuGet faltantes?
  El analisis debe funcionar en modo "sin compilacion" con resultados parciales.
- Que pasa con proyectos .NET Framework (no Core)? El analizador debe soportar ambos.
- Que ocurre con proyectos que usan F# o VB.NET ademas de C#?
  Solo se analizan archivos C# en esta iteracion; otros se ignoran con advertencia.
- Que pasa si el SDK de .NET no esta instalado en la maquina del usuario?
  El sistema debe detectarlo y reportar un error claro indicando que se necesita el SDK.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST detectar automaticamente si un proyecto es Java o .NET
  basandose en archivos marcadores (`.sln`, `.csproj` para .NET; `pom.xml`,
  `build.gradle` para Java).

- **FR-002**: El sistema MUST proveer una interfaz abstracta de analisis por lenguaje
  que permita intercambiar el analizador sin afectar la capa de inferencia ni los
  entry points (CLI/MCP).

- **FR-003**: El analizador .NET MUST extraer componentes (clases, interfaces) con sus
  metadatos: nombre cualificado, ruta de archivo, lineas de codigo, anotaciones/atributos,
  herencia, y tablas de base de datos referenciadas.

- **FR-004**: El analizador .NET MUST detectar dependencias entre componentes basandose en:
  invocaciones de metodos, constructor injection, implementacion de interfaces, y
  referencias de tipos en campos/propiedades.

- **FR-005**: El analizador .NET MUST extraer endpoints REST de controllers ASP.NET Core
  decorados con atributos de routing (`[HttpGet]`, `[HttpPost]`, `[Route]`, etc.).

- **FR-006**: El analizador .NET MUST detectar tablas de base de datos via Entity Framework
  (`DbSet<T>`, atributo `[Table]`, convenciones de `DbContext`).

- **FR-007**: El analizador .NET MUST calcular metricas de calidad por componente: CBO
  (Coupling Between Objects), LCOM (Lack of Cohesion of Methods), y LOC (Lines of Code).

- **FR-008**: El analizador .NET MUST clasificar componentes por capa arquitectonica
  (Controlador, Negocio, Datos, Compartida) basandose en atributos y convenciones de
  nombres de .NET.

- **FR-009**: El resultado del analizador .NET MUST producir un `DependencyGraph` con
  el mismo schema JSON que el analizador Java, para que el motor de inferencia funcione
  sin modificaciones.

- **FR-010**: El sistema MUST mantener backward compatibility total con el analisis Java
  existente: mismos argumentos CLI, mismos schemas de salida, mismos resultados MCP.

- **FR-011**: El sistema MUST reportar un error claro cuando no puede detectar el tipo
  de proyecto o cuando el entorno de ejecucion necesario no esta disponible.

- **FR-012**: El analizador .NET MUST funcionar sin requerir que el proyecto .NET compile
  exitosamente (modo sin compilacion / no-classpath equivalente).

- **FR-013**: El sistema MUST soportar tanto proyectos .NET Core/.NET 5+ como proyectos
  .NET Framework clasicos.

- **FR-014**: El analizador .NET MUST detectar patrones de inyeccion de dependencias:
  constructor injection, registro en `IServiceCollection`, y relaciones
  interfaz-implementacion.

### Key Entities

- **LanguageAnalyzer**: Abstraccion que define el contrato para analizar un proyecto
  en cualquier lenguaje y producir un `DependencyGraph`.

- **ProjectTypeDetector**: Componente que examina un directorio y determina el tipo
  de proyecto (Java, .NET, ambiguo, desconocido).

- **DotNetComponent**: Representacion de una clase/interfaz C# con sus metadatos,
  mapeada al modelo `Component` existente.

- **RoslynAnalyzer**: Analizador que usa el AST de C# para parsear proyectos y
  extraer componentes, dependencias, endpoints y metricas.

- **DependencyGraph**: (Existente) Modelo compartido language-agnostic que recibe los
  resultados de cualquier analizador.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un proyecto .NET de referencia con controllers, servicios y repositorios
  produce un `DependencyGraph` valido con componentes, edges y contratos API.

- **SC-002**: El analisis de un proyecto .NET de hasta 200 clases completa en menos de
  5 minutos, comparable al rendimiento del analisis Java.

- **SC-003**: La deteccion automatica de tipo de proyecto funciona correctamente para
  proyectos Java puros, .NET puros y mixtos.

- **SC-004**: El motor de inferencia genera propuestas de microservicios coherentes
  a partir del `DependencyGraph` de un proyecto .NET, sin modificaciones en el motor.

- **SC-005**: Todos los tests existentes del analisis Java siguen pasando sin
  modificaciones tras la introduccion del soporte .NET.

- **SC-006**: Las metricas CBO, LCOM y LOC calculadas para componentes .NET son
  consistentes con la logica de calculo existente para Java.

- **SC-007**: Los 5 tools MCP funcionan transparentemente con proyectos .NET usando
  los mismos schemas de entrada/salida.
