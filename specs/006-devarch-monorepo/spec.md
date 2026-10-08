# Feature Specification: Unificación del repositorio dev-arch

**Feature Branch**: `006-devarch-monorepo`
**Created**: 2026-10-08
**Status**: Draft
**Input**: User description: "Fase 2 de PLAN-INTEGRACION-DEV-ARCH.md — Unificación del repositorio: el motor de análisis (Static Inference, Java + .NET), el contrato de datos (feature 005), la aplicación web dev-arch, las skills, la documentación y la gobernanza iikit pasan a vivir juntos como un único producto dentro del monorepo `maestria-ia`, con historia del motor preservada, construcción y pruebas orquestadas desde un único punto de entrada, una sola versión de producto, integración continua por áreas y sin binarios versionados. Criterio de salida: construir y probar en verde desde un clon limpio, y la web en modo desarrollo analiza un ZIP usando el motor compilado localmente."

## Contexto

Hoy el producto `dev-arch` está repartido en piezas que no se conocen entre sí:

- **Motor de análisis** (Static Inference v1.2.0): repositorio git propio con el analizador Java, el analizador .NET (C# y VB.NET), el servidor MCP, 48 commits de historia, especificaciones iikit 001–005, Constitución y Premisa, skills para agentes y el **contrato de datos 1.0.0** (feature 005) con su paquete de tipos generados y su verificación de divergencias.
- **Aplicación web** (dev-arch): importada tal cual (2026-10-08) en la carpeta de producto del monorepo `maestria-ia`, en una rama de trabajo. Analiza un ZIP invocando un **binario del analizador copiado a mano en enero de 2026** (~15 MB) que no incluye MCP, .NET ni VB, y lee los resultados del directorio de trabajo. Incluye además un archivo de salida antiguo versionado por accidente. No tiene pruebas ni integración continua.
- **Monorepo `maestria-ia`**: aloja otros proyectos (ciencia de datos, ML, PLN) con reglas de exclusión de archivos globales (ignora, entre otros, archivos de bloqueo de dependencias, comprimidos, volcados SQL y bases de datos) y **sin** integración continua propia.

Mientras estas piezas sigan separadas, la web seguirá usando un analizador obsoleto, cada cambio del contrato exigirá sincronización manual entre repositorios y no es posible verificar el producto completo con un solo comando. Esta feature unifica la **estructura** del producto; la integración en tiempo de ejecución entre la web y el motor (cliente MCP, trabajos asíncronos, seguridad de carga) es la feature 007 y queda fuera de alcance.

## User Stories *(mandatory)*

### User Story 1 — Un solo producto con la historia del motor preservada (Priority: P1)

Como mantenedor de dev-arch, quiero encontrar en una única carpeta de producto del monorepo la aplicación web, el motor de análisis, el contrato de datos, las skills, la documentación y la gobernanza (Constitución, Premisa, especificaciones), y poder consultar la historia completa de cualquier archivo del motor, de modo que deje de mantener repositorios paralelos sin perder la trazabilidad de las features 001–005.

**Why this priority**: Es el cimiento de las demás historias: sin la estructura unificada no hay nada que construir, probar ni integrar.

**Independent Test**: En un clon limpio del monorepo en la rama de la feature, listar la carpeta de producto y verificar que contiene exactamente las áreas acordadas; consultar la historia de un archivo del motor y verificar que muestra los commits originales del repositorio de Static Inference; verificar que ningún binario ejecutable del analizador está versionado.

**Acceptance Scenarios**:

1. **Given** un clon limpio del monorepo en la rama de la feature, **When** se lista la carpeta de producto, **Then** contiene las áreas web, motor, contrato, skills, documentación y contenedores, además de la Constitución, la Premisa y las especificaciones iikit en su raíz
2. **Given** un archivo fuente del motor (p. ej. el servicio de análisis compartido), **When** se consulta su historia de cambios en el monorepo, **Then** aparecen los commits originales del repositorio de Static Inference, incluido el commit de la versión 1.2.0
3. **Given** el contenido versionado de la carpeta de producto, **When** se buscan binarios del analizador (paquetes ejecutables Java o el ejecutable del analizador .NET), **Then** no se encuentra ninguno; el binario copiado a mano y el archivo de salida antiguo de la web ya no están
4. **Given** las copias de trabajo previas del motor y de la web fuera de la carpeta de producto, **When** se completa la feature, **Then** siguen intactas (esta feature no las elimina)
5. **Given** las especificaciones iikit 001–005 del motor, **When** se abren desde la raíz del producto, **Then** están presentes con su contenido y sus anclas de integridad de aserciones sin cambios

---

### User Story 2 — Construcción y pruebas del producto completo desde un clon limpio (Priority: P1)

Como desarrollador que acaba de clonar el monorepo, quiero construir todo el producto (motor Java, analizador .NET, paquete de contrato y web) con un único comando y ejecutar todas sus pruebas con otro, de modo que sepa en minutos si el producto está sano sin conocer los detalles de cada área.

**Why this priority**: Es el criterio de salida de la fase y la condición para que la integración continua y las features siguientes funcionen.

**Independent Test**: En un clon limpio, con los prerrequisitos instalados (JDK 17 seleccionado explícitamente aunque la máquina tenga otro por defecto, .NET 8 y Node 20), ejecutar el comando de construcción y luego el de pruebas: ambos terminan con éxito; ejecutar cada subconjunto de pruebas (solo motor, solo web) por separado también funciona.

**Acceptance Scenarios**:

1. **Given** un clon limpio con los prerrequisitos instalados, **When** se ejecuta el comando único de construcción desde la raíz del producto, **Then** se generan el motor ejecutable, el analizador .NET y la web lista para servir, y el comando termina con éxito
2. **Given** el producto construido, **When** se ejecuta el comando único de pruebas, **Then** se ejecutan las pruebas del motor Java, del analizador .NET (excluyendo el corpus no redistribuible), del contrato y de la web, y el comando termina con éxito
3. **Given** una máquina cuyo JDK por defecto es una versión distinta de 17 y una variable de entorno que selecciona un JDK 17, **When** se construye y se prueba, **Then** todas las herramientas usan el JDK seleccionado
4. **Given** una máquina sin alguno de los prerrequisitos (JDK 17, .NET 8 o Node 20), **When** se ejecuta el comando de construcción, **Then** falla antes de compilar, nombrando el prerrequisito faltante y la versión requerida
5. **Given** el producto, **When** se piden por separado las pruebas del motor o las de la web, **Then** cada comando ejecuta solo su subconjunto
6. **Given** el producto construido, **When** se ejecuta el comando de limpieza, **Then** desaparecen todos los artefactos generados y el árbol de trabajo queda igual que tras el clon
7. **Given** el comando único de verificación del contrato heredado de la feature 005, **When** se ejecuta desde la raíz del producto, **Then** produce el mismo resultado y formato que en el repositorio original

---

### User Story 3 — La web analiza un ZIP con el motor compilado localmente (Priority: P1)

Como usuario de la web dev-arch en modo desarrollo, quiero subir el ZIP de un proyecto y ver sus resultados producidos por el motor que acabo de compilar, de modo que la web refleje siempre las capacidades actuales del motor y no un binario obsoleto.

**Why this priority**: Es la segunda mitad del criterio de salida y el valor visible para el usuario final de la fase.

**Independent Test**: Tras construir el producto, levantar la web en modo desarrollo, subir el ZIP de un proyecto Java de demostración y verificar que el análisis termina y que los resultados declaran la versión de contrato vigente (que el binario antiguo no emitía).

**Acceptance Scenarios**:

1. **Given** el producto construido y la web en modo desarrollo, **When** se sube el ZIP de un proyecto Java de demostración, **Then** el análisis termina con éxito y la web muestra componentes y propuestas de arquitectura
2. **Given** el mismo análisis, **When** se inspeccionan los resultados devueltos, **Then** declaran la versión de contrato 1.0.0, lo que prueba que los produjo el motor actual
3. **Given** la web en modo desarrollo sin el motor construido, **When** se sube un ZIP, **Then** el análisis falla con un mensaje que indica que el motor no está construido y qué comando ejecutar
4. **Given** una ruta alternativa del motor indicada por configuración del entorno, **When** se sube un ZIP, **Then** la web usa el motor de esa ruta
5. **Given** el producto construido con el analizador .NET disponible, **When** se sube el ZIP de un proyecto .NET de demostración, **Then** el análisis termina con éxito y los resultados identifican el origen .NET

---

### User Story 4 — Contrato de datos a nivel de producto (Priority: P2)

Como integrador del motor y de la web, quiero que el contrato de datos versionado y su paquete de tipos generados vivan a nivel de producto, al lado del motor y de la web, de modo que ambos lo consuman desde un mismo lugar y una divergencia en cualquiera de ellos se detecte igual que antes.

**Why this priority**: Habilita la feature 007 (la web consumirá tipos generados) sin bloquear el criterio de salida de esta fase.

**Independent Test**: Ejecutar la verificación de contrato desde la raíz del producto y obtener el mismo resultado que en el repositorio original; introducir una divergencia controlada en cada frente (Java, .NET, tipos) y comprobar que la verificación falla nombrando ese frente.

**Acceptance Scenarios**:

1. **Given** el contrato a nivel de producto, **When** se ejecuta la verificación de contrato, **Then** los cuatro frentes (versión, Java, .NET, tipos) se reportan correctos
2. **Given** una divergencia introducida en uno de los frentes, **When** se ejecuta la verificación, **Then** falla nombrando ese frente, igual que en la feature 005
3. **Given** el paquete de tipos del contrato, **When** la web declara su dependencia del paquete, **Then** la resolución usa la copia local del producto, sin publicar en un registro externo
4. **Given** las pruebas de contrato del motor, **When** se ejecutan desde la carpeta del motor, **Then** localizan el contrato del producto sin configuración manual

---

### User Story 5 — Integración continua por áreas (Priority: P2)

Como mantenedor, quiero que cada cambio en el producto dispare solo las verificaciones del área afectada (motor Java, analizador .NET, web, contrato) y que la publicación de versiones siga funcionando, de modo que la integración continua sea rápida y no afecte a los demás proyectos del monorepo.

**Why this priority**: Protege el producto contra regresiones, pero el producto ya es verificable localmente sin ella.

**Independent Test**: Revisar la definición de integración continua y simular cambios en cada área: un cambio solo en la web activa únicamente las verificaciones web; un cambio en el contrato activa contrato y los frentes que lo consumen; un cambio fuera de la carpeta de producto no activa ninguna verificación de dev-arch.

**Acceptance Scenarios**:

1. **Given** un cambio que solo toca la web, **When** se evalúa la integración continua, **Then** se ejecuta solo la verificación web
2. **Given** un cambio que solo toca el motor Java, **When** se evalúa, **Then** se ejecutan las verificaciones del motor Java y del contrato
3. **Given** un cambio que solo toca el analizador .NET, **When** se evalúa, **Then** se ejecutan las verificaciones del analizador .NET y del contrato
4. **Given** un cambio en el contrato, **When** se evalúa, **Then** se ejecutan la verificación de contrato y las de todos sus consumidores
5. **Given** un cambio fuera de la carpeta de producto (otros proyectos del monorepo), **When** se evalúa, **Then** no se ejecuta ninguna verificación de dev-arch
6. **Given** la verificación de contrato en integración continua, **When** se inspecciona, **Then** sigue siendo bloqueante, con JDK 17, .NET 8 y Node 20, sin tolerar fallos
7. **Given** una etiqueta de versión de dev-arch, **When** se publica, **Then** se genera la distribución del motor por plataforma como hoy, con nombre de producto y versión únicos

---

### User Story 6 — Una sola versión del producto (Priority: P2)

Como responsable de versiones, quiero declarar la versión del producto en un único lugar y que el motor, el analizador .NET, la web y el servidor MCP la reporten idéntica, de modo que cualquier artefacto o respuesta pueda trazarse a una versión exacta.

**Why this priority**: Requisito de la publicación unificada; sin él la publicación de la historia 5 mezcla versiones.

**Independent Test**: Leer la versión de cada artefacto construido (motor, analizador .NET, web, informe del servidor MCP) y compararla con la fuente única; alterar una de ellas y verificar que la comprobación falla nombrando el artefacto.

**Acceptance Scenarios**:

1. **Given** la fuente única de versión del producto, **When** se construye, **Then** el motor, el analizador .NET, la web y el servidor MCP reportan esa misma versión
2. **Given** un artefacto cuya versión declarada difiere de la fuente única, **When** se ejecutan las pruebas, **Then** fallan nombrando el artefacto y ambas versiones
3. **Given** un cambio de la versión del producto, **When** se propaga, **Then** basta con editar la fuente única y ejecutar el comando de propagación; ningún otro archivo se edita a mano
4. **Given** la versión del contrato de datos, **When** se compara con la del producto, **Then** son independientes: cambiar la versión del producto no altera la del contrato

---

### User Story 7 — Configuración de desarrollo y gobernanza unificadas (Priority: P3)

Como desarrollador o agente de IA trabajando en dev-arch, quiero un único conjunto de reglas de exclusión, de estilo de editor, de instrucciones para agentes y de configuración del servidor MCP local, con la Constitución, la Premisa y las especificaciones en la raíz del producto, de modo que cualquier herramienta se comporte igual en todo el producto.

**Why this priority**: Mejora la experiencia de desarrollo, pero el producto funciona sin ella.

**Independent Test**: Abrir el producto con un agente de IA y comprobar que encuentra sus instrucciones y que el servidor MCP local configurado arranca con el motor construido sin rutas absolutas; comprobar que las especificaciones están versionadas y que los artefactos generados no lo están.

**Acceptance Scenarios**:

1. **Given** el producto construido, **When** un agente de IA arranca el servidor MCP local con la configuración versionada, **Then** el servidor arranca usando el motor construido mediante una ruta relativa al producto
2. **Given** las reglas de exclusión del producto, **When** se construye y se prueba, **Then** ningún artefacto generado aparece como cambio pendiente, y las especificaciones y los archivos de bloqueo de dependencias del producto sí son versionables
3. **Given** las reglas de exclusión globales del monorepo, **When** se aplican las del producto, **Then** las reglas globales no se modifican y los demás proyectos no cambian de comportamiento
4. **Given** el producto, **When** se buscan instrucciones para agentes y reglas de estilo de editor, **Then** existe un único conjunto en la raíz del producto que aplica a web, motor y contrato
5. **Given** el producto tras la feature, **When** se genera el tablero iikit, **Then** refleja las features 001–006 desde la raíz del producto

---

### Edge Cases

- **JDK por defecto distinto de 17** (la máquina de referencia tiene 25): toda herramienta que use Java respeta el JDK seleccionado por variable de entorno; si no hay JDK 17 disponible, la construcción falla con mensaje claro en vez de compilar con otra versión.
- **Analizador .NET sin SDK, solo runtime**: el analizador degrada a análisis directo de fuentes (comportamiento vigente de v1.2.0); la construcción sí exige el SDK.
- **Reglas de exclusión globales del monorepo** que coinciden con archivos ya versionados del motor (volcados SQL y archivos de bloqueo dentro de los proyectos de demostración): la importación los conserva versionados y las pruebas que dependen de ellos siguen funcionando.
- **Archivos de bloqueo de dependencias ignorados globalmente**: el producto los necesita versionados para construir de forma reproducible desde un clon limpio.
- **Pruebas del motor que asumen la raíz del antiguo repositorio** (ubicación del contrato, de las especificaciones, de la documentación y de la definición de integración continua): tras la reubicación deben seguir encontrándolos sin cambiar lo que verifican.
- **Corpus VB no redistribuible**: las pruebas que lo requieren siguen excluidas del comando de pruebas por defecto.
- **Análisis simultáneos desde la web**: siguen sin aislarse (deuda conocida, feature 007); esta feature no la empeora.
- **Salida del motor actual con campos que el binario antiguo no emitía**: la web los ignora sin fallar.
- **Ruta del producto con espacios**: los comandos de construcción, prueba y análisis funcionan igual.
- **Integración continua inexistente en el monorepo**: la nueva definición no debe activarse para cambios fuera de la carpeta de producto.

## Requirements *(mandatory)*

### Functional Requirements

**Estructura e importación**

- **FR-001**: El producto MUST residir en una única carpeta del monorepo con seis áreas: web, motor, contrato, skills, documentación y contenedores.
- **FR-002**: El motor MUST importarse con su historia completa de commits, de modo que la historia de cualquier archivo del motor muestre sus commits originales.
- **FR-003**: La aplicación web existente MUST reubicarse íntegra en el área web sin cambios funcionales, salvo los exigidos por FR-013 a FR-016.
- **FR-004**: Ningún binario del analizador (paquete ejecutable Java ni ejecutable del analizador .NET) MUST estar versionado; el binario copiado a mano y el archivo de salida antiguo de la web MUST eliminarse.
- **FR-005**: La Constitución, la Premisa y las especificaciones iikit 001–005 MUST ubicarse en la raíz del producto, sin alterar su contenido ni sus anclas de integridad de aserciones; la Constitución se traslada sin enmiendas.
- **FR-006**: Las skills para agentes y la documentación del motor MUST ubicarse en sus áreas de producto respectivas.
- **FR-007**: Las copias de trabajo previas del motor y de la web fuera de la carpeta de producto MUST permanecer intactas.

**Construcción y pruebas**

- **FR-008**: El producto MUST ofrecer, desde su raíz, comandos únicos para: construir todo, probar todo, probar solo el motor, probar solo la web, verificar el contrato, levantar la web en desarrollo con el motor recién construido, limpiar artefactos, y dos marcadores reservados (pruebas extremo a extremo y diagnóstico del entorno) que informan que su contenido llega en features posteriores.
- **FR-009**: El comando de construcción MUST producir el motor ejecutable, el analizador .NET y la web, y dejar el motor en la ubicación que usa la web por defecto.
- **FR-010**: El comando de pruebas MUST ejecutar las pruebas del motor Java, las del analizador .NET excluyendo la categoría de corpus, las del contrato y las de la web, y fallar si cualquiera falla.
- **FR-011**: Toda herramienta Java invocada por los comandos MUST respetar el JDK seleccionado por la variable de entorno estándar de Java; antes de compilar, los comandos MUST verificar JDK 17, .NET 8 y Node 20 y fallar nombrando el prerrequisito faltante.
- **FR-012**: Los paquetes de la web y del contrato MUST gestionarse como un único espacio de trabajo de dependencias del producto, instalable con un solo comando y reproducible desde los archivos de bloqueo versionados.

**Web con motor local**

- **FR-013**: La web MUST analizar los ZIP con el motor construido localmente, ubicado por defecto en la ruta donde lo deja el comando de construcción.
- **FR-014**: La web MUST permitir sustituir la ruta del motor mediante una variable de entorno.
- **FR-015**: Si el motor no está disponible, la web MUST responder con un error que indique que el motor no está construido y el comando a ejecutar.
- **FR-016**: Los resultados producidos por el motor actual MUST mostrarse en la web sin errores, aunque contengan campos que la web todavía no utiliza.

**Contrato**

- **FR-017**: El contrato de datos y su paquete de tipos generados MUST ubicarse en el área de contrato del producto y ser consumibles por la web como dependencia local del espacio de trabajo.
- **FR-018**: La verificación de contrato MUST conservar sus cuatro frentes, su formato de salida y su semántica de fallo de la feature 005, ejecutada desde la raíz del producto.
- **FR-019**: Las pruebas existentes del motor MUST localizar el contrato, las especificaciones, la documentación y la definición de integración continua en sus nuevas ubicaciones sin modificar lo que afirman.

**Versión**

- **FR-020**: El producto MUST declarar su versión en una única fuente, con valor inicial igual a la versión vigente del motor (1.2.0).
- **FR-021**: El motor, el analizador .NET, la web y el servidor MCP MUST reportar la versión de la fuente única; un comando de propagación MUST actualizar todas las declaraciones y una prueba MUST fallar si alguna diverge.
- **FR-022**: La versión del contrato de datos MUST permanecer independiente de la versión del producto.

**Integración continua**

- **FR-023**: La integración continua MUST tener verificaciones separadas para motor Java, analizador .NET, web y contrato, activadas solo por cambios en sus áreas (y el contrato por cambios en sus consumidores), más una publicación activada por la rama principal o por etiquetas de versión de dev-arch.
- **FR-024**: Ningún cambio fuera de la carpeta de producto MUST activar verificaciones de dev-arch, y la definición de integración continua MUST no alterar el comportamiento de otros proyectos del monorepo.
- **FR-025**: Las verificaciones heredadas del motor (análisis estático con su umbral vigente, filtro de exclusiones, verificación de contrato bloqueante, publicación multiplataforma del analizador .NET dependiente de runtime) MUST conservarse con al menos la misma severidad.

**Configuración unificada**

- **FR-026**: El producto MUST tener un único conjunto de reglas de exclusión de archivos y de estilo de editor que cubra web, motor y contrato, sin modificar las reglas globales del monorepo.
- **FR-027**: El producto MUST versionar una configuración del servidor MCP local que use una ruta relativa al motor construido, sin rutas absolutas de una máquina concreta.
- **FR-028**: El producto MUST tener un único conjunto de instrucciones para agentes de IA en su raíz, que incluya las reglas iikit vigentes.
- **FR-029**: Las especificaciones iikit MUST estar versionadas, y el tablero iikit MUST regenerarse desde la raíz del producto al cerrar la feature.

### Key Entities

- **Producto dev-arch**: unidad de versionado, construcción y publicación; agrupa las seis áreas y la gobernanza.
- **Área**: subdivisión del producto (web, motor, contrato, skills, documentación, contenedores) con sus propias pruebas y disparadores de integración continua.
- **Versión de producto**: identificador semántico único (inicial 1.2.0) que reportan todos los artefactos.
- **Versión de contrato**: identificador semántico del contrato de datos (1.0.0), independiente de la versión de producto.
- **Motor construido**: el ejecutable del motor y el analizador .NET generados localmente; nunca versionados.
- **Comando del producto**: punto de entrada único (construir, probar, verificar contrato, desarrollo, limpiar, marcadores).
- **Verificación de integración continua**: unidad bloqueante asociada a un área y a sus disparadores.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Desde un clon limpio con los prerrequisitos instalados, el comando de construcción seguido del de pruebas termina con éxito en el 100 % de las ejecuciones en la máquina de referencia y en integración continua.
- **SC-002**: Con la web en modo desarrollo tras construir, subir el ZIP del proyecto Java de demostración produce resultados con versión de contrato 1.0.0 en menos de 2 minutos.
- **SC-003**: 0 binarios del analizador en el contenido versionado del producto, y 0 archivos cambiados en el árbol de trabajo tras construir y probar.
- **SC-004**: El 100 % de los commits del repositorio de Static Inference en su rama principal son alcanzables desde la historia del área del motor.
- **SC-005**: El 100 % de las pruebas que pasaban en el repositorio original del motor (excluido el corpus) pasan en el producto, sin modificar ninguna aserción ni ancla de integridad.
- **SC-006**: La verificación de contrato desde la raíz del producto reporta los cuatro frentes correctos y sigue fallando ante una divergencia en cualquiera de ellos.
- **SC-007**: Los cuatro artefactos con versión (motor, analizador .NET, web, servidor MCP) reportan la misma versión que la fuente única; cambiar la versión requiere editar exactamente un archivo.
- **SC-008**: Un cambio limitado a una sola área activa solo las verificaciones de esa área (y de sus dependientes); un cambio fuera del producto no activa ninguna.

## Clarifications

### Session 2026-10-08

- Q: ¿Destino del producto? -> A: Carpeta de producto dentro del monorepo `maestria-ia`, trabajando en una rama de feature del monorepo. (confirmado por usuario 2026-10-07) [FR-001]
- Q: ¿Integración en tiempo de ejecución (cliente MCP) en esta feature? -> A: No; es la feature 007. Esta feature solo garantiza que el flujo de análisis actual de la web use el motor construido localmente. (confirmado por usuario 2026-10-08) [FR-013]
- Q: ¿Se eliminan las copias previas del motor y de la web? -> A: No en esta feature. (confirmado por usuario 2026-10-08) [FR-007]
- Q: ¿El contrato de datos se promueve al nivel de producto o queda dentro del motor? -> A: Se promueve al área de contrato del producto (decisión D1 del plan), y las pruebas del motor lo localizan sin configuración manual. (default elegido por agente — pendiente confirmar) [FR-017, FR-019]
- Q: ¿Valor inicial de la versión única y relación con la del contrato? -> A: 1.2.0 (versión vigente del motor); la web pasa de 0.1.0 a 1.2.0; la versión del contrato (1.0.0) sigue independiente. (default elegido por agente — pendiente confirmar) [FR-020, FR-022]
- Q: ¿Se versionan los archivos de bloqueo de dependencias, que el monorepo ignora globalmente? -> A: Sí, solo dentro del producto, mediante reglas de exclusión propias del producto; las reglas globales no se tocan. (default elegido por agente — pendiente confirmar) [FR-012, FR-026]
- Q: ¿Pueden ajustarse las pruebas del motor que asumen rutas del antiguo repositorio? -> A: Solo la localización de archivos (dónde buscan contrato, especificaciones, documentación, integración continua), nunca lo que afirman; los `.feature` y las anclas de integridad no se tocan. (default elegido por agente — pendiente confirmar) [FR-019, SC-005]
- Q: ¿El análisis de un ZIP .NET desde la web es obligatorio para el criterio de salida? -> A: El criterio de salida exige el ZIP Java; el ZIP .NET es obligatorio cuando el entorno tiene el SDK .NET (en integración continua sí). (default elegido por agente — pendiente confirmar) [FR-013, SC-002]
- Q: ¿Qué hacen los marcadores de pruebas extremo a extremo y diagnóstico? -> A: Informan que se implementan en features posteriores (011 y 012) y terminan con éxito, para no bloquear el comando de pruebas. (default elegido por agente — pendiente confirmar) [FR-008]
- Q: ¿La Constitución cambia de alcance al trasladarse? -> A: No; se traslada sin enmiendas (gobierna el motor; la ampliación a todo el producto es la enmienda 2.0.0 de la Fase 7 y requiere aprobación explícita). Mientras tanto, los principios aplicables (TDD, huella mínima) se respetan también en los cambios de la web de esta feature. (default elegido por agente — pendiente confirmar) [FR-005]
