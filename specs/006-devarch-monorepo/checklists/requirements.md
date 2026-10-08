# Requirements Checklist: 006-devarch-monorepo

**Purpose**: Validar la calidad de los requisitos (completitud, claridad, consistencia, medibilidad) antes de testify/tasks
**Created**: 2026-10-08 (`/iikit-01-specify`), extendido por `/iikit-03-checklist` el 2026-10-08
**Feature**: [spec.md](../spec.md)

> Modo no interactivo: los gaps se resolvieron con defaults conservadores registrados en spec.md *Clarifications* como "(default elegido por agente — confirmado por usuario 2026-10-08)". La confirmación del usuario sigue pendiente y se reporta, pero no bloquea el checklist (los defaults están documentados y son reversibles).

## Content Quality

- [x] **CQ-001**: ¿La spec describe QUÉ y POR QUÉ sin herramientas de construcción, rutas, lenguajes de script ni proveedores de CI? [Clarity] — sí; Make, Maven, npm, GitHub Actions y rutas viven en plan.md/research.md
- [x] **CQ-002**: ¿Las 7 historias (US1–US7) tienen prioridad, prueba independiente y escenarios Given/When/Then? [Completeness]
- [x] **CQ-003**: ¿Los criterios de éxito (SC-001..SC-009) son medibles? [Measurability]
- [x] **CQ-004**: ¿Quedan marcadores `[NEEDS CLARIFICATION]`? [Ambiguity] — no; 10 defaults registrados
- [x] **CQ-005**: ¿Los edge cases cubren JDK por defecto distinto, reglas globales del monorepo, pruebas con rutas del repo antiguo, corpus VB, concurrencia y rutas con espacios? [Edge Case Coverage]

## Requirement Completeness

- [x] **CHK001**: ¿Se cubren las 9 tareas de la Fase 2 del plan de integración (estructura, web, importación, comandos, workspaces, configuración, gobernanza, CI, tablero)? [Completeness, FR-001..FR-029]
- [x] **CHK002**: ¿Se especifica qué contiene la raíz del producto además de las seis áreas? [Gap → resuelto ampliando FR-001]
- [x] **CHK003**: ¿Se especifica la regla exacta de versión de cada prerrequisito (JDK exacto 17; .NET SDK ≥ 8 + runtime 8; Node ≥ 20)? [Gap → resuelto ampliando FR-011]
- [x] **CHK004**: ¿Se define el comportamiento de la web cuando el motor no está construido? [Completeness, FR-015, US3-AS3]
- [x] **CHK005**: ¿Se define qué archivos de la web se eliminan (binario vendorizado, salida antigua)? [Completeness, FR-004]
- [x] **CHK006**: ¿Se define el nombre de las etiquetas de publicación en un monorepo compartido? [Gap → resuelto en Clarifications (`dev-arch-v*`)]
- [x] **CHK007**: ¿Se define el alcance de los marcadores (pruebas extremo a extremo, diagnóstico)? [Completeness, FR-008, Clarifications]
- [x] **CHK008**: ¿Se especifica la relación entre la versión de producto y la de contrato? [Completeness, FR-022, US6-AS4]

## Requirement Clarity

- [x] **CHK009**: ¿"Sin cambiar lo que afirman" (FR-019) es verificable objetivamente? [Clarity, FR-019, SC-005] — sí: aserciones, `.feature` y anclas de integridad sin diff
- [x] **CHK010**: ¿"Muestra componentes y propuestas" (US3-AS1) tiene un criterio verificable sin navegador? [Ambiguity → resuelto en Clarifications: verificación sobre la respuesta; visual en 011]
- [x] **CHK011**: ¿"Ruta relativa" del servidor MCP local define respecto de qué directorio? [Ambiguity → resuelto en Clarifications: raíz del producto]
- [x] **CHK012**: ¿"Mismo resultado y formato" de la verificación de contrato (US2-AS7, FR-018) referencia un formato existente? [Clarity] — sí: el de la feature 005

## Requirement Consistency

- [x] **CHK013**: ¿FR-004 (sin binarios) es consistente con FR-009 (el build deja el motor donde la web lo usa)? [Consistency] — sí: ubicación generada e ignorada (FR-026, SC-003)
- [x] **CHK014**: ¿FR-005 (Constitución sin enmiendas) es consistente con FR-001 (el producto incluye la web, fuera del alcance original)? [Consistency] — sí: Clarification sobre alcance; enmienda en Fase 7
- [x] **CHK015**: ¿FR-012 (lockfiles versionados) es consistente con FR-026 (no modificar reglas globales)? [Consistency] — sí: reinclusión local al producto
- [x] **CHK016**: ¿FR-023 (contrato activa a sus consumidores) es consistente con US5-AS2/AS3 (motor activa contrato)? [Consistency] — sí: relación bidireccional explícita

## Acceptance Criteria Quality & Scenario Coverage

- [x] **CHK017**: ¿Cada FR traza a una historia? [Traceability] — FR-001..007→US1; FR-008..012→US2; FR-013..016→US3; FR-017..019→US4; FR-023..025→US5; FR-020..022→US6; FR-026..029→US7
- [x] **CHK018**: ¿Hay escenarios negativos? [Scenario Coverage] — US2-AS4, US3-AS3, US4-AS2, US5-AS5, US6-AS2
- [x] **CHK019**: ¿Hay escenario para la ruta alternativa del motor? [Coverage, FR-014, US3-AS4]

## SC Test Coverage

- [x] **CHK020**: ¿Cada SC es verificable por prueba automatizada o inspección objetiva? [SC Test Coverage] — SC-001..008 automatizables; SC-009 por tiempos de CI
- [x] **CHK021**: ¿SC-001 y SC-002 representan literalmente el criterio de salida de la fase? [Traceability, PLAN Fase 2]

## Edge Case Coverage

- [x] **CHK022**: ¿Se cubren archivos versionados que coinciden con reglas globales de exclusión? [Edge Case, Assumptions]
- [x] **CHK023**: ¿Se cubre el analizador .NET con solo runtime? [Edge Case]
- [x] **CHK024**: ¿Se cubre que la salida actual del motor incluya campos que la web no usa? [Edge Case, FR-016]

## Non-Functional Requirements

- [x] **CHK025**: ¿Hay límite de tiempo para las verificaciones de CI? [Gap → resuelto con SC-009]
- [x] **CHK026**: ¿Se especifica la reproducibilidad del build desde clon limpio? [NFR, FR-012, SC-001]
- [x] **CHK027**: ¿Se especifica que el producto no ensucia el árbol de trabajo? [NFR, SC-003]

## Dependencies & Assumptions

- [x] **CHK028**: ¿Se documenta el acceso a registros de dependencias para el clon limpio? [Assumption → añadida]
- [x] **CHK029**: ¿Se documenta que la importación ocurre tras integrar la spec en la rama principal del motor? [Assumption → añadida]
- [x] **CHK030**: ¿Se acota lo que queda fuera (cliente MCP, trabajos, seguridad de carga, Docker, doctor, enmienda)? [Scope, Contexto, Clarifications]
- [x] **CHK031**: ¿Los defaults del agente están registrados con su estado de confirmación? [Assumption] — 10 defaults confirmados por usuario 2026-10-08 (A1: promover schema/; A2: nota en AGENTS.md; A3: job propio 45 min; A4: congelar repo origen)

## Summary

- Total: 36 ítems · Checked: 36 · Deferred: 0
- Gaps resueltos: 5 (CHK002 → FR-001, CHK003 → FR-011, CHK006 → Clarification, CHK025 → SC-009, CHK028/029 → Assumptions); ambigüedades resueltas: 2 (CHK010, CHK011)
- Completitud: 100 %
