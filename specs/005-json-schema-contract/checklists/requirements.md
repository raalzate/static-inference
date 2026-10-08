# Requirements Checklist: 005-json-schema-contract

**Purpose**: Validar la calidad de los requisitos (completitud, claridad, consistencia, medibilidad) antes de testify/tasks
**Created**: 2026-10-07 (`/iikit-01-specify`), extendido por `/iikit-03-checklist` el 2026-10-07
**Feature**: [spec.md](../spec.md)

> Modo no interactivo: los gaps se resolvieron con defaults conservadores registrados en spec.md *Clarifications* como "(default elegido por agente — confirmado por usuario 2026-10-07)".

## Content Quality

- [x] **CQ-001**: ¿La spec describe QUÉ y POR QUÉ sin librerías, dialecto de esquema, rutas ni lenguajes de generación? [Clarity] — sí; viven en plan.md/research.md
- [x] **CQ-002**: ¿Las 6 historias (US1–US6) tienen prioridad, prueba independiente y escenarios Given/When/Then? [Completeness]
- [x] **CQ-003**: ¿Los criterios de éxito son medibles? [Measurability, SC-001..SC-009]
- [x] **CQ-004**: ¿Quedan marcadores `[NEEDS CLARIFICATION]`? [Ambiguity] — no; 10 defaults registrados
- [x] **CQ-005**: ¿Los edge cases cubren valores abiertos, mapas dinámicos, campo obsoleto, grafo vacío, `roslyn-fallback`, VB, errores MCP e integridad referencial? [Edge Case Coverage]

## Requirement Completeness

- [x] **CHK001**: ¿Se especifica el contrato para los 3 artefactos y las 5 respuestas MCP? [Completeness, FR-001, FR-002]
- [x] **CHK002**: ¿Se define qué prevalece cuando la documentación y el output real difieren? [Clarity, FR-003]
- [x] **CHK003**: ¿Se enumeran explícitamente los catálogos cerrados y los campos abiertos? [Clarity, FR-005, Edge Cases]
- [x] **CHK004**: ¿Se define la estrictitud ante campos no declarados y la excepción de mapas dinámicos? [Completeness, FR-004]
- [x] **CHK005**: ¿Se especifica dónde vive la versión en los artefactos que hoy no tienen `meta`? [Completeness, FR-008]
- [x] **CHK006**: ¿Se define el comportamiento ante versión .NET ausente o distinta? [Gap → resuelto con FR-022]
- [x] **CHK007**: ¿Se especifica el comportamiento de la verificación cuando falta una herramienta de un frente? [Completeness, FR-015, US5-AS6]
- [x] **CHK008**: ¿Se define el alcance de la validación (estructural vs referencial)? [Gap → resuelto en Edge Cases]
- [x] **CHK009**: ¿La política de versionado clasifica cada tipo de cambio (mayor/menor/parche) y exige nota de migración? [Completeness, FR-019]

## Requirement Clarity & Consistency

- [x] **CHK010**: ¿"Proyectos de demostración disponibles" está cuantificado? [Ambiguity → resuelto: SC-001 enumera 6 proyectos]
- [x] **CHK011**: ¿FR-008 (meta en arquitectura/entrypoints) es consistente con la Clarification de no versionar `analyze_project`/`get_component_metrics`? [Consistency, FR-002, FR-008]
- [x] **CHK012**: ¿FR-010 (marca de tiempo opcional) es consistente con el principio IV y con US2-AS4/AS5? [Consistency]
- [x] **CHK013**: ¿FR-011 (no alterar otros campos/orden) es consistente con añadir `meta` nuevo en dos artefactos? [Consistency] — sí: es aditivo; el orden de los campos existentes no cambia
- [x] **CHK014**: ¿SC-006 cuantifica las divergencias documentales a resolver? [Measurability] — 7, listadas en Contexto

## Acceptance Criteria & Scenario Coverage

- [x] **CHK015**: ¿Cada FR traza a al menos una historia o escenario? [Traceability] — FR-001..007→US1; FR-008..011, 022→US2; FR-002, 014→US3; FR-017..018→US4; FR-012..016→US5; FR-019..021→US6/SC-008
- [x] **CHK016**: ¿Hay escenarios negativos (rechazo) además de positivos? [Scenario Coverage] — US1-AS4..6, US2-AS3, US4-AS3, US5-AS2..6
- [x] **CHK017**: ¿Cada SC es verificable por una prueba automatizada o una inspección objetiva? [SC Test Coverage] — SC-001..005, 007, 009 automatizables; SC-006 y SC-008 por inspección con conteo

## Non-Functional Requirements

- [x] **CHK018**: ¿Se especifica la restricción de huella (sin dependencias de ejecución nuevas)? [NFR, FR-021, SC-008]
- [x] **CHK019**: ¿Se especifica un límite de tiempo para la verificación? [Gap → resuelto con SC-009 < 10 min]
- [x] **CHK020**: ¿Se preserva el determinismo explícitamente? [NFR, FR-010, SC-003]

## Dependencies & Assumptions

- [x] **CHK021**: ¿Se documenta que el corpus VB externo es opcional para FR-013? [Assumption, FR-013 "cuando el corpus esté disponible"]
- [x] **CHK022**: ¿Se documenta que el consumidor web real llega en Fase 2 y queda fuera de alcance? [Assumption, US4]
- [x] **CHK023**: ¿Los 10 defaults del agente han sido confirmados por el usuario? [Assumption] — confirmado por usuario 2026-10-07 (incluye default 11: specs/ deja de estar en .gitignore)

## Summary

- Total: 28 ítems · Checked: 28 · Deferred: 0
- Gaps resueltos: 3 (CHK006 → FR-022, CHK008 → Edge Cases, CHK019 → SC-009); ambigüedades resueltas: 1 (CHK010 → SC-001)
- Completitud: 96% (100% de ítems no diferidos)

## Spec Quality Score: 9/10 (descontado por defaults pendientes de confirmar)
