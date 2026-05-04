# Análisis: NominaDestajo (dotnet-unstructured)

## ⚠️ Cobertura del análisis

- **Scan inicial:** 0 componentes (`componentCount == 0`, sin `.sln` en raíz)
- **Tier 1:** expansión `<Compile Include>` en `Api/NominaDestajo.csproj` → sin mejora (Roslyn-fallback ignora entradas cross-directorio)
- **Tier 2:** stubs `.csproj` + `_si_aggregate.sln` sintético → **7 componentes, 4 edges**
- **Archivos temporales:** `_si_aggregate.sln`, `Application_si_stub.csproj`, `Domain_si_stub.csproj` → restaurados ✅
- **Recomendación:** formalizar con `.sln` real + un `.csproj` por módulo (`Api`, `Application`, `Domain`) con `<ProjectReference>` entre ellos

---

## Resumen

| Métrica | Valor |
|---|---|
| Componentes | 7 |
| LOC | 531 |
| Edges | 4 |
| Lenguaje | .NET (roslyn-fallback) |
| Propuestas | Alta=1 · Media=0 · Baja=1 |
| Endpoints | 4 |
| Listeners mensajería | 0 |
| Riesgos críticos | 0 secrets · R6 peso=1 · R12 WARNING en PayrollCalculationService |

---

## Candidatos a extracción (Alta viabilidad)

| # | Nombre | Componentes | Cohesión | Acopl. ext. | Tablas | CC max | Tipo entrada | Operaciones expuestas | Decisión |
|---|---|---|---|---|---|---|---|---|---|
| 0 | Controllers + Payrollcalculation | 5 | 1.0 ✅ | 0.0 ✅ | — | 12 ⚠️ | REST | 4 endpoints | **EXTRAER** + refactor prereq |

### Verificación de reglas — Propuesta 0

| Regla | Resultado | Evidencia JSON |
|---|---|---|
| R1 Viability | ✅ Alta | `proposals[0].viability = "Alta"` |
| R2 Sensitive data | ✅ Limpio | `metrics.sensitive = false` · `secrets_locations = []` |
| R3 Cohesion floor | ✅ 1.0 ≥ 0.5 | `metrics.cohesion_avg = 1.0` |
| R4 Coupling ceiling | ✅ 0.0 ≤ 0.6 | `metrics.external_coupling = 0.0` |
| R5 Layer mixing | ✅ 2 core layers | Controlador + Negocio (Compartida excluida) |
| R6 Code quality | ✅ Peso 1 ≤ 10 | 1×WARNING en PayrollCalculationService |
| R7 Entrypoint | ✅ REST | `legacy_entrypoint.type = "rest"` |
| R8 Alignment | ⚠️ Schema gap | `POST /liquidar` → `request_body_schema = null` |
| R12 CC gate | ⚠️ WARNING | `PayrollCalculationService.complexity_max = 12` |

**Contrato REST expuesto:**
- `POST /api/nomina/destajo/liquidar` ⚠️ schema nulo
- `GET /api/nomina/destajo/{cia}/{sucursal}/{ano}/{periodo}`
- `GET /api/nomina/destajo/{cia}/{sucursal}/{ano}/{periodo}/{subper}/observaciones/export`
- `GET /api/nomina/destajo/verificar-acumulados`

---

## Mantener en monolito (Baja viabilidad)

### Propuesta 1 — Componente de Aplicación Principal

- **Componentes:** `EjecutarLiquidacionCommandHandler` · `ConsultarDestajoQueryHandler`
- **Razón:** `cohesion_avg = 0.0` + `external_coupling = 1.0` · `legacy_entrypoint.type = "internal"`
- **Nota:** handlers MediatR del application layer — deben acompañar Propuesta 0 si se extrae el microservicio.

---

## Riesgos transversales

**Secrets:** ninguno

**Code issues peso alto:** R6 limpio (peso máximo = 1)

**Complejidad ciclomática (R12):**

| Componente | CC max | Método | Línea | Severidad |
|---|---|---|---|---|
| `PayrollCalculationService` | **12** | `CalcularEmpleado` | 37 | WARNING |

**Layer mixing:** ninguna propuesta afectada

---

## Próximos pasos accionables

1. **Formalizar estructura .NET** — crear `NominaDestajo.sln` con proyectos `Api`, `Application`, `Domain` + `<ProjectReference>`.
2. **Incluir handlers MediatR** — `EjecutarLiquidacionCommandHandler` y `ConsultarDestajoQueryHandler` acompañan a Propuesta 0.
3. **⚠️ [R12 PREREQ] Descomponer `CalcularEmpleado` (CC=12, línea 37)** — extraer en métodos privados cohesivos (< 10 CC) antes del cutover.
4. **Documentar schema `EjecutarLiquidacionRequest`** — `POST /liquidar` tiene `request_body_schema = null`.
5. **Validar dinámicamente** antes del cutover.

---

*Generado por static-inference-expert · 2026-04-30 · Tier 2 · R1–R8, R11, R12*
