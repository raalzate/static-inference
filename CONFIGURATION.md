# Guía de Configuración y Personalización

Este documento explica cómo personalizar el comportamiento del motor de inferencia de microservicios ajustando parámetros, umbrales y listas de exclusión.

## Parámetros Configurables

### 1. Pesos de Señales de Consolidación

**Ubicación**: `src/main/java/com/extractor/inference/InterClusterGraph.java`

```java
// Pesos para cálculo de evidenceScore
private static final double TABLE_WEIGHT = 0.25;    // Peso de tablas compartidas
private static final double CALL_WEIGHT = 0.35;     // Peso de densidad de llamadas
private static final double TOKEN_WEIGHT = 0.30;    // Peso de similitud de tokens
private static final double EVENT_WEIGHT = 0.10;    // Peso de enlaces de eventos
```

**Valores por defecto**:
- `TABLE_WEIGHT = 0.25` (25%)
- `CALL_WEIGHT = 0.35` (35%)
- `TOKEN_WEIGHT = 0.30` (30%)
- `EVENT_WEIGHT = 0.10` (10%)

**Cuándo modificar**:
- **Aumentar TABLE_WEIGHT** si el proyecto tiene clara separación por tablas de BD
- **Aumentar CALL_WEIGHT** si las llamadas son el principal indicador de cohesión
- **Aumentar TOKEN_WEIGHT** si los nombres de clases reflejan bien los dominios
- **Aumentar EVENT_WEIGHT** para arquitecturas event-driven

**Ejemplo: Arquitectura orientada a datos**
```java
private static final double TABLE_WEIGHT = 0.40;  // ↑ Priorizar tablas
private static final double CALL_WEIGHT = 0.25;   // ↓
private static final double TOKEN_WEIGHT = 0.25;  // ↓
private static final double EVENT_WEIGHT = 0.10;  // =
```

### 2. Umbrales de Consolidación

**Ubicación**: `src/main/java/com/extractor/inference/ClusterConsolidator.java`

```java
// Umbral de evidencia para consolidar
private static final double EVIDENCE_THRESHOLD = 0.65;

// Mínimo de señales fuertes requeridas
private static final int MIN_STRONG_SIGNALS = 2;

// Umbral para señal fuerte
private static final double STRONG_SIGNAL_THRESHOLD = 0.5;

// Tamaño máximo sin alta similitud
private static final int MAX_SIZE_WITHOUT_HIGH_SIMILARITY = 40;

// Umbral de similitud para permitir tamaño grande
private static final double HIGH_TOKEN_SIMILARITY = 0.75;

// Ratio para detectar librería de soporte
private static final double SUPPORT_RATIO = 0.8;
```

**Valores por defecto**:
- `EVIDENCE_THRESHOLD = 0.65` (65%)
- `MIN_STRONG_SIGNALS = 2`
- `MAX_SIZE_WITHOUT_HIGH_SIMILARITY = 40` componentes
- `HIGH_TOKEN_SIMILARITY = 0.75` (75%)
- `SUPPORT_RATIO = 0.8` (80%)

**Cuándo modificar**:

**Para consolidación más agresiva** (menos microservicios, más grandes):
```java
private static final double EVIDENCE_THRESHOLD = 0.55;  // ↓ Más permisivo
private static final int MIN_STRONG_SIGNALS = 1;        // ↓ Menos restrictivo
private static final int MAX_SIZE_WITHOUT_HIGH_SIMILARITY = 60;  // ↑
```

**Para consolidación más conservadora** (más microservicios, más pequeños):
```java
private static final double EVIDENCE_THRESHOLD = 0.75;  // ↑ Más estricto
private static final int MIN_STRONG_SIGNALS = 3;        // ↑ Más exigente
private static final int MAX_SIZE_WITHOUT_HIGH_SIMILARITY = 25;  // ↓
```

### 3. Clasificación de Viabilidad

**Ubicación**: `src/main/java/com/extractor/inference/ViabilityScorer.java`

```java
// Pesos para cálculo de viabilidad
private static final double COHESION_WEIGHT = 0.5;
private static final double COUPLING_WEIGHT = 0.35;
private static final double DATA_WEIGHT = 0.15;

// Umbrales de viabilidad
private static final double HIGH_VIABILITY_THRESHOLD = 0.7;
private static final double MEDIUM_VIABILITY_THRESHOLD = 0.5;

// Penalizaciones (Multiplicadores)
private static final double SMALL_SIZE_PENALTY = 0.6;  // Multiplicador si size < 3
private static final double MONOLITH_PENALTY = 0.7;    // Multiplicador si size > 50 y densidad < 0.5
private static final int MIN_SIZE = 3;
private static final int MAX_SIZE = 50;
private static final double MIN_DENSITY = 0.5;
```

**Valores por defecto**:
- `COHESION_WEIGHT = 0.5` (50%)
- `COUPLING_WEIGHT = 0.35` (35%)
- `DATA_WEIGHT = 0.15` (15%)
- `HIGH_VIABILITY_THRESHOLD = 0.7` (70%)
- `MEDIUM_VIABILITY_THRESHOLD = 0.5` (50%)
- `SMALL_SIZE_PENALTY = 0.6` (Reduce score al 60%)
- `MONOLITH_PENALTY = 0.7` (Reduce score al 70%)

**Cuándo modificar**:

**Para priorizar bajo acoplamiento**:
```java
private static final double COHESION_WEIGHT = 0.4;   // ↓
private static final double COUPLING_WEIGHT = 0.45;  // ↑ Más importante
private static final double DATA_WEIGHT = 0.15;      // =
```

**Para clasificación más estricta**:
```java
private static final double HIGH_VIABILITY_THRESHOLD = 0.8;   // ↑
private static final double MEDIUM_VIABILITY_THRESHOLD = 0.6; // ↑
```

### 4. Recomendaciones de Diseño

**Ubicación**: `src/main/java/com/extractor/inference/MicroserviceRecommendationEngine.java`

```java
// Umbrales para candidatos fuertes
private static final double STRONG_COHESION = 0.7;
private static final double STRONG_COUPLING_MAX = 0.3;
private static final int STRONG_MIN_SIZE = 3;

// Umbrales para nano-servicios
private static final int NANO_MAX_SIZE = 2;
```

**Valores por defecto**:
- `STRONG_COHESION = 0.7` (70%)
- `STRONG_COUPLING_MAX = 0.3` (30%)
- `STRONG_MIN_SIZE = 3` componentes
- `NANO_MAX_SIZE = 2` componentes

**Cuándo modificar**:

**Para estándares más exigentes**:
```java
private static final double STRONG_COHESION = 0.8;        // ↑ Cohesión más alta
private static final double STRONG_COUPLING_MAX = 0.2;    // ↓ Acoplamiento más bajo
private static final int STRONG_MIN_SIZE = 5;             // ↑ Tamaño mínimo mayor
```

### 5. Tokens Excluidos en Nombres

**Ubicación**: `src/main/java/com/extractor/inference/MicroserviceNameGenerator.java`

```java
private static final Set<String> EXCLUDE_TOKENS = Set.of(
    // Architectural patterns
    "entity", "model", "data", "dto", "event", "command", "query",
    
    // Implementation details  
    "impl", "repository", "service", "controller",
    
    // Technical terms
    "api", "rest", "http", "adapter", "port",
    
    // Persistence
    "jpa", "repo", "dao", "operations",
    
    // Event-driven
    "listener", "publisher", "handler",
    
    // Infrastructure
    "factory", "db", "usecase"
);
```

**Cuándo modificar**:
- **Agregar tokens** si aparecen nombres genéricos no deseados
- **Remover tokens** si son significativos para tu dominio

**Ejemplo: Dominio financiero**
```java
private static final Set<String> EXCLUDE_TOKENS = Set.of(
    "entity", "dto", "service", "repository",
    // ... tokens estándar ...
    
    // Agregar tokens técnicos específicos del dominio
    "transaction", "ledger", "journal"  // Si son muy comunes
);
```

**Ejemplo: Preservar patrón DDD**
```java
// REMOVER "aggregate" de exclusiones si es significativo
private static Set<String> getExcludeTokens() {
    Set<String> tokens = new HashSet<>(EXCLUDE_TOKENS);
    tokens.remove("aggregate");  // Preservar en nombres
    return tokens;
}
```

### 6. Keywords de Infraestructura

**Ubicación**: `src/main/java/com/extractor/inference/MicroserviceNameGenerator.java`

```java
private static final Map<String, String> INFRA_KEYWORDS = Map.of(
    "config", "Configuración",
    "security", "Seguridad",
    "auth", "Autenticación",
    "swagger", "Documentación API",
    "exception", "Manejo de Errores"
);
```

**Ubicación adicional**: `src/main/java/com/extractor/inference/ClusterConsolidator.java`

```java
Set<String> supportKeywords = Set.of(
    "application", "config", "configuration",
    "security", "auth", "swagger", "main",
    "exception", "error", "filter",
    "interceptor", "aspect", "openapi"
);
```

**Cuándo modificar**:
- Agregar keywords específicos de tu stack tecnológico
- Agregar componentes cross-cutting de tu arquitectura

**Ejemplo: Stack Spring Cloud**
```java
Set<String> supportKeywords = Set.of(
    // ... keywords existentes ...
    
    // Spring Cloud
    "eureka", "zuul", "ribbon", "hystrix",
    "feign", "gateway", "discovery"
);
```
### 7. Constantes de Análisis (AnalysisConstants)

**Ubicación**: `src/main/java/com/extractor/constants/AnalysisConstants.java`

Esta clase centraliza patrones y listas de palabras clave para la detección de componentes y datos sensibles.

```java
// Detección de Datos Sensibles
public static final Set<String> SENSITIVE_KEYWORDS = Set.of(
    "password", "ssn", "creditcard", "apikey", "secretkey", "token", "auth"
);

// Patrones SQL
public static final Pattern SQL_TABLE_PATTERN = Pattern.compile(
    "\\b(?:FROM|JOIN|INTO|UPDATE)\\s+([a-zA-Z0-9_]+)", Pattern.CASE_INSENSITIVE
);

// Annotaciones JPA/Spring
public static final Set<String> SPRING_REPO_INTERFACES = Set.of(...);
public static final Set<String> JPA_ENTITY_ANNOTATIONS = Set.of(...);
```

**Cuándo modificar**:
- **Agregar nuevos keywords sensibles**: Para cumplir con políticas de seguridad específicas.
- **Ajustar patrones SQL**: Si se usan dialectos SQL no estándar.
- **Soportar nuevas anotaciones**: Si se migra a Jakarta EE 10+ o nuevas versiones de Spring.

> [!IMPORTANT]
> Cualquier cambio en `AnalysisConstants.java` requiere re-compilar el proyecto (`mvn clean package`) para que tenga efecto, ya que son constantes en tiempo de compilación.
## Casos de Uso de Configuración

### Caso 1: Arquitectura Hexagonal

**Problema**: Adaptadores detectados como clusters separados

**Solución**: Aumentar peso de tokens, reducir umbral
```java
// InterClusterGraph.java
private static final double TOKEN_WEIGHT = 0.45;  // ↑ de 0.30
private static final double CALL_WEIGHT = 0.30;   // ↓ de 0.35

// ClusterConsolidator.java  
private static final double EVIDENCE_THRESHOLD = 0.60;  // ↓ de 0.65
```

### Caso 2: Microservicios Event-Driven

**Problema**: Eventos no detectan relación fuerte

**Solución**: Aumentar peso de eventos
```java
// InterClusterGraph.java
private static final double EVENT_WEIGHT = 0.25;  // ↑ de 0.10
private static final double CALL_WEIGHT = 0.25;   // ↓ de 0.35
private static final double TOKEN_WEIGHT = 0.25;  // ↓ de 0.30
private static final double TABLE_WEIGHT = 0.25;  // = 0.25
```

### Caso 3: Monolito con Muchas Dependencias

**Problema**: Todos clasificados como baja viabilidad

**Solución**: Ajustar pesos de viabilidad, reducir penalización por acoplamiento
```java
// ViabilityScorer.java
private static final double COHESION_WEIGHT = 0.6;   // ↑ de 0.5
private static final double COUPLING_WEIGHT = 0.25;  // ↓ de 0.35
private static final double DATA_WEIGHT = 0.15;      // = 0.15
```

### Caso 4: Proyectos Pequeños

**Problema**: Muchos nano-servicios generados

**Solución**: Reducir tamaño mínimo
```java
// MicroserviceRecommendationEngine.java
private static final int STRONG_MIN_SIZE = 2;  // ↓ de 3
private static final int NANO_MAX_SIZE = 1;    // ↓ de 2
```

## Extensibilidad: Agregar Nuevas Señales

### Paso 1: Definir Nueva Señal en InterClusterGraph

```java
private double calculateCustomSignal(int clusterA, int clusterB) {
    // Tu lógica personalizada
    // Retornar valor [0, 1]
    return customValue;
}
```

### Paso 2: Integrar en Evidence Score

```java
private static final double CUSTOM_WEIGHT = 0.10;

double evidenceScore = TABLE_WEIGHT * tableJaccard +
                       CALL_WEIGHT * callDensity +
                       TOKEN_WEIGHT * tokenSimilarity +
                       EVENT_WEIGHT * eventLinks +
                       CUSTOM_WEIGHT * customSignal;  // Nueva señal
```

### Paso 3: Ajustar Pesos (suma = 1.0)

```java
private static final double TABLE_WEIGHT = 0.20;   // ↓ de 0.25
private static final double CALL_WEIGHT = 0.30;    // ↓ de 0.35
private static final double TOKEN_WEIGHT = 0.25;   // ↓ de 0.30
private static final double EVENT_WEIGHT = 0.10;   // = 0.10
private static final double CUSTOM_WEIGHT = 0.15;  // Nueva
```

## Variables de Entorno (Futuro)

**Actualmente no soportado**, pero aquí está el diseño recomendado:

```java
// InterClusterGraph.java
private static final double TABLE_WEIGHT = 
    Double.parseDouble(System.getenv().getOrDefault("TABLE_WEIGHT", "0.25"));
```

**Uso**:
```bash
export TABLE_WEIGHT=0.30
export CALL_WEIGHT=0.40
export TOKEN_WEIGHT=0.20
export EVENT_WEIGHT=0.10

mvn exec:java -Dexec.args="/path/to/project output.json"
```

## Archivo de Configuración (Futuro)

**Propuesta de configuración via JSON**:

```json
{
  "consolidation": {
    "weights": {
      "table": 0.25,
      "call": 0.35,
      "token": 0.30,
      "event": 0.10
    },
    "thresholds": {
      "evidence": 0.65,
      "minStrongSignals": 2,
      "maxSize": 40
    }
  },
  "viability": {
    "weights": {
      "cohesion": 0.5,
      "coupling": 0.35,
      "data": 0.15
    },
    "thresholds": {
      "high": 0.7,
      "medium": 0.5
    }
  },
  "naming": {
    "excludeTokens": ["entity", "dto", "service"],
    "infraKeywords": {
      "config": "Configuración",
      "security": "Seguridad"
    }
  }
}
```

**Carga**:
```bash
mvn exec:java -Dexec.args="/path/to/project output.json --config config.json"
```

## Mejores Prácticas

### 1. Validación de Configuración
Siempre verificar que:
```
TABLE_WEIGHT + CALL_WEIGHT + TOKEN_WEIGHT + EVENT_WEIGHT = 1.0
COHESION_WEIGHT + COUPLING_WEIGHT + DATA_WEIGHT = 1.0
```

### 2. Iteración y Ajuste
1. Ejecutar con configuración por defecto
2. Revisar resultados en `*_architecture.json`
3. Identificar problemas (ej: muchos nano-servicios)
4. Ajustar un parámetro a la vez
5. Re-ejecutar y comparar resultados

### 3. Documentar Cambios
Mantener registro de configuraciones usadas:

```markdown
## Configuración para Proyecto X
- TABLE_WEIGHT: 0.30 (↑ por fuerte separación de datos)
- CALL_WEIGHT: 0.30 (↓)
- EVIDENCE_THRESHOLD: 0.60 (↓ para consolidar más)
- Razón: Arquitectura orientada a datos con clara separación por BDs
```

### 4. Testing
Probar con proyectos de referencia antes de aplicar a producción:
```bash
# Configuración original
mvn exec:java -Dexec.args="/path/to/test-project baseline.json"

# Configuración modificada
# (modificar código con nuevos parámetros)
mvn exec:java -Dexec.args="/path/to/test-project modified.json"

# Comparar resultados
diff baseline_architecture.json modified_architecture.json
```

## Troubleshooting de Configuración

### Problema: No se consolida nada

**Causa**: Umbral de evidencia muy alto

**Solución**:
```java
private static final double EVIDENCE_THRESHOLD = 0.50;  // ↓ de 0.65
private static final int MIN_STRONG_SIGNALS = 1;        // ↓ de 2
```

### Problema: Todo se consolida en un mega-servicio

**Causa**: Umbral muy bajo, guardrails débiles

**Solución**:
```java
private static final double EVIDENCE_THRESHOLD = 0.75;  // ↑ de 0.65
private static final int MAX_SIZE_WITHOUT_HIGH_SIMILARITY = 25;  // ↓ de 40
```

### Problema: Nombres con tokens no deseados

**Causa**: Tokens de dominio no excluidos

**Solución**: Agregar a `EXCLUDE_TOKENS`:
```java
private static final Set<String> EXCLUDE_TOKENS = Set.of(
    // ... existentes ...
    "tuTokenNoDeseado1", "tuTokenNoDeseado2"
);
```
