# Contract: resolución del motor local por la web

Módulo: `web/src/server/engine/engine-path.ts`

```ts
export type EngineResolution =
  | { kind: 'resolved'; javaBin: string; jarPath: string }
  | { kind: 'not-built'; jarPath: string; message: string };

export function resolveEngine(env: NodeJS.ProcessEnv, cwd: string, exists: (p: string) => boolean): EngineResolution;
```

| Caso | Entrada | Resultado |
|------|---------|-----------|
| Defecto | sin `ENGINE_JAR_PATH`; `<cwd>/engine-dist/engine.jar` existe | `resolved`, `jarPath = <cwd>/engine-dist/engine.jar` |
| Override | `ENGINE_JAR_PATH=/x/e.jar` existe | `resolved`, `jarPath = /x/e.jar` |
| Override relativo | `ENGINE_JAR_PATH=../engine/target/java-dependency-extractor.jar` | resuelto contra `cwd` |
| Java | `JAVA_HOME=/jdk17` | `javaBin = /jdk17/bin/java`; sin `JAVA_HOME` → `java` |
| No construido | el jar resuelto no existe | `not-built`, `message` contiene `Engine not built`, la ruta y `make build` |

`POST /api/analyze` (sin cambios de request/response salvo el error):

| Situación | HTTP | Cuerpo |
|-----------|------|--------|
| éxito | 200 | `{ componentsData, archData }` (del motor actual; `componentsData.meta.schema_version = "1.0.0"`) |
| `not-built` | 500 | `{ error: "<message>" }` |
| fallo del motor | 500 | `{ error: "Failed to analyze the project ..." }` (existente) |

Invocación: `execFile(javaBin, ['-jar', jarPath, projectRoot, 'output.json'], { env: process.env })` — sin shell; rutas con espacios soportadas.
