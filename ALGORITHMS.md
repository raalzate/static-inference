# Algoritmos del Motor de Inferencia de Microservicios

Este documento describe los algoritmos técnicos utilizados para consolidar clusters, generar nombres de microservicios y clasificar viabilidad.

## 1. InterClusterGraph: Cálculo de Relaciones Entre Clusters

### Propósito
Calcula la fuerza de relación entre pares de clusters usando múltiples señales para determinar si deben consolidarse.

### Señales Calculadas

#### 1.1 Table Jaccard Index
Mide similitud de tablas compartidas entre clusters.

```
tableJaccard(A, B) = |tables(A) ∩ tables(B)| / |tables(A) ∪ tables(B)|
```

- **Rango**: [0, 1]
- **Interpretación**: 
  - 1.0 = Ambos clusters usan exactamente las mismas tablas
  - 0.5 = Comparten la mitad de las tablas
  - 0.0 = No comparten tablas

#### 1.2 Call Density
Densidad normalizada de llamadas entre clusters.

```
calls_AB = count(methods in A calling methods in B)
calls_BA = count(methods in B calling methods in A)
maxPossible = |A| × |B|

callDensity(A, B) = (calls_AB + calls_BA) / maxPossible
```

- **Rango**: [0, 1]
- **Interpretación**:
  - >0.5 = Alta densidad de llamadas (muy acoplados)
  - 0.1-0.5 = Densidad moderada
  - <0.1 = Débilmente acoplados

#### 1.3 Token Similarity
Similitud de tokens de dominio extraídos de componentes role-bearing.

**Extracción de tokens**:
```java
// Solo de componentes con roles arquitectónicos
String[] tokens = componentName
    .replaceAll("(Service|Repository|Controller|Entity|Dto)$", "")
    .split("(?=[A-Z])");
```

**Cálculo de similitud**:
```
tokenSimilarity(A, B) = |tokens(A) ∩ tokens(B)| / |tokens(A) ∪ tokens(B)|
```

- **Rango**: [0, 1]
- **Ejemplo**: 
  - "OrderService" y "OrderRepository" → tokens comunes: {Order} → alta similitud
  - "UserService" y "ProductRepository" → sin tokens comunes → 0.0

#### 1.4 Event Links
Detección de acoplamiento por eventos (publisher→listener).

```
hasEventLink(A, B) = ∃ publisher in A AND ∃ listener in B
```

- **Valor**: 0.0 o 1.0 (binario)
- **Patrones detectados**:
  - Nombres con "Publisher" llamando a nombres con "Listener"
  - Indicador de arquitectura event-driven

### Evidence Score Final

```
evidenceScore(A, B) = 0.25 × tableJaccard(A, B) +
                      0.35 × callDensity(A, B) +
                      0.30 × tokenSimilarity(A, B) +
                      0.10 × eventLinks(A, B)
```

**Pesos justificados**:
- **35% Call Density**: Principal indicador de acoplamiento funcional
- **30% Token Similarity**: Indica cohesión de dominio
- **25% Table Jaccard**: Cohesión de datos compartida
- **10% Event Links**: Señal binaria complementaria

**Umbral de arista**: `evidenceScore > 0.1` para crear arista en el grafo

### Ejemplo Práctico

**Cluster A**: {OrderService, OrderRepository, OrderEntity}
**Cluster B**: {OrderController, OrderDto}

```
tableJaccard = 1.0     (ambos usan tabla "order")
callDensity = 0.4      (OrderController llama OrderService frecuentemente)
tokenSimilarity = 1.0  (ambos tienen token "Order")
eventLinks = 0.0       (no hay publishers/listeners)

evidenceScore = 0.25×1.0 + 0.35×0.4 + 0.30×1.0 + 0.10×0.0
              = 0.25 + 0.14 + 0.30 + 0.0
              = 0.69  ✅ FUERTE EVIDENCIA PARA CONSOLIDAR
```

---

## 2. ClusterConsolidator: Algoritmo Greedy de Consolidación

### Propósito
Agrupa clusters relacionados usando evidencia del grafo, con guardrails para evitar consolidaciones incorrectas.

### Algoritmo

```
ENTRADA: clusters[], interClusterGraph
SALIDA: groups[] (sets de cluster IDs consolidados)

1. **Fase 0: Pre-consolidación por Nombre de Dominio**
   - Agrupar clusters que generan el mismo nombre de negocio (ej. "Componente de Order").
   - Fusionar si son compatibles (mismo tipo de infra/negocio, tamaño combinado <= 50).

2. **Fase 1: Consolidación basada en Grafo**
   - Ordenar aristas del grafo por evidenceScore (descendente)
   - Para cada arista (A, B, score):
       Si shouldMerge(A, B, score):
           Fusionar grupos de A y B

3. Retornar grupos finales
```

### Función `shouldMerge(A, B, score)`

Evaluación multi-criterio con guardrails:

#### Criterio 1: Evidence Score Threshold
```
score >= 0.65 AND strongSignalCount >= 2
```

**Strong signals**: 
- `tableJaccard >= 0.4`
- `callDensity >= 0.35`
- `tokenSimilarity >= 0.6`
- `eventLinks` (presencia)

**Justificación**: Alta evidencia requiere múltiples señales confirmatorias con umbrales específicos.

#### Criterio 2: Support Library Guard
```
NOT (isSupport(A) XOR isSupport(B))
```

**isSupport(X)**: ≥80% de componentes tienen keywords de infraestructura:
- `application`, `config`, `security`, `auth`, `swagger`, `main`
- `exception`, `error`, `filter`, `interceptor`, `aspect`

**Justificación**: Prevenir mezclar librerías de soporte con lógica de negocio.

#### Criterio 3: Size Guard
```
NOT (totalSize > 40 AND tokenSimilarity < 0.75)
```

**Justificación**: Evitar mega-servicios heterogéneos. Permitir tamaño grande solo si dominio muy cohesivo.

#### Criterio 4: Strong Candidate Separation
```
NOT (isBothStrongCandidates(A, B) AND isWeak(callDensity, tableJaccard))
```

**isBothStrongCandidates**: cohesión ≥70%, acoplamiento <30%, tamaño ≥3

**isWeak**: callDensity < 0.15 AND tableJaccard < 0.2

**Justificación**: Preservar separación de candidatos fuertes débilmente acoplados.

### Ejemplo de Consolidación

**Entrada**:
- Cluster 0: {ItemService, ItemRepository} - cohesión 0.9
- Cluster 1: {ItemController, ItemDto} - cohesión 0.8
- Cluster 2: {OrderService, OrderRepository} - cohesión 0.85
- Cluster 3: {ConfigService, SecurityConfig} - support

**Aristas ordenadas**:
1. (0↔1): score=0.75, tableJaccard=1.0, callDensity=0.5, tokenSim=1.0
2. (0↔2): score=0.3, tableJaccard=0.0, callDensity=0.2, tokenSim=0.0
3. (1↔3): score=0.2, tableJaccard=0.0, callDensity=0.1, tokenSim=0.0

**Proceso**:
1. Evaluar (0↔1): score=0.75 ✅, no support ✅, size=4 ✅ → **MERGE** → Grupo {0,1}
2. Evaluar (0↔2): score=0.3 ❌ umbral → NO MERGE
3. Evaluar (1↔3): score=0.2 ❌ umbral → NO MERGE

**Salida**:
- Grupo 1: {0, 1} → "Microservicio de Item"
- Grupo 2: {2} → "Microservicio de Order"
- Grupo 3: {3} → "Librería de Configuración y Seguridad"

---

## 3. MicroserviceNameGenerator: Generación Automática de Nombres

### Propósito
Generar nombres de negocio human-readable sin hardcoding de dominios.

### Algoritmo

#### 3.1 Detección de Tipo (Infraestructura vs Negocio)

```
infraKeywords = {config, security, auth, swagger, ...}

isInfra = ANY component name contains infraKeywords

IF isInfra:
    return infraKeywordBasedName()
ELSE:
    return domainTokenBasedName()
```

#### 3.2 Nombres de Infraestructura

**Mapeo de keywords**:
```java
config → "Configuración"
security → "Seguridad"
auth → "Autenticación"
swagger → "Documentación API"
```

**Formato**: "Componente de {keyword1} & {keyword2}"

**Ejemplo**:
- Input: {SecurityConfig, AuthFilter}
- Output: "Componente de Seguridad & Autenticación"

#### 3.3 Nombres de Negocio

**Paso 1: Extracción de tokens de dominio**

```java
for each component:
    // Solo role-bearing components (Entity, Service, Repository, etc.)
    if hasArchitecturalRole(component):
        tokens = extractDomainTokens(component)
```

**Paso 2: Exclusión de tokens técnicos**

```java
EXCLUDE_TOKENS = {
    "entity", "dto", "model", "data", "event", "command", "query",
    "repository", "service", "controller", "impl",
    "api", "rest", "http", "adapter", "port",
    "jpa", "repo", "dao", "operations",
    "listener", "publisher", "handler", "factory"
    // + 30 más...
}

domainTokens = tokens - EXCLUDE_TOKENS
```

**Paso 3: TF-IDF para selección de top tokens**

```
TF(token) = count(token) / total_tokens
IDF(token) = 1 (simplificado, un solo documento)

score(token) = TF(token) × frequency(token)

topTokens = top 2 tokens por score
```

**Paso 4: Formato final**

```
IF topTokens.size == 1:
    name = "Componente de {token1}"
ELSE:
    name = "Componente de {token1} y {token2}"
```

**Ejemplo completo**:

Input: {OrderEntity, OrderService, OrderRepository, OrderLineItem, OrderDto}

```
1. Tokens extraídos: {Order, OrderLine}
2. Excluir: {Entity, Service, Repository, Dto, Item} → queda {Order, OrderLine}
3. TF-IDF: Order=4/5, OrderLine=1/5 → top 2: {Order, OrderLine}
4. Output: "Componente de Order y OrderLine"
```

---

## 4. ViabilityScorer: Clasificación de Viabilidad

### Propósito
Clasificar propuestas consolidadas en Alta/Media/Baja viabilidad para guiar implementación.

### Fórmula de Viabilidad

```
score = 0.5 × cohesionAdj +
        0.35 × (1 - externalCoupling) +
        0.15 × dataCohesion

// Penalizaciones aplicadas al score final
IF totalSize < 3:
    score = score * 0.6  (Penalización por tamaño muy pequeño)

ELSE IF totalSize > 50 AND internalDensity < 0.5:
    score = score * 0.7  (Penalización por monolito no cohesivo)
```

#### 4.1 Cohesion Adjusted

```
averageCohesion = average(cohesion of all clusters provided)
internalDensity = edgesWithinClusters / possibleEdges

cohesionAdj = 0.7 × averageCohesion + 0.3 × internalDensity
```

#### 4.2 External Coupling

```
totalCalls = internalCalls + externalCalls

externalCoupling = externalCalls / totalCalls
```

- Rango: [0, 1]
- 0.0 = No hay llamadas externas (independiente)
- 1.0 = Todas las llamadas son externas (acoplado)

#### 4.3 Data Cohesion

```
dataCohesion = max(0.5, data_jaccard)
```

- Usa Jaccard de tablas compartidas
- Floor de 0.5 para no penalizar demasiado

### Clasificación

```
IF viabilityScore >= 0.7:
    viability = "Alta"
ELSE IF viabilityScore >= 0.5:
    viability = "Media"
ELSE:
    viability = "Baja"
```

### Rationale Generation

Genera explicaciones en español:

Tambien calcula métricas de calidad de código:
- **CBO (Coupling Between Objects)**: Bajo <= 5, Alto > 10.
- **LCOM (Lack of Cohesion of Methods)**: Bajo <= 0.3, Alto > 0.6.

Genera explicaciones detalladas basadas en métricas:

```
IF cohesionAdj >= 0.7:
    "✅ Alta cohesión interna..."
ELSE IF cohesionAdj >= 0.5:
    "⚠️ Cohesión moderada..."

IF externalCoupling < 0.3:
    "✅ Bajo acoplamiento externo..."

IF cbo > 10:
    "❌ CBO alto..."
```

### Recommended Actions

Acciones específicas por viabilidad:

**Alta**:
- ✅ Diseñar como microservicio independiente
- ✅ Definir API pública con contratos claros
- ✅ Asignar base de datos exclusiva
- ✅ Implementar patrones de resiliencia

**Media**:
- ⚠️ Refactorizar para mejorar cohesión interna
- ⚠️ Reducir dependencias externas antes de separar
- ⚠️ Consolidar con clusters relacionados si es posible

**Baja**:
- ❌ No implementar como microservicio en estado actual
- ❌ Analizar si pertenece a otro contexto de negocio
- ❌ Considerar fusionar con microservicios relacionados

### Ejemplo Numérico

**Propuesta**: "Componente de Order"

```
Clusters: {1, 4}
Components: 8
Average Cohesion: 0.65
Internal density: 0.35
Cohesion Adjusted: 0.7*0.65 + 0.3*0.35 = 0.56

External coupling: 0.2
Data cohesion: 0.7

Base Score = 0.5*0.56 + 0.35*(0.8) + 0.15*0.7
           = 0.28 + 0.28 + 0.105
           = 0.665

Size penalty: None (size 8)

Final Score: 0.665 → MEDIA
(Rationale incluye métricas CBO/LCOM si disponibles)
```

---

## 5. Flujo Completo del Pipeline

```
1. ProjectAnalyzer → DependencyGraph (componentes, llamadas, tablas)
2. InferenceEngine → Clusters (agrupación por dominio y tablas)
3. InterClusterGraph → Aristas con evidenceScore
4. ClusterConsolidator → Grupos consolidados
5. MicroserviceNameGenerator → Nombres de negocio
6. ViabilityScorer → Clasificación Alta/Media/Baja
7. Output → *_architecture.json
```

---

## 6. Limitaciones y Supuestos

### Supuestos
- Código Java bien estructurado con nombres significativos
- Uso de patrones arquitectónicos reconocibles (Entity, Service, Repository, etc.)
- Tablas de BD mapeadas correctamente en anotaciones JPA o JDBC

### Limitaciones
- No considera aspectos de runtime (carga, latencia)
- Basado en análisis estático (no observa tráfico real)
- Nombres generados dependen de calidad de nombres en código
- No detecta bounded contexts de DDD automáticamente

### Mejoras Futuras
- Análisis de logs de producción para ajustar pesos
- Machine learning para mejorar detección de dominios
- Integración con event storming para validar fronteras
- Análisis de transacciones distribuidas para detectar acoplamiento
