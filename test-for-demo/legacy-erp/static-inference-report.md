# Análisis: legacy-erp (com.acme.erp)

> Generado: 2026-04-30 | Analizador: spoon (Java) | Fuente: MCP `analyze_project`

---

## Resumen

| Métrica | Valor |
|---------|-------|
| Componentes | 20 |
| LOC | 1 006 |
| Edges | 42 |
| Lenguaje | Java (Spoon) — EJB 3.x + Struts 1.x + JAX-WS + JMS |
| Propuestas | Alta=**0** · Media=**1** · Baja=**3** |
| Endpoints | 7 (3 REST/Struts + 4 SOAP) |
| Listeners JMS | 1 (`OrderEventMDB` — consumer) |
| Componentes con datos sensibles | 5 |

**Riesgos críticos:** `NotificationServiceBean` y `OrderServiceBean` usan `@Resource` (JNDI injection); `OrderAction` y `OrderApiServlet` hacen JNDI lookup directo; `AppConfig` lee via `System.getProperty()`. Todos en el cluster candidato a extracción.

---

## Candidatos a extracción — Alta viabilidad

**Ninguno.** El analizador no produjo propuestas con `viability == "Alta"`.  
No hay extracción recomendable sin trabajo previo.

---

## Condicional — Media viabilidad

### Propuesta 1 · "Componente de Orderweb y Web"
**`proposals[1].viability = "Media"`**

| Atributo | Valor | Regla | Estado |
|----------|-------|-------|--------|
| Cohesión avg | 0.697 | ≥ 0.5 | ✅ |
| Acoplamiento externo | 0.355 | < 0.6 | ✅ |
| Datos sensibles | true | requiere remediación | ⚠️ |
| Peso code issues | 7 (7×WARNING) | < 10 | ✅ |
| Cobertura entrypoint | `type="rest"` (7 ops) | ≠ "internal" | ✅ |
| Capas core mezcladas | Controlador + Negocio + Persistencia | ≤ 2 permitidas | ❌ R5 |
| LCOM avg | 0.63 | alto — responsabilidades múltiples | ⚠️ |

**Componentes (10):** `OrderDAO`, `OrderEventMDB`, `Order`, `OrderStatus`, `OrderServiceBean`, `OrderServiceLocal`, `OrderAction`, `OrderApiServlet`, `OrderSoapResponse`, `OrderWebService`

**Bloqueos para promover a Alta:**

1. **R5 — Layer mixing:** 3 capas core mezcladas (`Controlador`: OrderAction, OrderApiServlet / `Negocio`: OrderEventMDB, OrderServiceBean, OrderServiceLocal, OrderWebService / `Persistencia`: OrderDAO, Order). Dividir en al menos dos servicios:
   - **order-domain-service** → capa Negocio + Persistencia (OrderDAO, Order, OrderServiceBean, OrderServiceLocal, OrderEventMDB)
   - **order-api-gateway** → capa Controlador + Web (OrderAction, OrderApiServlet, OrderWebService, OrderSoapResponse)
2. **R2 — Sensitive data:** `proposals[1].metrics.sensitive = true`. Componentes afectados:
   - `OrderDAO` (`sensitive_data=true`) — revisar qué datos de orden son PII
   - `OrderEventMDB` (`sensitive_data=true`) — auditar payload JMS
   - `OrderServiceBean` (`secrets_references=["@Resource"]`) — externalizar recurso a config segura
3. **R8 — Null schemas en REST:** `GET /api/orders` y `PUT /api/orders` tienen `request_body_schema=null` y `response_schema=null`. Documentar contratos antes de exponer el servicio.
4. **Messaging — channel ownership:** `OrderEventMDB` consume JMS (`messaging_role=consumer`) pero no se identifica canal propietario. Documentar nombre de la queue/topic.
5. **LCOM alto (0.63):** `OrderServiceBean.lcom=0.889` — clase con múltiples responsabilidades. Aplicar SRP antes de extraer.

**Operaciones expuestas (`legacy_entrypoint.exposed_operations`):**

| Método | Path | Schema request | Schema response |
|--------|------|----------------|-----------------|
| STRUTS_ACTION | `/order.do` | null ⚠️ | null ⚠️ |
| GET | `/api/orders` | null ⚠️ | null ⚠️ |
| PUT | `/api/orders` | null ⚠️ | null ⚠️ |
| SOAP | `/OrderService/getOrder` | — | OrderSoapResponse ✅ |
| SOAP | `/OrderService/getOrdersByCustomer` | — | List ⚠️ (schema vacío) |
| SOAP | `/OrderService/getOrdersByStatus` | — | List ⚠️ (schema vacío) |
| SOAP | `/OrderService/updateOrderStatus` | — | OrderSoapResponse ✅ |

---

## Mantener en monolito — Baja viabilidad

| # | Nombre | Componentes | Razón |
|---|--------|-------------|-------|
| 0 | Componente de Notification y Inventory | CustomerDAO, Customer, CustomerServiceBean, InventoryServiceBean, NotificationServiceBean | `cohesion_avg=0.417 < 0.5`; aunque `data_jaccard=0.8` justifica cohesión baja, `external_coupling=0.5` y entrypoint `type="service"` (EJB/JNDI, sin endpoint REST). Datos sensibles presentes (`Customer` PII, `NotificationServiceBean @Resource`). No justifica extracción aislada. |
| 2 | Componente de Negocio (OrderItem + OrderItemRequest) | OrderItem, OrderItemRequest | `cohesion_avg=0.0`, `external_coupling=1.0 > 0.6` (R4 coupling ceiling), `data_jaccard=0.0`. `legacy_entrypoint.type="internal"` → módulo compartido, sin contrato externo. Solo 2 componentes. |
| 3 | Componente de Negocio (ProductDAO + Product) | ProductDAO, Product | `cohesion_avg=1.0` y `external_coupling=0.0` son excelentes, pero solo 2 componentes — overhead de módulo separado no justificado. `legacy_entrypoint.type="internal"`. Candidato a unirse a un futuro servicio de Inventory. |

---

## Riesgos transversales

### Secrets y datos sensibles

`project_metadata.secrets_locations = []` (sin archivos de configuración detectados con secretos en texto plano), pero hay 5 componentes con `sensitive_data=true` y referencias activas:

| Componente | Referencia | Riesgo |
|-----------|-----------|--------|
| `AppConfig` | `System.getProperty()` | Configuración leída de JVM args — externalizar a vault/env |
| `NotificationServiceBean` | `@Resource` | Inyección JNDI — reemplazar con config explícita al extraer |
| `OrderServiceBean` | `@Resource` | Inyección JNDI — idem |
| `OrderAction` | `JNDI lookup` | Lookup manual — eliminar antes de extraer |
| `OrderApiServlet` | `JNDI lookup` | Lookup manual — eliminar antes de extraer |

### Code issues con mayor peso (WARNING=1, INFO=0)

| Severidad | Archivo | Línea | Descripción |
|-----------|---------|-------|-------------|
| WARNING | `OrderAction.java` | 34, 61, 94 | String comparison con `==`/`!=` |
| WARNING | `OrderDAO.java` | 60, 75 | String comparison con `==`/`!=` |
| WARNING | `OrderApiServlet.java` | 36, 40 | String comparison con `==`/`!=` |
| INFO | `OrderAction.java` | 51 | Catch genérico `Exception`/`Throwable` |
| INFO | `OrderEventMDB.java` | 65 | Catch genérico `Exception`/`Throwable` |
| INFO | `OrderServiceBean.java` | 192 | `System.out` directo — usar logger |

**Peso total: 7** (umbral de bloqueo: 10). No bloquea extracción por sí solo, pero los WARNING de comparación de strings en `OrderDAO` son riesgosos en código de persistencia.

### Layer mixing

- **Propuesta 1** mezcla 3 capas core: Controlador + Negocio + Persistencia → R5 bloqueado. Ver plan de split arriba.

### Stack legacy de alto riesgo

| Dependencia | Versión | Riesgo |
|-------------|---------|--------|
| `org.apache.struts:struts-core` | 1.3.10 | EOL — múltiples CVEs conocidos |
| `org.hibernate:hibernate-core` | 4.3.11.Final | EOL |
| `log4j:log4j` | 1.2.17 | EOL — CVE-2019-17571 |
| `javax:javaee-api` | 7.0 | Obsoleto — migrar a jakarta |

---

## Próximos pasos accionables

1. **Remediación de secrets antes de cualquier extracción** — reemplazar JNDI lookups (`OrderAction`, `OrderApiServlet`) y `@Resource` injection (`OrderServiceBean`, `NotificationServiceBean`) con configuración externalizada (env vars / secrets manager). No extraer hasta completar este paso.

2. **Split de Propuesta 1 por capas** — dividir el cluster en `order-domain-service` (Negocio+Persistencia) y `order-api-gateway` (Controlador+Web) para cumplir R5. Re-evaluar viabilidad de cada sub-cluster por separado.

3. **Documentar contratos REST** — agregar schemas de request/response a `GET /api/orders`, `PUT /api/orders` y `STRUTS_ACTION /order.do` antes de exponer como servicio independiente.

4. **Refactorizar `OrderServiceBean`** (`lcom=0.889`, `cbo=21`) — aplicar SRP: separar lógica de negocio de orquestación. Es el componente con mayor acoplamiento del sistema.

5. **Evaluar `ProductDAO + Product` (Propuesta 3)** como núcleo de un futuro `inventory-service` fusionado con `InventoryServiceBean` (actualmente en Propuesta 0). Cohesión y acoplamiento excelentes; solo falta masa crítica.

6. **Actualizar dependencias críticas** — `log4j 1.2.17` (CVE activo), `struts-core 1.3.10` (EOL), `hibernate-core 4.3.11` antes de cualquier migración a producción.

7. **Instrumentar validación dinámica** — el análisis estático no detecta comportamiento en runtime (lazy loading Hibernate, resolución JNDI, routing JMS). Validar con pruebas de integración antes de cualquier cutover.

---

> **Nota:** Análisis estático — no refleja comportamiento en runtime. Cualquier candidato Alta (cuando se promueva) requiere validación dinámica antes de cutover a producción.
