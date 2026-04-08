# MCP - Model Context Protocol Server

Servidor MCP integrado que expone las capacidades de analisis estatico como herramientas consumibles por asistentes IA (Claude Code, VS Code, JetBrains, etc.).

## Arquitectura

```
┌─────────────────────────────────────────────────┐
│              Asistente IA (Claude Code)          │
│                      │                          │
│               protocolo stdio                   │
│                      ▼                          │
│  ┌──────────────────────────────────────────┐   │
│  │         McpServerMain.java               │   │
│  │    (StdioServerTransportProvider)        │   │
│  │              │                           │   │
│  │    ┌─────────┼─────────┐                 │   │
│  │    ▼         ▼         ▼                 │   │
│  │ Tool 1   Tool 2   Tool N                │   │
│  │    │         │         │                 │   │
│  │    └─────────┼─────────┘                 │   │
│  │              ▼                           │   │
│  │      AnalysisService.java               │   │
│  │       (logica compartida)               │   │
│  │         │              │                 │   │
│  │         ▼              ▼                 │   │
│  │   JavaSpoon     DotNetBridge             │   │
│  │   Analyzer      Analyzer                │   │
│  └──────────────────────────────────────────┘   │
└─────────────────────────────────────────────────┘
```

El servidor MCP y la CLI comparten el mismo `AnalysisService`, garantizando resultados identicos independientemente del modo de ejecucion.

## Inicio Rapido

### 1. Compilar el JAR

```bash
mvn clean package -DskipTests
```

Genera `target/java-dependency-extractor.jar` con todas las dependencias incluidas.

### 2. (Opcional) Compilar el analizador .NET

Solo necesario si se van a analizar proyectos .NET/C#:

```bash
dotnet publish src/main/dotnet/DotNetAnalyzer/DotNetAnalyzer.csproj \
  -c Release -r osx-arm64 --self-contained -p:PublishSingleFile=true -o target
```

> Ajustar `-r` segun la plataforma: `osx-x64` (Mac Intel), `linux-x64`, `win-x64`.

### 3. Configurar el cliente MCP

Crear o editar `.mcp.json` en la raiz del proyecto:

```json
{
  "mcpServers": {
    "static-inference": {
      "type": "stdio",
      "command": "java",
      "args": [
        "-jar",
        "/ruta/absoluta/target/java-dependency-extractor.jar",
        "--mcp"
      ]
    }
  }
}
```

### 4. Verificar la conexion

En Claude Code, las herramientas del servidor aparecen automaticamente al iniciar la sesion. En otros clientes MCP, consultar la documentacion del cliente para registrar servidores stdio.

## Herramientas Disponibles

### `analyze_project`

Ejecuta el pipeline completo de analisis estatico. Genera archivos JSON en disco y retorna un resumen.

**Parametros:**

| Parametro | Tipo | Requerido | Descripcion |
|-----------|------|-----------|-------------|
| `projectPath` | string | Si | Ruta absoluta al directorio raiz del proyecto |
| `outputDir` | string | No | Directorio de salida para los JSON. Por defecto: raiz del proyecto |

**Respuesta:**

```json
{
  "componentCount": 20,
  "edgeCount": 42,
  "proposalCount": 6,
  "endpointCount": 7,
  "filesGenerated": [
    "/path/output.json",
    "/path/output_architecture.json",
    "/path/output_entrypoints.json"
  ],
  "summary": "ANALISIS DE ARQUITECTURA - COMPONENTES AGRUPADOS..."
}
```

**Archivos generados:**
- `output.json` — Grafo completo de dependencias
- `output_architecture.json` — Propuestas de microservicios con viabilidad
- `output_entrypoints.json` — Contratos de API (endpoints REST y listeners)

---

### `get_dependency_graph`

Retorna el grafo de dependencias completo sin generar archivos en disco.

**Parametros:**

| Parametro | Tipo | Requerido | Descripcion |
|-----------|------|-----------|-------------|
| `projectPath` | string | Si | Ruta absoluta al directorio raiz del proyecto |

**Respuesta:** Objeto `DependencyGraph` con componentes, aristas, contratos API y metadatos.

---

### `get_architecture_proposal`

Retorna propuestas de descomposicion en microservicios con scores de viabilidad.

**Parametros:**

| Parametro | Tipo | Requerido | Descripcion |
|-----------|------|-----------|-------------|
| `projectPath` | string | Si | Ruta absoluta al directorio raiz del proyecto |
| `minViability` | string | No | Umbral minimo de viabilidad: `"Alta"`, `"Media"`, `"Baja"` |

**Respuesta:** Objeto `ConsolidatedArchitecture` con propuestas filtradas por viabilidad.

**Ejemplo de uso:** Obtener solo propuestas listas para implementar:
```
get_architecture_proposal(projectPath="/path/to/project", minViability="Alta")
```

---

### `get_api_contracts`

Retorna los endpoints REST y contratos de mensajeria descubiertos en el proyecto.

**Parametros:**

| Parametro | Tipo | Requerido | Descripcion |
|-----------|------|-----------|-------------|
| `projectPath` | string | Si | Ruta absoluta al directorio raiz del proyecto |

**Deteccion soportada:**
- **Java:** `@RestController`, `@RequestMapping`, `@Path` (JAX-RS)
- **.NET:** `[ApiController]`, `[HttpGet]`, `[HttpPost]`, `[HttpPut]`, `[HttpDelete]`
- **Mensajeria:** Kafka, RabbitMQ, JMS listeners; MassTransit, Azure Service Bus (.NET)

---

### `get_component_metrics`

Retorna metricas detalladas de un componente especifico.

**Parametros:**

| Parametro | Tipo | Requerido | Descripcion |
|-----------|------|-----------|-------------|
| `projectPath` | string | Si | Ruta absoluta al directorio raiz del proyecto |
| `componentId` | string | Si | Nombre completo del tipo (fully qualified) |

**Formatos de `componentId`:**
- Java: `com.example.service.UserService`
- .NET: `ECommerceApp.Services.ProductService`

**Metricas incluidas:**
- CBO (Coupling Between Objects)
- LCOM (Lack of Cohesion in Methods)
- Capa arquitectonica
- Tablas de BD utilizadas
- Rol de mensajeria
- Grafo de llamadas

**Manejo de errores:** Si el componente no existe, retorna la lista de componentes disponibles.

## Decisiones de Diseno

### Artefacto Unico

CLI y MCP coexisten en un unico JAR. El flag `--mcp` activa el modo servidor:

```
java -jar tool.jar /path output.json   # Modo CLI
java -jar tool.jar --mcp               # Modo MCP
```

### MCP SDK v1.1.0

Se utiliza el SDK oficial de MCP (`io.modelcontextprotocol.sdk:mcp:1.1.0`):
- API sincrona (`McpSyncServer`) compatible con el pipeline de analisis bloqueante
- Transporte stdio nativo
- Jackson 3 interno que coexiste con Jackson 2 del proyecto (namespaces diferentes)

### Sin Framework

El servidor se implementa en Java puro sin Spring Boot ni otros frameworks:
- Footprint minimo del JAR
- Arranque instantaneo
- Sin dependencias transitivas innecesarias

### Logging a stderr

En modo MCP, stdout esta reservado exclusivamente para el protocolo JSON-RPC. Todo el logging SLF4J se redirige a stderr para evitar corrupcion del canal de comunicacion.

### AnalysisService Compartido

Ambos modos (CLI y MCP) delegan en `AnalysisService.java`, garantizando:
- Resultados identicos independientemente del modo
- Un unico lugar para corregir bugs o agregar funcionalidad
- Tests de paridad (`CliMcpParityTest`) que verifican la equivalencia

## Estructura de Archivos MCP

```
src/main/java/com/extractor/
├── MicroserviceInferenceMain.java    # Routing --mcp / CLI
├── mcp/
│   ├── McpServerMain.java           # Configuracion del servidor
│   ├── ToolSchemas.java             # Esquemas JSON de entrada
│   └── tools/
│       ├── AnalyzeProjectTool.java       # analyze_project
│       ├── GetDependencyGraphTool.java   # get_dependency_graph
│       ├── GetArchitectureTool.java      # get_architecture_proposal
│       ├── GetApiContractsTool.java      # get_api_contracts
│       └── GetComponentMetricsTool.java  # get_component_metrics
└── service/
    └── AnalysisService.java         # Logica compartida

src/test/java/com/extractor/
├── mcp/
│   └── McpServerContractTest.java   # Contrato del servidor
└── integration/
    └── CliMcpParityTest.java        # Paridad CLI/MCP
```

## Troubleshooting

### El servidor no inicia

**Causa:** JAR no compilado o Java < 17.

```bash
java -version        # Verificar Java 17+
mvn clean package    # Recompilar
```

### Timeout al analizar proyectos .NET

**Causa:** El binario `dotnet-analyzer` no esta en `target/` o las dependencias NuGet no estan restauradas.

```bash
# Compilar el analizador .NET
dotnet publish src/main/dotnet/DotNetAnalyzer/DotNetAnalyzer.csproj \
  -c Release -r osx-arm64 --self-contained -p:PublishSingleFile=true -o target

# Restaurar dependencias del proyecto destino
dotnet restore /path/to/target-project
```

### "Component not found"

**Causa:** El `componentId` no coincide con el nombre completo del tipo.

Usar `get_dependency_graph` primero para obtener la lista de componentes disponibles, luego usar el `id` exacto del componente deseado.

### Proyecto ambiguo (Java + .NET en el mismo directorio)

**Causa:** El directorio contiene marcadores de ambos tipos (`pom.xml` + `.csproj`).

**Solucion:** Ejecutar el analisis desde el subdirectorio especifico de cada stack.
