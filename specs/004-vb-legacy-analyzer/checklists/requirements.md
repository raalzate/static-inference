# Requirements Checklist: 004-vb-legacy-analyzer

> Reconstruido 2026-10-07 junto con la spec. Evaluado sobre la spec reconstruida.

## Content Quality

- [x] **CQ-001**: Spec describes WHAT the system does, not HOW — clases, APIs de Roslyn y estructura de archivos viven en plan.md
- [x] **CQ-002**: All user stories (US1–US5) have independently testable acceptance scenarios
- [x] **CQ-003**: Success criteria are measurable (SC-001 through SC-008, con umbrales numéricos)
- [x] **CQ-004**: No `[NEEDS CLARIFICATION]` markers remain unresolved
- [x] **CQ-005**: Edge cases are explicitly addressed (directorios mixtos, `.sln`, pre-SDK, `Designer.vb` literal, separadores)
- [x] **CQ-006**: Out-of-scope items are explicit (sección *Deferred* → feature 009)

## Requirement Completeness

- [x] **RC-001**: Formatos de entrada cubiertos (FR-001..FR-004, FR-007)
- [x] **RC-002**: Patrones de autogenerados enumerados (FR-010, FR-011) y tipos sintéticos (FR-012)
- [x] **RC-003**: Relaciones estructurales cubiertas (FR-013..FR-017)
- [x] **RC-004**: Reglas de capa y LOC explícitas (FR-020, FR-021, FR-025, FR-026)
- [x] **RC-005**: Compatibilidad de schema y no-regresión explícitas (FR-027, FR-028, SC-005)
- [x] **RC-006**: Determinismo explícito (FR-029, SC-008)
- [x] **RC-007**: Degradación controlada explícita (FR-005, FR-030..FR-033, SC-006)

## Feature Readiness / Traceability

- [x] **TR-001**: Cada FR traza a al menos una tarea en tasks.md (ver *FR coverage*)
- [x] **TR-002**: Cada SC tiene evidencia: test automatizado (SC-001, 005, 006, 007, 008) o test de corpus `Category=Corpus` (SC-002, 003, 004)
- [x] **TR-003**: IDs citados en commits/tests (FR-020, FR-021, FR-026, FR-030, FR-032, SC-002, SC-004, SC-008) preservados
- [ ] **TR-004**: `.feature` files y hash de aserciones en `context.json` — perdidos con `specs/`; requieren `/iikit-04-testify` (no bloqueante para el cierre)
- [ ] **TR-005**: SC-002/003/004 reproducibles en CI — dependen de corpus externo no redistribuible (diferido a 009)

## Known Risks

- FR-020 cambia la clasificación de clases C# `*Controller` sin evidencia HTTP ("Controlador" → "Negocio"). Aceptado como corrección; documentado en spec *Clarifications*.
- FR-033 se verifica indirectamente (suite verde y corpus sin excepciones); no hay test dedicado que afirme campos vacíos en VB.

## Spec Quality Score: 4/5 (descontado por trazabilidad TS-XXX/Gherkin no recuperable)
