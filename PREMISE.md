# Static Inference MCP Server Premise

## What

Static Inference es un motor de análisis estático para proyectos Java que extrae grafos de dependencias, infiere descomposición en microservicios mediante clustering, genera contratos OpenAPI desde controllers REST, y calcula métricas de calidad de código (CBO, LCOM). Actualmente funciona como CLI y el objetivo es exponerlo como un servidor MCP (Model Context Protocol) para que agentes LLM puedan invocar sus capacidades de análisis de forma programática.

## Who

- Arquitectos de software que necesitan evaluar la viabilidad de descomponer monolitos Java en microservicios
- Desarrolladores que trabajan con agentes LLM (Claude Code, Copilot, etc.) y quieren análisis estático integrado en su flujo conversacional
- Equipos de modernización de aplicaciones legacy Java/EJB

## Why

Las herramientas de análisis estático tradicionales generan reportes estáticos que requieren interpretación manual. Al exponer Static Inference como servidor MCP, los agentes LLM pueden consumir directamente los resultados del análisis (grafos de dependencia, propuestas de microservicios, contratos API, métricas) y ofrecer recomendaciones contextualizadas en lenguaje natural. Esto reduce la barrera de entrada para análisis arquitectónico y permite integración en flujos de trabajo asistidos por IA.

## Domain

- **Análisis estático de código**: Inspección de código fuente sin ejecución, usando AST (Abstract Syntax Trees) via Spoon
- **Descomposición en microservicios**: Clustering de componentes por responsabilidad de negocio, acoplamiento y cohesión
- **Model Context Protocol (MCP)**: Protocolo estándar para exponer herramientas (tools) y recursos (resources) a agentes LLM via transporte stdio/SSE
- **Métricas de calidad**: CBO (Coupling Between Objects), LCOM (Lack of Cohesion of Methods), viabilidad de descomposición

## Scope

**Dentro del alcance:**
- Refactoring del core para desacoplar la lógica de análisis del CLI
- Implementación de servidor MCP con transporte stdio
- Exposición de tools: análisis completo, grafo de dependencias, propuesta de arquitectura, contratos API, métricas por componente
- Exposición de resources: configuración y documentación de algoritmos
- Empaquetado como fat JAR con doble modo (CLI y MCP)

**Fuera del alcance:**
- Análisis de lenguajes distintos a Java
- Transporte SSE/HTTP para el servidor MCP (solo stdio en primera iteración)
- UI web o dashboard visual
- Modificación de los algoritmos de clustering o inferencia existentes
