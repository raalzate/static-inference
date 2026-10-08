# Algoritmos del Motor de Inferencia de Microservicios

Este documento describe los algoritmos técnicos utilizados para consolidar clusters, generar nombres de microservicios y clasificar viabilidad. Cada fórmula y umbral está referenciado al código fuente (`archivo:línea`) en el que se implementa.

> [!NOTE]
> **Todos los umbrales y pesos descritos aquí están hardcodeados** como literales en el código Java (`src/main/java/com/extractor/inference/`). No existen constantes con nombre ni parámetros configurables en tiempo de ejecución. La configurabilidad está planificada en la **feature 008 (InferenceConfig)**. Ver [CONFIGURATION.md](CONFIGURATION.md).
>
> El analizador .NET (`src/main/dotnet/DotNetAnalyzer`) **no** ejecuta consolidación ni scoring: solo extrae el grafo de dependencias (componentes, llamadas, tablas, CBO/LCOM, endpoints). Toda la inferencia descrita en este documento la realiza el motor Java sobre ese grafo.

Rutas abreviadas usadas abajo (todas bajo `src/main/java/com/extractor/inference/`):
`InterClusterGraph.java`, `ClusterConsolidator.java`, `MicroserviceNameGenerator.java`, `ViabilityScorer.java`, `MicroserviceRecommendationEngine.java`, `MetricsCalculator.java`.

## 1. InterClusterGraph: Cálculo de Relaciones Entre Clusters

### Propósito
Calcula la fuerza de relación entre cada par de clusters (todas las combinaciones `i < j`, `InterClusterGraph.java:21-32`) usando cuatro señales, para determinar si deben consolidarse.

### Señales Calculadas

#### 1.1 Table Jaccard Index
Similitud de las tablas de cada cluster (`ClusterMetrics.getTablesShared()`). `InterClusterGraph.java:47-60`.

```
tableJaccard(A, B) = |tables(A) ∩ tables(B)| / |tables(A) ∪ tables(B)|
                     (0.0 si ambos conjuntos están vacíos)
```

- **Rango**: [0, 1]
  - 1.0 = ambos clusters usan exactamente las mismas tablas
  - 0.0 = no comparten tablas

#### 1.2 Call Density
Densidad de llamadas cruzadas **relativa a las llamadas internas** de ambos clusters (no al producto de tamaños). `InterClusterGraph.java:62-76`.

```
cross    = calls(A→B) + calls(B→A)          // aristas callsOut cuyo destino está en el otro cluster
internal = internalCalls(A) + internalCalls(B)

IF cross == 0 OR internal == 0:
    callDensity = 0.0
ELSE:
    callDensity = min(1.0, cross / (0.5 × internal))
```

- **Rango**: [0, 1] (saturado en 1.0)
- Observación: si ninguno de los dos clusters tiene llamadas internas, la señal es 0 aunque haya muchas llamadas cruzadas.

#### 1.3 Token Similarity
Jaccard de tokens de dominio extraídos **solo de componentes cuyo nombre contiene una palabra de rol**. `InterClusterGraph.java:112-159`.

**Extracción de tokens** (`InterClusterGraph.java:127-159`):

```
roleKeywords   = {service, controller, repository, repo, usecase, operations,
                  listener, publisher, adapter, factory, handler, db}       // :130-131
excludeKeywords = {entity, model, data, dto, event, command, query}        // :129

para cada componente (FQCN):
    simple = nombre simple en minúsculas
    para el primer role ∈ roleKeywords contenido en simple:
        token = simple sin "role.*", sin "repository", sin "impl"
        si token no vacío y token ∉ excludeKeywords → añadir token
        lastPackage = último segmento del paquete
        si lastPackage ∉ excludeKeywords y len(lastPackage) > 2 → añadir lastPackage
        (break)
```

No hay separación CamelCase: el token es el prefijo completo previo a la palabra de rol (p. ej. `orderlineservice` → `orderline`). Componentes sin palabra de rol (p. ej. `OrderEntity`, `OrderDto`) no aportan tokens.

**Cálculo**:
```
tokenSimilarity(A, B) = |tokens(A) ∩ tokens(B)| / |tokens(A) ∪ tokens(B)|   (0.0 si ambos vacíos)
```

- **Ejemplo**: `com.shop.order.OrderService` → tokens `{order}` (prefijo) y `{order}` (paquete).

#### 1.4 Event Links
`InterClusterGraph.java:161-205`.

```
published(X) = { nombre simple de c ∈ X | c contiene "publisher" o "event" }     // :185-194
consumed(X)  = { nombre simple de c ∈ X | c contiene "listener" o "consumer" }   // :196-205

eventLinks(A, B) = (published(A) ∩ consumed(B)) ∪ (published(B) ∩ consumed(A))
eventSignal      = 1 si eventLinks no vacío, 0 en otro caso
```

- **Valor en el score**: binario (0 o 1).
- Observación: el emparejamiento es por **nombre simple idéntico** del componente, no por el tipo de evento publicado/consumido. En la práctica solo dispara cuando existe una clase con el mismo nombre simple (y que contenga p. ej. `event` y `listener`) en ambos clusters; no detecta el patrón "publisher en A llama a listener en B".

### Evidence Score Final

`InterClusterGraph.java:41-42` (literales, sin constantes con nombre):

```
evidenceScore(A, B) = 0.25 × tableJaccard +
                      0.35 × callDensity +
                      0.30 × tokenSimilarity +
                      0.10 × eventSignal
```

**Umbral de arista**: se crea arista solo si `evidenceScore > 0.1` (`InterClusterGraph.java:28`).

### Evidencia fuerte (`hasStrongEvidence`)

`InterClusterGraph.java:267-275` (método de `EdgeSignals`, no de `ClusterConsolidator`):

```
strongSignals = #{ tableJaccard >= 0.4,
                   callDensity  >= 0.35,
                   tokenSimilarity >= 0.6,
                   eventLinks no vacío }

hasStrongEvidence = evidenceScore >= 0.65 AND strongSignals >= 2
```

### Ejemplo Práctico

```
tableJaccard = 1.0, callDensity = 0.4, tokenSimilarity = 1.0, eventSignal = 0

evidenceScore = 0.25×1.0 + 0.35×0.4 + 0.30×1.0 + 0.10×0
              = 0.25 + 0.14 + 0.30 + 0
              = 0.69
strongSignals = 3 (table, token, callDensity >= 0.35)
→ hasStrongEvidence = true
```

---

## 2. ClusterConsolidator: Algoritmo Greedy de Consolidación

### Propósito
Agrupa clusters relacionados usando la evidencia del grafo, con guardrails para evitar consolidaciones incorrectas. Usa una estructura tipo union-find sobre `mergedGroups` (`ClusterConsolidator.java:215-230`).

### Palabras clave de infraestructura

`ClusterConsolidator.java:8-11` — se comparan contra el **nombre simple** de la clase en minúsculas (`contains`):

```
config, configuration, security, application, exception,
error, filter, interceptor, aspect, swagger, openapi, main
```

(Nota: **no** incluye `auth`.)

### Algoritmo (`consolidate()`, `ClusterConsolidator.java:28-46`)

```
ENTRADA: clusters[], allComponents
SALIDA: groups[] (sets de cluster IDs consolidados)

1. Fase 0 — Pre-consolidación por nombre de dominio (consolidateByDomainName, :48-77)
   - Para cada cluster individual se genera un nombre con MicroserviceNameGenerator.
   - Se ignoran los nombres genéricos (:50-51): "Componente de Negocio",
     "Componente Desconocido", "Componente de Infraestructura",
     "Componente de Configuración".
   - Clusters con el mismo nombre se fusionan si canMergeSameDomain (:79-94):
       a. NOT (isSupport(A) XOR isSupport(B))
       b. hasSignificantInfrastructure(A) == hasSignificantInfrastructure(B)
          (>= 30% de miembros del grupo con keyword de infraestructura, :96-123)
       c. tamaño combinado <= 50   (:91)

2. Fase 1 — Consolidación basada en grafo
   - Candidatos = pares con arista y hasStrongEvidence() (findMergeCandidates, :125-143)
   - Ordenar por evidenceScore descendente (:140-141)
   - Para cada candidato (A, B): si raíces distintas y canMerge(...) → fusionar

3. Retornar grupos no vacíos
```

### Función `canMerge(rootA, rootB, signals)` (`ClusterConsolidator.java:145-162`)

El umbral de evidencia (`>= 0.65` y `>= 2` señales fuertes) ya se aplicó al seleccionar candidatos (ver §1). `canMerge` añade estos guardrails:

#### Guard 1: Support Library (`:149-150`)
```
NOT (isSupport(A) XOR isSupport(B))
```
**isSupport(X)** (`:164-183`): `>= 80%` de los miembros **del cluster raíz** tienen una keyword de infraestructura en su nombre simple.
Observación: `isSupport` evalúa solo el cluster cuyo id es la raíz del grupo, no el grupo consolidado completo.

#### Guard 2: Size (`:152-153`)
```
NOT (totalSize > 40 AND tokenSimilarity < 0.75)
```

#### Guard 3: Separación de candidatos fuertes (`:155-159`)
```
NOT (bothStrongCandidates(A, B) AND callDensity < 0.15 AND tableJaccard < 0.2)
```
**isStrongCandidate(cluster)** (`:201-213`): `cohesion >= 0.7 AND coupling < 0.3 AND size >= 3`.
**bothStrongCandidates** (`:195-199`): algún cluster de cada grupo es candidato fuerte.

### Ejemplo de Consolidación

**Entrada**:
- Cluster 0: {ItemService, ItemRepository}
- Cluster 1: {ItemController, ItemMapper}
- Cluster 2: {OrderService, OrderRepository}
- Cluster 3: {SecurityConfig, SwaggerConfig} — support (100% infra)

**Aristas**:
1. (0↔1): tableJaccard=1.0, callDensity=0.5, tokenSim=1.0 → score = 0.25 + 0.175 + 0.30 = 0.725, 3 señales fuertes → candidato
2. (0↔2): tableJaccard=0.0, callDensity=0.2, tokenSim=0.0 → score = 0.07 → sin arista (≤ 0.1)
3. (1↔3): tableJaccard=0.0, callDensity=0.3, tokenSim=0.0 → score = 0.105 → arista, pero sin evidencia fuerte → no candidato

**Proceso**:
1. Fase 0: clusters 0 y 1 probablemente generan "Componente de Item" → se fusionan ya aquí si pasan `canMergeSameDomain`.
2. Fase 1: (0↔1) si no estaban ya unidos, `canMerge` → no support, size=4 → **MERGE**.

**Salida**:
- Grupo {0, 1} → propuesta "Componente de Item"
- Grupo {2} → propuesta "Componente de Order"
- Grupo {3} → librería de soporte (ver §5)

---

## 3. MicroserviceNameGenerator: Generación Automática de Nombres

### Propósito
Generar nombres de negocio legibles sin hardcoding de dominios.

### 3.1 Detección de Tipo (Infraestructura vs Negocio)

`MicroserviceNameGenerator.java:43-57`:

```
infraCount = # miembros cuyo FQCN en minúsculas contiene alguna clave de INFRA_KEYWORDS
isInfra    = infraCount / totalMiembros >= 0.8

IF isInfra: generateInfrastructureName()  ELSE: generateBusinessName()
```

Observación: aquí se compara contra el **FQCN completo** (incluye paquete), a diferencia de `ClusterConsolidator`, que usa el nombre simple.

### 3.2 Nombres de Infraestructura

**Mapeo** (`INFRA_KEYWORDS`, `MicroserviceNameGenerator.java:13-23`):

| keyword | etiqueta |
|---|---|
| config | Configuración |
| security | Seguridad |
| auth | Autenticación |
| swagger | Documentación |
| email | Notificaciones por Email |
| notification | Notificaciones |
| log | Logging |
| audit | Auditoría |
| application | Aplicación Principal |

**Algoritmo** (`:59-88`): cuenta ocurrencias de cada keyword en los miembros, toma las 2 más frecuentes.
- 0 keywords → `"Componente de Infraestructura"`
- 1 → `"Componente de {etiqueta}"`
- 2 → `"Componente de {etiqueta1} & {etiqueta2}"`

Observación: en empates el orden depende de la iteración de `HashMap`.

**Ejemplo**: {SecurityConfig, WebSecurityConfig, CorsConfig} → config=3, security=2 → `"Componente de Configuración & Seguridad"`.

### 3.3 Nombres de Negocio

**Paso 1: Extracción de tokens de dominio** (`extractDomainTokens`, `:118-149`) — misma lógica que §1.3 (palabras de rol, prefijo previo al rol, último segmento de paquete), con dos diferencias: se filtra con `EXCLUDE_TOKENS` y el token prefijo requiere longitud > 2 (`:132`).

**Paso 2: Exclusión de tokens** (`EXCLUDE_TOKENS`, `:7-11`, 34 entradas):

```
entity, model, data, dto, event, command, query, impl, repository, service,
controller, api, rest, http, adapter, port, localevents, rabbit, jpa, repo,
dao, operations, listener, publisher, handler, factory, db,
activityupdaterimpl, usecase, primaryports, secondaryports,
userprofilejpa, tweetcrud, suggestion, suggestions
```

(Incluye algunos tokens específicos de proyectos de prueba, p. ej. `tweetcrud`, `userprofilejpa`, `suggestion`.)

**Paso 3: Selección de top tokens** (`generateBusinessName`, `:90-116`)

No se usa TF-IDF. Se calcula una **frecuencia de documento** sobre clusters:

```
freq(token) = número de clusters del grupo en cuyo conjunto de tokens aparece token
topTokens   = 2 tokens con mayor freq (empates: orden de HashMap)
```

**Paso 4: Formato final**

```
sin tokens         → "Componente de Negocio"
1 token            → "Componente de {Token1}"
2 tokens           → "Componente de {Token1} y {Token2}"
```
(capitalización solo de la primera letra, `:151-154`)

**Ejemplo**:

Input: un cluster `{com.shop.order.OrderService, com.shop.order.OrderRepository, com.shop.order.OrderEntity, com.shop.order.OrderDto}`

```
1. Solo OrderService y OrderRepository contienen palabra de rol.
2. Tokens: "order" (prefijo) y "order" (paquete) → {order}
3. freq(order) = 1 → top = [order]
4. Output: "Componente de Order"
```

---

## 4. ViabilityScorer: Clasificación de Viabilidad

### Propósito
Clasificar propuestas consolidadas en Alta/Media/Baja viabilidad. `ViabilityScorer.calculateViability`, `ViabilityScorer.java:17-56`.

### Fórmula de Viabilidad

`ViabilityScorer.java:41-50`:

```
score = 0.5 × cohesionAdj +
        0.35 × (1 - externalCoupling) +
        0.15 × dataCohesion

IF totalSize < 3:
    score = score × 0.6
ELSE IF totalSize > 50 AND internalDensity < 0.5:
    score = score × 0.7
```

#### 4.1 Cohesion Adjusted (`ViabilityScorer.java:58-76`)

```
avgCohesion     = Σ(cohesion_c × size_c) / Σ size_c        // promedio PONDERADO por tamaño
internalDensity = internalEdges / (n × (n − 1))             // :78-97, n = miembros únicos

cohesionAdj = 0.7 × avgCohesion + 0.3 × internalDensity
```

`cohesion_c` es la cohesión del cluster calculada en `MetricsCalculator.java:41-64`: `Σ peso(aristas internas) / Σ peso(aristas salientes de miembros)` (0.0 para clusters de 1 componente).

#### 4.2 External Coupling (`ViabilityScorer.java:99-122`)

```
externalCoupling = externalCalls / (internalCalls + externalCalls)   (0.0 si no hay llamadas)
```

- 0.0 = sin llamadas externas; 1.0 = todas las llamadas son externas.

#### 4.3 Data Cohesion (`ViabilityScorer.java:124-143`)

**No** es `max(0.5, jaccard)`. El código calcula la proporción de tablas compartidas por más de un cluster del grupo:

```
allTables = ∪ tablas de los clusters del grupo
IF allTables vacío:
    dataCohesion = 0.5
ELSE:
    sharedTables = #{ t ∈ allTables | t aparece en > 1 cluster del grupo }
    dataCohesion = sharedTables / |allTables|
```

Consecuencias:
- Una propuesta de **un solo cluster** con tablas obtiene `dataCohesion = 0.0` (ninguna tabla aparece en >1 cluster), menor que una propuesta sin tablas (0.5).
- No existe floor de 0.5 cuando hay tablas.

> [!WARNING]
> El campo `data_jaccard` que se emite en las métricas de cada propuesta (`*_architecture.json`) **no** es este valor: `MicroserviceRecommendationEngine.java:468` lo fija en `0.8` si la propuesta tiene tablas y `0.0` si no. Es un valor de marcador, independiente del `dataCohesion` usado en el score.

### Clasificación (`ViabilityScorer.java:52`)

```
score >= 0.7 → "Alta"
score >= 0.5 → "Media"
otro         → "Baja"
```

### Rationale Generation (`ViabilityScorer.java:195-330`)

Genera explicaciones en español con estos cortes:

| Métrica | ✅ | ⚠️ | ❌ | Línea |
|---|---|---|---|---|
| cohesionAdj | >= 0.7 | >= 0.5 | < 0.5 | :209-218 |
| externalCoupling | < 0.3 | < 0.5 | >= 0.5 | :222-231 |
| CBO promedio | <= 5 | <= 10 | > 10 | :235-244 |
| LCOM promedio | <= 0.3 | <= 0.6 | > 0.6 | :249-258 |
| dataCohesion | >= 0.6 | >= 0.3 | (sin mensaje) | :262-266 |
| tamaño | 3..50 | < 3 ó > 50 | — | :269-275 |

CBO/LCOM son promedios de los valores por componente que traen el grafo (`ViabilityScorer.java:145-179`); solo se reportan si algún componente los tiene.

Para viabilidad **Baja** se añade la sección "⛔ RAZONES POR LAS QUE ESTA DESCOMPOSICIÓN NO ES VIABLE" (`:278-327`) con razones disparadas por: `cohesionAdj < 0.5`, `externalCoupling >= 0.5`, `CBO > 10`, `LCOM > 0.6`, `totalSize < 3`, `0 < dataCohesion < 0.3`.

### Recommended Actions (`MicroserviceRecommendationEngine.generateActions`, `:486-514`)

**Alta**:
- ✅ Diseñar como microservicio independiente
- ✅ Definir API pública con contratos claros (OpenAPI/gRPC)
- ✅ Asignar base de datos exclusiva con ownership de: {tablas} (solo si hay tablas)
- ✅ Implementar patrones de resiliencia (circuit breaker, retry, timeout)
- ⚠️ Implementar encriptación, auditoría y controles de acceso por datos sensibles (solo si `sensitive`)

**Media**:
- 🔧 Refactorizar para mejorar cohesión y reducir acoplamiento
- 🔧 Aplicar principios SOLID (SRP, DIP) para separación de responsabilidades
- 🔧 Considerar eventos asíncronos para reducir acoplamiento síncrono
- 📋 Re-evaluar después de refactorización

**Baja**:
- ❌ NO implementar como microservicio en estado actual
- 🔧 Requiere refactorización profunda o fusión con otros dominios
- 💡 Evaluar si debe ser librería compartida o módulo interno

### Ejemplo Numérico

**Propuesta**: "Componente de Order", clusters {1, 4}, 8 componentes.

```
cluster 1: 5 miembros, cohesion 0.70
cluster 4: 3 miembros, cohesion 0.60
avgCohesion     = (0.70×5 + 0.60×3) / 8 = 0.6625
internalDensity = 0.35
cohesionAdj     = 0.7×0.6625 + 0.3×0.35 = 0.569

externalCoupling = 0.2
tablas: {orders, order_lines, customers}; orders y order_lines aparecen en ambos clusters
dataCohesion     = 2/3 = 0.667

score = 0.5×0.569 + 0.35×0.8 + 0.15×0.667
      = 0.284 + 0.28 + 0.10
      = 0.664
Penalización: ninguna (size 8)
→ MEDIA
```

---

## 5. MicroserviceRecommendationEngine: Ensamblado de la Propuesta

`MicroserviceRecommendationEngine.analyzeConsolidated`, `:57-163`:

1. Ejecuta `ClusterConsolidator.consolidate()` (`:64-65`).
2. Para cada grupo:
   - Si `isSupportGroup` (`:165-183`, `>= 80%` de miembros con keyword de infraestructura en el nombre simple, lista en `:18-21`) → **librería de soporte**.
   - Si no → **propuesta** (`createProposal`, `:185-226`): nombre (§3), viabilidad (§4), métricas consolidadas, acciones.
   - Propuestas cuyos componentes ya fueron asignados a otra propuesta se descartan (`:82-87`).
3. Los componentes de infraestructura se filtran de las propuestas (`:199-204`) y se agrupan en una librería "Infraestructura y Configuración Filtrada" (`:102-111`).

Métricas consolidadas emitidas por propuesta (`calculateConsolidatedMetrics`, `:420-476`) — **difieren** de las usadas para el score de viabilidad:
- `cohesion`: promedio, por componente, de la máxima cohesión de cluster al que pertenece (`:427-438`).
- `external_coupling` e `internal_edge_density`: calculados sobre los miembros **sin** infraestructura (`:444-462`).
- `data_jaccard`: `0.8` si hay tablas, `0.0` si no (`:468`) — ver advertencia en §4.3.

---

## 6. Flujo Completo del Pipeline

```
1. Analizador (Java/Spoon, o .NET/Roslyn vía bridge) → DependencyGraph
   (componentes, llamadas, tablas, CBO/LCOM, endpoints)
2. InferenceEngine → Clusters (ClusteringAlgorithm) + métricas (MetricsCalculator)
   + reglas (HighCohesionRule, LowCouplingRule, SharedDataRule)
3. InterClusterGraph → aristas con evidenceScore
4. ClusterConsolidator → grupos consolidados
5. MicroserviceNameGenerator → nombres de negocio
6. ViabilityScorer → clasificación Alta/Media/Baja
7. MicroserviceRecommendationEngine → propuestas + librerías de soporte
8. Output → <salida>_architecture.json (MicroserviceInferenceMain.java:69)
```

### Determinismo de la salida: `meta.collected_at`

El campo `meta.collected_at` del grafo de dependencias es **opt-in** y se **omite por defecto** para que dos ejecuciones sobre el mismo código produzcan JSON idéntico. Se activa con la variable de entorno `STATIC_INFERENCE_COLLECTED_AT=true`:
- Java: `src/main/java/com/extractor/model/DependencyGraph.java:100,119-130` (acepta también la system property `-DSTATIC_INFERENCE_COLLECTED_AT=true`).
- .NET: `src/main/dotnet/DotNetAnalyzer/Model/DependencyGraph.cs:49-57`.

Observación: algunos pasos dependen del orden de iteración de `Set.of`/`HashMap` (p. ej. la elección de la palabra de rol en §1.3/§3.3 y los desempates de top tokens en §3), lo que puede afectar el determinismo de nombres en casos límite.

---

## 7. Limitaciones y Supuestos

### Supuestos
- Código bien estructurado con nombres significativos
- Uso de patrones arquitectónicos reconocibles en el nombre de clase (Service, Repository, Controller, etc.)
- Tablas de BD detectables (JPA/JDBC en Java; EF/SQL en .NET)

### Limitaciones
- No considera aspectos de runtime (carga, latencia)
- Basado en análisis estático (no observa tráfico real)
- Nombres generados dependen de la calidad de los nombres en código
- No detecta bounded contexts de DDD automáticamente
- Umbrales y pesos hardcodeados (ver nota inicial; feature 008)

### Mejoras Futuras
- Parametrización de pesos y umbrales (feature 008: InferenceConfig)
- Análisis de logs de producción para ajustar pesos
- Machine learning para mejorar detección de dominios
- Integración con event storming para validar fronteras
- Análisis de transacciones distribuidas para detectar acoplamiento
