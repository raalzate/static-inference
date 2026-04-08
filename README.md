# Static Inference — Java & .NET Dependency Extractor con Motor de Inferencia de Microservicios

Herramienta de análisis estático que extrae dependencias de proyectos Java multi-módulo y .NET/C#, genera clusters inteligentes de componentes, y propone arquitecturas de microservicios con nombres de negocio y clasificación de viabilidad. Detecta automáticamente el tipo de proyecto (Java o .NET) a partir de los archivos de construcción presentes.

[![Ver video en YouTube](https://img.youtube.com/vi/m8U0r368jR8/maxresdefault.jpg)](https://www.youtube.com/watch?v=m8U0r368jR8)

## 🚀 Inicio Rápido

### Requisitos

- **Java 11+**
- **Maven 3.6+**
- **.NET 8.0 SDK** (solo para analizar proyectos .NET/C#)

### Instalación

```bash
# Clonar el repositorio
git clone <repository-url>
cd static-inference

# Compilar el proyecto Java
mvn clean compile

# Build .NET analyzer (required for .NET project analysis)
dotnet publish src/main/dotnet/DotNetAnalyzer/DotNetAnalyzer.csproj \
  -c Release -r osx-arm64 --self-contained -p:PublishSingleFile=true -o target
```

> Usa `-r osx-x64` en Mac Intel, `-r linux-x64` en Linux, `-r win-x64` en Windows.

### Ejecución

La herramienta detecta automáticamente si el proyecto destino es Java o .NET a partir de los archivos de construcción presentes (ver sección **Detección de Proyecto** más abajo).

```bash
mvn exec:java -Dexec.args="<ruta-proyecto> <archivo-salida.json>"
```

**Ejemplo — proyecto Java:**
```bash
mvn exec:java -Dexec.args="/home/runner/workspace/spring-boot-monolith output.json"
```

**Ejemplo — proyecto .NET (auto-detectado):**
```bash
mvn exec:java -Dexec.args="/path/to/dotnet-project output.json"
```

### Archivos Generados

La herramienta genera automáticamente **3 archivos JSON** especializados:

1. **`output.json`** - Grafo completo de dependencias con todos los componentes.
2. **`output_architecture.json`** - Propuesta consolidada de microservicios con clasificación de viabilidad.
3. **`output_entrypoints.json`** - Contratos de API (OpenAPI) extraídos de controladores y listeners.

## 📋 Referencia de Salidas

### 1. Grafo Completo (`output.json`)

Contiene el análisis exhaustivo de todos los componentes y sus relaciones:

```json
{
  "components": [
    {
      "id": "com.example.UserService",
      "layer": "Negocio",
      "tables_used": ["users"],
      "calls_out": ["com.example.UserRepository"],
      "metrics": {
         "cbo": 5,
         "lcom": 1.2
      }
    }
  ]
}
```


### 2. Arquitectura Consolidada (`output_architecture.json`)

Propuesta final de agrupación lógica:

```json
{
  "proposals": [
    {
      "id": 0,
      "name": "Microservicio de Item y Inventory",
      "viability": "Alta",
      "clusters": [0],
      "components": ["ItemEntity", "ItemRepo", "ItemService"],
      "metrics": {
        "size": 5,
        "cohesion_avg": 0.85,
        "external_coupling": 0.12,
        "data_jaccard": 0.8,
        "tables": ["item"]
      },
      "rationale": [
        "✅ Alta cohesión interna (85%) - componentes bien relacionados",
        "✅ Bajo acoplamiento externo (12%) - buena independencia",
        "✅ Tamaño adecuado (5 componentes)"
      ],
      "recommended_actions": [
        "✅ Diseñar como microservicio independiente",
        "✅ Definir API pública con contratos claros",
        "✅ Asignar base de datos exclusiva",
        "✅ Implementar patrones de resiliencia"
      ]
    }
  ],
  "support_libraries": [...],
  "viability_summary": {
    "alta": 2,
    "media": 1,
    "baja": 0
  }
}
```

**Clasificación de Viabilidad:**
- **Alta (≥0.7)**: Listos para implementar
- **Media (0.5-0.7)**: Requieren refactorización moderada
- **Baja (<0.5)**: Requieren refactorización profunda

**Métricas Consolidadas:**
- `cohesion_avg`: Promedio de cohesión de clusters consolidados
- `external_coupling`: Ratio de llamadas externas vs totales
- `data_jaccard`: Similitud de tablas compartidas (0-1)
- `internal_edge_density`: Densidad de conexiones internas

### 3. Contratos de API (`output_entrypoints.json`)

Endpoints REST y listeners extraídos automáticamente:

```json
{
  "endpoints": [
    {
      "id": "MovementController.registerMovement",
      "path": "/movimientos/crearMovimiento",
      "method": "POST",
      "parameters": [],
      "request_body_schema": "Movement",
      "response_schema": "Movement",
      "component_id": "banco_hexagonal.pruebatecnica.infraestructure.Adapter.Web.MovementController"
    }
  ],
  "schemas": {
    "Movement": {
      "type": "object",
      "properties": {
        "movementId": { "type": "long" },
        "movementType": { "type": "String" },
        "value": { "type": "Double" }
      }
    }
  }
}
```

**Tipos de Endpoints Detectados:**
- **REST**: `GET`, `POST`, `PUT`, `DELETE`, `PATCH`
- **Listeners**: `KAFKA_LISTEN`, `RABBIT_LISTEN`, `JMS_LISTEN`

## 📊 Estructura de Datos del Grafo

El sistema genera la información completa del grafo de arquitectura en el archivo `output_architecture.json`. Esta información puede ser consumida por herramientas de visualización o análisis posteriores.

### Interpretación del Grafo (`output_architecture.json`)

El archivo contiene dos niveles de grafos:

#### 1. Grafo de Dependencias de Paquetes (`project_metadata.package_dependencies`)
Representa la estructura física del proyecto como un grafo dirigido.

- **Nodos**: Claves del mapa (ej: `com.mx.ing.afore.constanciaLiq.bean`)
- **Aristas**: Lista `depends_on_packages`

```json
"com.mx.package.a": {
  "components_count": 5,
  "depends_on_packages": ["com.mx.package.b", "com.mx.package.c"]
}
```

#### 2. Grafo de Microservicios (`proposals`)
Representa la arquitectura lógica propuesta.

- **Nodos**: Objetos en la lista `proposals`
- **Contenido del Nodo**:
  - `id`: Identificador único del nodo
  - `clusters`: IDs de los clusters originales contenidos
  - `components`: Lista completa de clases Java en este nodo
  - `metrics`: Datos de cohesión y acoplamiento del nodo

**Relaciones implícitas**:
La conexión entre nodos de microservicios se infiere de las llamadas entre sus componentes internos (`components`).

### Integración con Herramientas Externas

Para visualizar estos grafos, se recomienda transformar el JSON a formatos estándar como:
- **Mermaid**: Generar diagramas de clases o componentes.
- **Graphviz/DOT**: Para visualización de dependencias complejas.
- **Gephi**: Para análisis de redes grandes.

## 📊 Interpretación de Resultados

### Lectura Rápida de Viabilidad

```
✅ Alta viabilidad (≥0.7)
   → Implementar directamente como microservicio
   → API bien definida, BD independiente, resiliencia

⚠️ Media viabilidad (0.5-0.7)
   → Refactorizar antes de implementar
   → Mejorar cohesión o reducir acoplamiento

❌ Baja viabilidad (<0.5)
   → Requiere rediseño profundo
   → Consolidar con otros clusters o replantear
```

### Ejemplo de Salida en Consola

```
PROPUESTA DE ARQUITECTURA DE MICROSERVICIOS
═══════════════════════════════════════════════

📋 Microservicios Propuestos:
─────────────────────────────
• Microservicio de Item y Inventory → Clusters 0 (5 componentes)
• Microservicio de Order → Clusters 1 (5 componentes)
• Microservicio de Customer → Clusters 2 (5 componentes)

📚 Librerías de Soporte:
────────────────────────
• Microservicio de Aplicación Principal → Clusters 3

📌 Conclusión de Viabilidad:
────────────────────────────
✅ Alta viabilidad: 2 microservicio(s) - Listos para implementar
⚠️ Media viabilidad: 0 microservicio(s) - Requieren refactorización moderada
❌ Baja viabilidad: 1 microservicio(s) - Requieren refactorización profunda
```

## 🎯 Casos de Uso

### 1. Análisis de Monolito para Migración

```bash
mvn exec:java -Dexec.args="/path/to/legacy-monolith migration_analysis.json"
```

Genera propuestas de microservicios con viabilidad clasificada.

### 2. Evaluación de Arquitectura Existente

```bash
mvn exec:java -Dexec.args="/path/to/current-system evaluation.json"
```

Identifica oportunidades de mejora en cohesión y acoplamiento.

### 3. Auditoría de Dependencias

Revisa `output.json` para mapear todas las dependencias entre componentes y librerías externas.

## 🔧 Características Principales

### Motor de Análisis de Código (Spoon — Java)
- **Análisis de AST**: Parsea el código fuente sin necesidad de compilarlo.
- **Soporte Multi-módulo**: Recorre recursivamente árboles de directorios.
- **Procesamiento de Anotaciones**: Soporte completo para Lombok y otros procesadores.
- **Análisis Estático de Patrones**: Detecta bugs (comparación de Strings), errores de gestión de excepciones y desviaciones de estilo (nomenclatura de paquetes, hardcoded literals).

### Motor de Análisis de Código (Roslyn — .NET/C#)
- **Auto-detección**: Activado cuando el proyecto contiene un archivo `.sln` o `.csproj`.
- **Ejecutable nativo**: El binario `dotnet-analyzer` (publicado en `target/`) es invocado por el proceso Java.
- **10 pasadas de análisis**:
  1. Tipos (clases, interfaces, enums)
  2. Invocaciones entre componentes
  3. Inyección de Dependencias (DI container registrations)
  4. Endpoints ASP.NET Core (`[HttpGet]`, `[HttpPost]`, etc.)
  5. Tablas Entity Framework (`DbSet<T>`, `[Table]`)
  6. Métricas CBO y LCOM
  7. Clasificación de capas arquitectónicas
  8. Mensajería MassTransit y Azure Service Bus
  9. Secretos e IConfiguration
  10. Consolidación y serialización del grafo

### Modelo de Análisis Basado en Componentes
Cada clase, interfaz o enum se modela como un "componente" con metadatos ricos:
- **Clasificación de Capas**: Asignación automática a capas arquitectónicas.
- **Métricas de Código**: CBO (Acoplamiento) y LCOM (Cohesión).
- **Relaciones**: Llamadas a métodos, herencia e implementación de interfaces.
- **Dependencias Externas**: Mapeo a coordenadas Maven/Gradle.
- **Detección de Sistemas**: Bases de datos, mensajería, datos sensibles.

## 🔍 Detección de Proyecto

La herramienta inspecciona el directorio raíz del proyecto antes de lanzar el analizador:

| Archivos encontrados | Motor seleccionado |
|---|---|
| `.sln` o `.csproj` | .NET (Roslyn) |
| `pom.xml` o `build.gradle` | Java (Spoon) |
| Ambos | Error `AMBIGUOUS_PROJECT_TYPE` |
| Ninguno | Java (Spoon) — compatibilidad con versiones anteriores |

Si el proyecto resulta ambiguo, mueve o elimina los archivos de construcción del tipo que no quieres analizar antes de ejecutar la herramienta.

## 🏗️ Arquitectura y Clasificación

### Sistema de Clasificación de Capas
El `LayerClassifier` categoriza componentes analizando su nombre, anotaciones y dependencias:

#### Capas Core
- **Controlador**: Endpoints REST, Struts Actions, JSF Beans.
- **Negocio**: Servicios, lógica de negocio principal.
- **Compartida**: Utilidades, configuraciones, clientes externos, factories.
- **Web**: Componentes basados en Servlets.

#### Capas de Datos Refinadas
- **Persistencia**: Capa de acceso a datos (`@Repository`, DAOs, JDBC calls).
- **Dominio**: Objetos de dominio o entidades de negocio.
- **Transferencia**: DTOs (Data Transfer Objects).

### Detección de Acceso a Base de Datos
Estrategia multi-nivel para identificar interacciones con BD:

1. **JDBC**: Detección de `java.sql` (`PreparedStatement`, `executeQuery`) y extracción de nombres de tablas de strings SQL.
2. **iBatis/MyBatis**: Detección de `SqlSession`, `SqlSessionFactory` y mappers.
3. **JPA/Hibernate**: Anotaciones `@Entity`, `@Table`, uso de `EntityManager`.
4. **Spring Data**: Interfaces `JpaRepository`, `CrudRepository`.

### Detección de Sistemas de Mensajería
Identifica componentes que actúan como productores o consumidores:
- **Tipos**: JMS, Kafka, RabbitMQ, ActiveMQ.
- **Roles**: Publisher, Consumer.

### Detección de Datos Sensibles (PII & Secretos)
Escaneo basado en patrones para seguridad:
- **Métodos**: Búsqueda de keywords (password, token, secret) en variables y literales.
- **Prevención de Falsos Positivos**: 
  - Exclusión automática de endpoints REST (ej: `/getAuthToken`).
  - Validación de rutas URL vs secretos reales (Base64, UUIDs).

## 📊 Métricas de Calidad de Código

### CBO (Coupling Between Objects)
Mide el número de clases únicas a las que un componente está acoplado.
- **Fuentes**: Llamadas a métodos, constructores, campos, parámetros, herencia.
- **Umbrales de Viabilidad**:
  - **≤ 5**: Bajo acoplamiento (Ideal)
  - **≤ 10**: Acoplamiento moderado (Aceptable)
  - **> 10**: Alto acoplamiento (Problemático)

### LCOM (Lack of Cohesion in Methods)
Usa la fórmula LCOM-HS (Henderson-Sellers) para medir la cohesión basada en uso de campos compartidos.
- **Escala**: 0 = Alta cohesión, 1 = Baja cohesión.
- **Umbrales de Viabilidad**:
  - **≤ 0.3**: Alta cohesión (Ideal - Clases bien enfocadas)
  - **≤ 0.6**: Cohesión moderada (Aceptable)
  - **> 0.6**: Baja cohesión (Problemática - Posible "God Class")

### Cálculo de Viabilidad de Microservicios
Fórmula ponderada para determinar si un clúster es viable como microservicio:

```java
score = 0.5 * adjustedCohesion + 
        0.35 * (1 - externalCoupling) + 
        0.15 * dataCohesion
```

### Motor de Inferencia de Microservicios
- **Clustering automático** por dominio y tablas compartidas
- **Consolidación inteligente** con señales múltiples (tablas, llamadas, tokens, eventos)
- **Generación de nombres** de negocio automática
- **Clasificación de viabilidad** (Alta/Media/Baja)

## 📏 Métricas de Precisión

La herramienta calcula automáticamente dos métricas de precisión que se incluyen en la sección `meta` del archivo `output.json`:

### 1. Dependency Accuracy (Precisión de Dependencias)

Mide la calidad de la detección de dependencias en el código:

```json
"dependency_accuracy": {
  "overall_score": 0.65,
  "coverage_score": 0.75,
  "precision_score": 0.80,
  "depth_score": 0.60,
  "details": {
    "total_components": 50,
    "components_with_calls": 35,
    "components_with_db": 12,
    "components_with_sensitive": 5,
    "total_structural_deps": 120,
    "total_calls": 150
  }
}
```

**Componentes de la métrica:**
- **Coverage Score**: Porcentaje de componentes con llamadas a métodos detectadas
- **Precision Score**: Ratio entre llamadas a métodos detectadas y dependencias estructurales (edges del grafo). Valores altos indican que se detectaron muchas llamadas en relación a las dependencias estructurales
- **Depth Score**: Nivel de análisis profundo (llamadas + BD + datos sensibles) / total componentes
- **Overall Score**: Promedio ponderado de las tres métricas anteriores (40% coverage, 40% precision, 20% depth)

**Interpretación:**
- **≥0.7**: Excelente cobertura de dependencias
- **0.5-0.7**: Buena cobertura, algunas dependencias pueden faltar
- **<0.5**: Cobertura limitada, considerar revisar manualmente

### 2. Decomposition Accuracy (Calidad de Descomposición)

Evalúa la calidad de la agrupación en clusters y microservicios:

```json
"decomposition_accuracy": {
  "overall_score": 0.72,
  "modularity_score": 0.68,
  "cohesion_score": 0.75,
  "coupling_score": 0.25,
  "balance_score": 0.82,
  "details": {
    "cluster_count": 4,
    "avg_cluster_size": 8.5,
    "min_cluster_size": 3,
    "max_cluster_size": 15,
    "clusters_with_high_cohesion": 3,
    "clusters_with_low_coupling": 3
  }
}
```

**Componentes de la métrica:**
- **Modularity Score**: Ratio de conexiones internas vs totales (Q de Newman)
- **Cohesion Score**: Promedio de cohesión de todos los clusters
- **Coupling Score**: Promedio de acoplamiento externo de clusters
- **Balance Score**: Uniformidad en el tamaño de clusters (coeficiente de variación)
- **Overall Score**: Combinación ponderada (30% modularidad, 30% cohesión, 30% bajo acoplamiento, 10% balance)

**Interpretación:**
- **≥0.7**: Excelente descomposición en microservicios
- **0.5-0.7**: Descomposición aceptable, revisar clusters con baja cohesión
- **<0.5**: Descomposición problemática, considerar re-clustering

**Uso práctico:**
- Usar `dependency_accuracy` para validar que el análisis detectó suficientes dependencias
- Usar `decomposition_accuracy` para evaluar si la propuesta de microservicios es viable
- Scores bajos indican necesidad de refactorización antes de migrar a microservicios

## 🧮 Algoritmos del Motor de Inferencia

### 1. InterClusterGraph
Calcula relaciones entre clusters usando 4 señales:

```
evidenceScore = 0.25 * tableJaccard + 
                0.35 * callDensity + 
                0.30 * tokenSimilarity + 
                0.10 * eventLinks
```

- **tableJaccard**: Similitud de tablas compartidas (índice de Jaccard)
- **callDensity**: Densidad normalizada de llamadas entre clusters
- **tokenSimilarity**: Similitud de tokens de dominio (Jaccard)
- **eventLinks**: Acoplamiento por eventos (publisher→listener)

### 2. ClusterConsolidator
Algoritmo greedy que agrupa clusters con:
- **Umbral de evidencia**: ≥0.65 con ≥2 señales fuertes
- **Guardrails de tamaño**: Evita grupos >40 componentes (excepto alta similitud)
- **Separación de soporte**: No mezcla infraestructura (≥80%) con negocio
- **Preservación de candidatos**: No fusiona candidatos fuertes débilmente acoplados

### 3. MicroserviceNameGenerator
Genera nombres automáticos:
- **Infraestructura**: "Microservicio de Seguridad & Configuración"
- **Negocio**: "Microservicio de Item y Inventory"
- **Exclusiones**: 30+ tokens técnicos (api, rest, dto, entity, etc.)

### 4. ViabilityScorer
Clasifica propuestas:

```
viabilityScore = 0.5 * cohesionAdj + 
                 0.35 * (1 - externalCoupling) + 
                 0.15 * dataCohesion
```

- **Alta (≥0.7)**: Implementar directamente
- **Media (0.5-0.7)**: Refactorizar moderadamente
- **Baja (<0.5)**: Rediseño profundo necesario

Ver [ALGORITHMS.md](ALGORITHMS.md) para detalles técnicos completos.

## 🔧 Configuración Avanzada

### Personalización de Consolidación

Los parámetros pueden ajustarse en el código fuente:

**Pesos de señales** (`InterClusterGraph.java`):
```java
private static final double TABLE_WEIGHT = 0.25;
private static final double CALL_WEIGHT = 0.35;
private static final double TOKEN_WEIGHT = 0.30;
private static final double EVENT_WEIGHT = 0.10;
```

**Umbrales de viabilidad** (`ViabilityScorer.java`):
```java
private static final double HIGH_VIABILITY = 0.7;
private static final double MEDIUM_VIABILITY = 0.5;
```

**Tokens excluidos** (`MicroserviceNameGenerator.java`):
```java
private static final Set<String> EXCLUDE_TOKENS = Set.of(
    "entity", "dto", "api", "rest", "service", ...
);
```

Ver [CONFIGURATION.md](CONFIGURATION.md) para guía completa de personalización.

## 🛠️ Troubleshooting

### No se generan componentes

**Causa**: Proyecto sin código Java válido o estructura no reconocida

**Solución**: 
- Verificar carpetas `src/main/java` existan
- Revisar logs de Spoon para errores de parsing

### Nombres genéricos (ej: "Microservicio de Entity")

**Causa**: Componentes solo tienen tokens técnicos excluidos

**Solución**:
- Revisar `MicroserviceNameGenerator.EXCLUDE_TOKENS`
- Asegurar nombres de clases con dominios de negocio claros

### Todos los microservicios con baja viabilidad

**Causa**: Monolito altamente acoplado sin separación clara

**Solución**:
- Revisar recomendaciones de refactorización
- Desacoplar componentes antes de migrar
- Considerar arquitectura hexagonal para separación

### Muchos nano-servicios generados

**Causa**: Componentes muy granulares o desconectados

**Solución**:
- Revisar recomendación de fusionar nano-servicios
- Analizar si componentes pequeños pertenecen a contextos mayores

### dotnet-analyzer not found

**Causa**: El binario del analizador .NET no fue compilado o no se encuentra en `target/`

**Solución**:
```bash
dotnet publish src/main/dotnet/DotNetAnalyzer/DotNetAnalyzer.csproj \
  -c Release -r osx-arm64 --self-contained -p:PublishSingleFile=true -o target
```
Asegúrate de que el binario resultante quede junto al JAR en el directorio `target/`.

### 0 componentes en proyecto .NET

**Causa**: Dependencias NuGet no restauradas; Roslyn no puede resolver los tipos

**Solución**:
```bash
dotnet restore <ruta-al-proyecto>
```
Ejecuta `dotnet restore` en el proyecto destino antes de lanzar el análisis.

### Tipo de proyecto ambiguo (Ambiguous project type)

**Causa**: El directorio contiene a la vez marcadores Java (`pom.xml` / `build.gradle`) y .NET (`.sln` / `.csproj`)

**Solución**:
- Si solo quieres analizar el lado Java, elimina o mueve los archivos `.sln` / `.csproj` del directorio raíz.
- Si solo quieres analizar el lado .NET, elimina o mueve `pom.xml` / `build.gradle`.
- Si el repositorio contiene ambos stacks de forma intencional, ejecuta la herramienta desde el subdirectorio específico de cada stack.

## 📚 Documentación Adicional

- [ALGORITHMS.md](ALGORITHMS.md) - Deep dive técnico en algoritmos de consolidación
- [CONFIGURATION.md](CONFIGURATION.md) - Guía de configuración y personalización

## 🤝 Contribución

Para extender el sistema:

1. **Añadir nuevas señales** en `InterClusterGraph`
2. **Personalizar reglas** de consolidación en `ClusterConsolidator`
3. **Agregar categorías** de diseño en `MicroserviceRecommendationEngine`
4. **Extender detección** de patrones en `DatabaseDetector` o `SensitiveDataDetector`

## 📝 Estructura del Proyecto

```
src/main/java/com/extractor/
├── MicroserviceInferenceMain.java     # Punto de entrada principal
├── analyzer/                          # Motor de análisis de código
│   ├── ProjectAnalyzer.java           # Análisis Spoon de AST
│   ├── MetricsCalculator.java         # Cálculo de CBO y LCOM
│   ├── PackageGroupAnalyzer.java      # Agrupación de dependencias por paquete
│   ├── ComponentRegistry.java         # Registro de componentes
│   ├── EdgeAccumulator.java           # Acumulador de relaciones
│   ├── TableNameExtractor.java        # Extracción de nombres de tablas
│   ├── ClassNameValidator.java        # Validación de nombres de clases
│   ├── SpoonLauncherFactory.java      # Fábrica de configuración Spoon
│   ├── StaticCodeAnalyzer.java        # Analizador de patrones de código y estilo
│   └── SourcePathDiscoverer.java      # Descubridor de rutas fuente
├── constants/                         # Constantes del sistema
│   └── LayerConstants.java            # Constantes de capas
├── inference/                         # Motor de inferencia de microservicios
│   ├── InferenceEngine.java           # Motor principal de clustering
│   ├── ClusteringAlgorithm.java       # Algoritmo de clustering
│   ├── InterClusterGraph.java         # Cálculo de relaciones entre clusters
│   ├── ClusterConsolidator.java       # Consolidación greedy de clusters
│   ├── MicroserviceNameGenerator.java # Generación de nombres de negocio
│   ├── ViabilityScorer.java           # Clasificación de viabilidad
│   ├── MicroserviceRecommendationEngine.java  # Recomendaciones
│   ├── LayerClassifier.java           # Clasificación de capas arquitectónicas
│   ├── Cluster.java                   # Modelo de cluster
│   ├── ClusterMetrics.java            # Métricas de clusters
│   ├── ClusterExplanation.java        # Explicaciones de clusters
│   ├── ExplanationGenerator.java      # Generador de explicaciones
│   ├── MicroserviceProposal.java      # Propuesta consolidada
│   ├── MicroserviceCandidates.java    # Candidatos a microservicios
│   ├── ConsolidatedArchitecture.java  # Arquitectura final
│   ├── InferenceRule.java             # Reglas de inferencia
│   ├── MetricsCalculator.java         # Calculador de métricas
│   └── rules/                         # Reglas de inferencia
│       ├── DomainAffinityRule.java    # Afinidad de dominio
│       ├── SharedTableRule.java       # Regla de tablas compartidas
│       └── CallPatternRule.java       # Regla de patrones de llamadas
├── model/                             # Modelos de datos
│   ├── DependencyGraph.java           # Modelo de grafo de dependencias
│   ├── Component.java                 # Modelo de componente
│   ├── Edge.java                      # Modelo de arista
│   ├── EdgeData.java                  # Datos de arista
│   ├── CallInfo.java                  # Información de llamadas
│   ├── DependencyInfo.java            # Información de dependencias
│   ├── PackageGroup.java              # Agrupación de paquetes
│   ├── CodeIssue.java                 # Modelo de hallazgos de análisis estático
│   └── WebArchitecture.java           # Arquitectura web
└── utils/                             # Utilidades de detección
    ├── DatabaseDetector.java          # Detección de BD
    ├── DependencyResolver.java        # Resolución de dependencias
    ├── SensitiveDataDetector.java     # Detección de datos sensibles
    ├── SecretsDetector.java           # Detección de secretos
    ├── MessagingDetector.java         # Detección de mensajería
    └── EJBDetector.java               # Detección de EJBs

src/main/dotnet/                       # Analizador .NET (Roslyn)
│   ├── DotNetAnalyzer.sln
│   └── DotNetAnalyzer/
│       ├── Analysis/                  # TypeAnalyzer, EndpointExtractor, etc.
│       ├── Model/                     # Mirrors en C# de los modelos Java
│       └── Program.cs

src/test/dotnet/                       # Tests xUnit
│   └── DotNetAnalyzer.Tests/

test-for-demo/dotnet-ecommerce         # Proyecto .NET de demo
```

