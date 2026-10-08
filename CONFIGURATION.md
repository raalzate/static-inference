# Guía de Configuración y Personalización

Este documento describe qué parámetros, umbrales y listas controlan el comportamiento del motor de inferencia de microservicios, **dónde están en el código** y cómo modificarlos hoy.

> [!IMPORTANT]
> **Estado actual: todos los umbrales y pesos están hardcodeados.** En el código no existen constantes con nombre como `TABLE_WEIGHT`, `EVIDENCE_THRESHOLD` o `COHESION_WEIGHT`: los valores son **literales numéricos** dentro de los métodos de `src/main/java/com/extractor/inference/`. Para cambiarlos hay que editar el literal y recompilar (`mvn clean package`). No hay variables de entorno, flags de CLI ni archivo de configuración para ajustarlos.
>
> La configurabilidad (pesos, umbrales y listas externalizados) está **planificada en la feature 008 (InferenceConfig)**. Ver la sección [No implementado](#no-implementado-planificado--feature-008-inferenceconfig).
>
> El analizador .NET (`src/main/dotnet/DotNetAnalyzer`) no contiene umbrales de inferencia: solo extrae el grafo; el scoring y la consolidación ocurren en Java.

Para la descripción de los algoritmos ver [ALGORITHMS.md](ALGORITHMS.md).

## Variables de Entorno Soportadas

| Variable | Valor por defecto | Efecto | Código |
|---|---|---|---|
| `STATIC_INFERENCE_COLLECTED_AT` | ausente (`false`) | Con `true` se emite `meta.collected_at` (timestamp UTC) en el grafo de dependencias. **Por defecto se omite** para que la salida sea determinista (dos ejecuciones sobre el mismo código producen el mismo JSON). | Java: `src/main/java/com/extractor/model/DependencyGraph.java:100,119-130` (acepta también system property `-DSTATIC_INFERENCE_COLLECTED_AT=true`). .NET: `src/main/dotnet/DotNetAnalyzer/Model/DependencyGraph.cs:49-57` |

```bash
# Incluir meta.collected_at en la salida (rompe la comparación byte a byte entre ejecuciones)
export STATIC_INFERENCE_COLLECTED_AT=true
```

## Parámetros (hardcodeados) del Motor

Rutas relativas a `src/main/java/com/extractor/inference/`.

### 1. Pesos de Señales de Consolidación

**Ubicación**: `InterClusterGraph.java:41-42` (literales en `calculateSignals`)

```java
double evidenceScore = 0.25 * tableJaccard + 0.35 * callDensity +
                      0.30 * tokenSimilarity + 0.10 * (eventLinks.isEmpty() ? 0 : 1);
```

| Señal | Peso | Línea |
|---|---|---|
| Table Jaccard | 0.25 | `InterClusterGraph.java:41` |
| Call Density | 0.35 | `InterClusterGraph.java:41` |
| Token Similarity | 0.30 | `InterClusterGraph.java:42` |
| Event Links (binario) | 0.10 | `InterClusterGraph.java:42` |
| Umbral para crear arista | `evidenceScore > 0.1` | `InterClusterGraph.java:28` |
| Saturación de call density | `min(1.0, cross / (0.5 × internal))` | `InterClusterGraph.java:75` |

**Cuándo modificar** (editando los literales):
- Subir el peso de tablas si el proyecto tiene clara separación por tablas de BD
- Subir el peso de llamadas si las llamadas son el principal indicador de cohesión
- Subir el peso de tokens si los nombres de clases reflejan bien los dominios
- Subir el peso de eventos para arquitecturas event-driven (ojo: la señal de eventos rara vez dispara, ver ALGORITHMS.md §1.4)

Mantener la suma de pesos = 1.0 para que los umbrales de §2 conserven su significado.

### 2. Umbrales de Consolidación

El umbral de evidencia **no** está en `ClusterConsolidator`, sino en `InterClusterGraph.EdgeSignals.hasStrongEvidence()`.

| Parámetro | Valor | Ubicación |
|---|---|---|
| Umbral de evidencia | `evidenceScore >= 0.65` | `InterClusterGraph.java:274` |
| Mínimo de señales fuertes | `>= 2` | `InterClusterGraph.java:274` |
| Señal fuerte: tablas | `tableJaccard >= 0.4` | `InterClusterGraph.java:269` |
| Señal fuerte: llamadas | `callDensity >= 0.35` | `InterClusterGraph.java:270` |
| Señal fuerte: tokens | `tokenSimilarity >= 0.6` | `InterClusterGraph.java:271` |
| Señal fuerte: eventos | `eventLinks` no vacío | `InterClusterGraph.java:272` |
| Guard de tamaño (fase grafo) | rechaza si `totalSize > 40 AND tokenSimilarity < 0.75` | `ClusterConsolidator.java:153` |
| Ratio de librería de soporte | `>= 0.8` de miembros con keyword infra | `ClusterConsolidator.java:182` |
| Separación de candidatos fuertes | rechaza si ambos fuertes y `callDensity < 0.15 AND tableJaccard < 0.2` | `ClusterConsolidator.java:155-159` |
| Candidato fuerte | `cohesion >= 0.7 AND coupling < 0.3 AND size >= 3` | `ClusterConsolidator.java:210-212` |
| Tamaño máx. fusión por nombre (fase 0) | `totalSize <= 50` | `ClusterConsolidator.java:91` |
| Infraestructura significativa (fase 0) | `>= 0.3` de miembros con keyword infra | `ClusterConsolidator.java:122` |

**Para consolidación más agresiva** (menos microservicios, más grandes): bajar `0.65` (`InterClusterGraph.java:274`), bajar `strongSignals >= 2` a `1`, subir `40` (`ClusterConsolidator.java:153`).

**Para consolidación más conservadora** (más microservicios, más pequeños): subir `0.65`, exigir `>= 3` señales fuertes, bajar `40`.

### 3. Clasificación de Viabilidad

**Ubicación**: `ViabilityScorer.java`

| Parámetro | Valor | Línea |
|---|---|---|
| Peso cohesión ajustada | 0.5 | `:41` |
| Peso (1 − acoplamiento externo) | 0.35 | `:41` |
| Peso cohesión de datos | 0.15 | `:41` |
| Mezcla de cohesión ajustada | `0.7 × avgCohesion + 0.3 × internalDensity` | `:75` |
| `dataCohesion` sin tablas | 0.5 | `:129` |
| Penalización tamaño pequeño | `× 0.6` si `totalSize < 3` | `:43-44` |
| Penalización monolito | `× 0.7` si `totalSize > 50` y `internalDensity < 0.5` | `:45-49` |
| Umbral Alta | `score >= 0.7` | `:52` |
| Umbral Media | `score >= 0.5` | `:52` |

Cortes de los mensajes de rationale (no afectan el score): cohesión 0.7/0.5 (`:209-212`), acoplamiento 0.3/0.5 (`:222-225`), CBO 5/10 (`:235-238`), LCOM 0.3/0.6 (`:249-252`), datos 0.6/0.3 (`:262-264`), tamaño 3/50 (`:269-271`).

**Para priorizar bajo acoplamiento**: p. ej. cambiar `:41` a `0.4 * cohesionAdj + 0.45 * (1 - externalCoupling) + 0.15 * dataCohesion`.

**Para clasificación más estricta**: subir `0.7`/`0.5` en `:52` (p. ej. a `0.8`/`0.6`).

### 4. Recomendaciones y Ensamblado de Propuestas

**Ubicación**: `MicroserviceRecommendationEngine.java`

| Parámetro | Valor | Línea |
|---|---|---|
| Grupo = librería de soporte | `>= 0.8` de miembros con keyword infra | `:182` |
| Keywords de infraestructura (nombre simple) | ver §6 | `:18-21` |
| `data_jaccard` emitido en métricas | `0.8` si hay tablas, `0.0` si no (valor fijo) | `:468` |
| Acciones recomendadas por viabilidad | textos fijos | `:486-514` |

Este archivo **no** define umbrales de "candidato fuerte" ni de "nano-servicio". El criterio de candidato fuerte vive en `ClusterConsolidator.java:210-212` (ver §2); no existe un umbral de nano-servicio (lo más cercano es la penalización `totalSize < 3` de `ViabilityScorer.java:43-44`).

### 5. Tokens Excluidos en Nombres

**Ubicación**: `MicroserviceNameGenerator.java:7-11`

```java
private static final Set<String> EXCLUDE_TOKENS = Set.of("entity", "model", "data", "dto",
    "event", "command", "query", "impl", "repository", "service", "controller", "api", "rest",
    "http", "adapter", "port", "localevents", "rabbit", "jpa", "repo", "dao", "operations",
    "listener", "publisher", "handler", "factory", "db", "activityupdaterimpl", "usecase",
    "primaryports", "secondaryports", "userprofilejpa", "tweetcrud", "suggestion", "suggestions");
```

Listas relacionadas (independientes, también hardcodeadas):
- Palabras de rol que habilitan la extracción de tokens: `MicroserviceNameGenerator.java:120-121` y `InterClusterGraph.java:130-131`.
- Exclusiones para la similitud de tokens del grafo (lista más corta): `InterClusterGraph.java:129` → `entity, model, data, dto, event, command, query`.
- Longitud mínima de token: `> 2` (`MicroserviceNameGenerator.java:132,139`; en `InterClusterGraph.java:149` solo para el segmento de paquete).

**Cuándo modificar**:
- **Agregar tokens** si aparecen nombres genéricos no deseados
- **Remover tokens** si son significativos para tu dominio

Si se cambian las exclusiones del nombre, considerar replicar el cambio en `InterClusterGraph.java:129` para que la consolidación y el nombrado usen el mismo vocabulario.

### 6. Keywords de Infraestructura

Hay **tres** listas distintas:

**a) Etiquetas para nombres de infraestructura** — `MicroserviceNameGenerator.java:13-23` (se comparan contra el FQCN completo; umbral `>= 0.8` en `:56`):

```java
config → "Configuración", security → "Seguridad", auth → "Autenticación",
swagger → "Documentación", email → "Notificaciones por Email",
notification → "Notificaciones", log → "Logging", audit → "Auditoría",
application → "Aplicación Principal"
```

**b) Detección de soporte en consolidación** — `ClusterConsolidator.java:8-11` (nombre simple):

```java
"config", "configuration", "security", "application", "exception",
"error", "filter", "interceptor", "aspect", "swagger", "openapi", "main"
```
(no incluye `auth`)

**c) Filtrado de infraestructura en propuestas** — `MicroserviceRecommendationEngine.java:18-21` (nombre simple): misma lista que (b).

**Cuándo modificar**: agregar keywords específicos de tu stack (p. ej. `gateway`, `discovery`, `feign` para Spring Cloud) en (b) y (c) a la vez para mantenerlas consistentes.

### 7. Constantes de Análisis (AnalysisConstants)

**Ubicación**: `src/main/java/com/extractor/constants/AnalysisConstants.java`

Centraliza patrones y listas para la detección de componentes y datos sensibles (estas sí son constantes con nombre).

```java
// :16-18
public static final Set<String> SENSITIVE_KEYWORDS = Set.of(
    "password", "ssn", "creditcard", "apikey", "secretkey", "token", "auth"
);

// :21-23
public static final Pattern SQL_TABLE_PATTERN = Pattern.compile(
    "\\b(?:FROM|JOIN|INTO|UPDATE)\\s+([a-zA-Z0-9_]+)", Pattern.CASE_INSENSITIVE
);

// :25 y :38
public static final Set<String> SPRING_REPO_INTERFACES = Set.of(...);
public static final Set<String> JPA_ENTITY_ANNOTATIONS = Set.of(...);
```

**Cuándo modificar**:
- **Agregar keywords sensibles**: para cumplir políticas de seguridad específicas.
- **Ajustar patrones SQL**: si se usan dialectos SQL no estándar.
- **Soportar nuevas anotaciones**: si se migra a Jakarta EE 10+ o nuevas versiones de Spring.

> [!IMPORTANT]
> Cualquier cambio en `AnalysisConstants.java` o en los literales de `inference/` requiere recompilar (`mvn clean package`).

## Casos de Uso (editando literales)

### Caso 1: Arquitectura Hexagonal
**Problema**: adaptadores detectados como clusters separados.
**Solución**: subir el peso de tokens y bajar el umbral de evidencia:
- `InterClusterGraph.java:41-42`: `0.30 * tokenSimilarity` → `0.45`, `0.35 * callDensity` → `0.20` (mantener suma 1.0)
- `InterClusterGraph.java:274`: `evidenceScore >= 0.65` → `0.60`

### Caso 2: Microservicios Event-Driven
**Problema**: eventos no generan relación fuerte.
**Solución**: subir el peso de eventos en `InterClusterGraph.java:42` (p. ej. `0.25`) y bajar los demás. Nota: dado que la detección de eventos empareja por nombre simple idéntico (`InterClusterGraph.java:161-205`), subir el peso puede no tener efecto sin mejorar antes la detección.

### Caso 3: Monolito con Muchas Dependencias
**Problema**: todo clasificado como baja viabilidad.
**Solución**: en `ViabilityScorer.java:41` cambiar a `0.6 * cohesionAdj + 0.25 * (1 - externalCoupling) + 0.15 * dataCohesion`.

### Caso 4: Proyectos Pequeños
**Problema**: muchos módulos de 1-2 componentes penalizados.
**Solución**: en `ViabilityScorer.java:43` cambiar `totalSize < 3` por `totalSize < 2` (o suavizar el `0.6` de `:44`); opcionalmente bajar `size >= 3` de candidato fuerte en `ClusterConsolidator.java:212`.

## Extensibilidad: Agregar Nuevas Señales

1. Implementar el cálculo en `InterClusterGraph` (retornar valor en [0, 1]):
   ```java
   private double calculateCustomSignal(Cluster a, Cluster b) { ... }
   ```
2. Añadirlo a `calculateSignals` (`InterClusterGraph.java:35-45`) y al constructor de `EdgeSignals` (`:252-259`).
3. Reajustar los literales de `:41-42` para que la suma siga siendo 1.0, p. ej.:
   ```java
   double evidenceScore = 0.20 * tableJaccard + 0.30 * callDensity +
                          0.25 * tokenSimilarity + 0.10 * eventSignal + 0.15 * customSignal;
   ```
4. Si debe contar como señal fuerte, añadir su condición en `hasStrongEvidence()` (`:267-275`).

## Mejores Prácticas

### 1. Validación
Al editar pesos, verificar que:
```
0.25 + 0.35 + 0.30 + 0.10 = 1.0        (InterClusterGraph.java:41-42)
0.5  + 0.35 + 0.15        = 1.0        (ViabilityScorer.java:41)
```

### 2. Iteración y Ajuste
1. Ejecutar con los valores por defecto
2. Revisar resultados en `*_architecture.json`
3. Identificar problemas (p. ej. muchos módulos diminutos)
4. Ajustar un literal a la vez
5. Recompilar, re-ejecutar y comparar

### 3. Documentar Cambios
Mantener registro de los literales modificados (archivo:línea, valor anterior/nuevo, razón).

### 4. Testing
```bash
# Línea base (sin collected_at → salida determinista y comparable con diff)
mvn exec:java -Dexec.args="/path/to/test-project baseline.json"

# Editar literales, recompilar
mvn clean package
mvn exec:java -Dexec.args="/path/to/test-project modified.json"

diff baseline_architecture.json modified_architecture.json
```
No activar `STATIC_INFERENCE_COLLECTED_AT` al comparar salidas.

## Troubleshooting

### No se consolida nada
**Causa**: umbral de evidencia alto o pocas señales fuertes.
**Solución**: bajar `0.65` y/o `strongSignals >= 2` en `InterClusterGraph.java:274`, o los umbrales de señal fuerte en `:269-272`.

### Todo se consolida en un mega-servicio
**Causa**: umbral bajo, guardrails débiles.
**Solución**: subir `0.65` (`InterClusterGraph.java:274`) y bajar `40` (`ClusterConsolidator.java:153`) / `50` (`ClusterConsolidator.java:91`).

### Nombres con tokens no deseados
**Solución**: agregar el token a `EXCLUDE_TOKENS` (`MicroserviceNameGenerator.java:7-11`).

---

## No implementado (planificado — feature 008: InferenceConfig)

Lo siguiente aparecía en versiones anteriores de esta guía pero **no existe en el código**. Se conserva como referencia de diseño para la feature 008.

### Constantes con nombre

Ninguna de estas constantes existe; los valores reales son literales (columna derecha):

| Nombre documentado antes | Valor real hoy | Dónde |
|---|---|---|
| `TABLE_WEIGHT = 0.25` | literal `0.25` | `InterClusterGraph.java:41` |
| `CALL_WEIGHT = 0.35` | literal `0.35` | `InterClusterGraph.java:41` |
| `TOKEN_WEIGHT = 0.30` | literal `0.30` | `InterClusterGraph.java:42` |
| `EVENT_WEIGHT = 0.10` | literal `0.10` | `InterClusterGraph.java:42` |
| `EVIDENCE_THRESHOLD = 0.65` (en ClusterConsolidator) | literal `0.65` en **InterClusterGraph** | `InterClusterGraph.java:274` |
| `MIN_STRONG_SIGNALS = 2` | literal `2` | `InterClusterGraph.java:274` |
| `STRONG_SIGNAL_THRESHOLD = 0.5` | no existe un umbral único: 0.4 / 0.35 / 0.6 / presencia | `InterClusterGraph.java:269-272` |
| `MAX_SIZE_WITHOUT_HIGH_SIMILARITY = 40` | literal `40` | `ClusterConsolidator.java:153` |
| `HIGH_TOKEN_SIMILARITY = 0.75` | literal `0.75` | `ClusterConsolidator.java:153` |
| `SUPPORT_RATIO = 0.8` | literal `0.8` | `ClusterConsolidator.java:182` |
| `COHESION_WEIGHT = 0.5` | literal `0.5` | `ViabilityScorer.java:41` |
| `COUPLING_WEIGHT = 0.35` | literal `0.35` | `ViabilityScorer.java:41` |
| `DATA_WEIGHT = 0.15` | literal `0.15` | `ViabilityScorer.java:41` |
| `HIGH_VIABILITY_THRESHOLD = 0.7` | literal `0.7` | `ViabilityScorer.java:52` |
| `MEDIUM_VIABILITY_THRESHOLD = 0.5` | literal `0.5` | `ViabilityScorer.java:52` |
| `SMALL_SIZE_PENALTY = 0.6`, `MIN_SIZE = 3` | literales `0.6`, `3` | `ViabilityScorer.java:43-44` |
| `MONOLITH_PENALTY = 0.7`, `MAX_SIZE = 50`, `MIN_DENSITY = 0.5` | literales `0.7`, `50`, `0.5` | `ViabilityScorer.java:45-49` |
| `STRONG_COHESION = 0.7`, `STRONG_COUPLING_MAX = 0.3`, `STRONG_MIN_SIZE = 3` (en MicroserviceRecommendationEngine) | literales en **ClusterConsolidator** | `ClusterConsolidator.java:210-212` |
| `NANO_MAX_SIZE = 2` | **no existe** ningún equivalente | — |

### Variables de entorno para pesos

Diseño propuesto (no implementado):

```java
private static final double TABLE_WEIGHT =
    Double.parseDouble(System.getenv().getOrDefault("TABLE_WEIGHT", "0.25"));
```

```bash
export TABLE_WEIGHT=0.30
export CALL_WEIGHT=0.40
export TOKEN_WEIGHT=0.20
export EVENT_WEIGHT=0.10
mvn exec:java -Dexec.args="/path/to/project output.json"
```

### Archivo de configuración JSON (`--config`)

Diseño propuesto (no implementado; `MicroserviceInferenceMain` no acepta `--config`):

```json
{
  "consolidation": {
    "weights": { "table": 0.25, "call": 0.35, "token": 0.30, "event": 0.10 },
    "thresholds": { "evidence": 0.65, "minStrongSignals": 2, "maxSize": 40 }
  },
  "viability": {
    "weights": { "cohesion": 0.5, "coupling": 0.35, "data": 0.15 },
    "thresholds": { "high": 0.7, "medium": 0.5 }
  },
  "naming": {
    "excludeTokens": ["entity", "dto", "service"],
    "infraKeywords": { "config": "Configuración", "security": "Seguridad" }
  }
}
```

```bash
mvn exec:java -Dexec.args="/path/to/project output.json --config config.json"
```

### Otras ideas de diseño no implementadas
- `getExcludeTokens()` para quitar tokens (p. ej. `aggregate`) de `EXCLUDE_TOKENS` en tiempo de ejecución. `aggregate` tampoco figura en la lista actual.
- Un umbral explícito de "nano-servicio" en `MicroserviceRecommendationEngine`.
- Unificar las tres listas de keywords de infraestructura (§6) en una sola fuente configurable.
