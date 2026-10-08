# Quickstart: 005-json-schema-contract

## Prerrequisitos

- JDK 17 (`export JAVA_HOME=$(/usr/libexec/java_home -v 17)` en macOS)
- .NET SDK 8 (o superior con `DOTNET_ROLL_FORWARD=Major`)
- Node 20+

## 1. Verificación completa

```bash
make schema-check
# Esperado: 4 frentes OK, exit 0
```

## 2. Validar una salida Java a mano

```bash
mvn -q package -DskipTests
java -jar target/java-dependency-extractor.jar test-for-demo/java-standar /tmp/si/out.json
jq '.meta.schema_version' /tmp/si/out.json /tmp/si/out_architecture.json /tmp/si/out_entrypoints.json
# "1.0.0" x3
npm --prefix schema run check -- --file /tmp/si/out.json --kind dependency-graph
```

## 3. Determinismo intacto

```bash
java -jar target/java-dependency-extractor.jar test-for-demo/java-standar /tmp/a/out.json
java -jar target/java-dependency-extractor.jar test-for-demo/java-standar /tmp/b/out.json
diff -r /tmp/a /tmp/b   # sin diferencias
```

## 4. Provocar divergencias (SC-004)

| Frente | Cómo provocarla | Resultado esperado |
|--------|-----------------|--------------------|
| Java | Añadir temporalmente un `@JsonProperty("foo")` a `Component` | `[schema-check] java: FAIL — ... additional property 'foo'` |
| .NET | Cambiar `Layer` de un componente fixture a `"Servicio"` | `[schema-check] dotnet: FAIL — ... layer` |
| TS | Editar `schema/1.0.0/common.schema.json` sin `npm run generate` | `[schema-check] ts: FAIL — generated/ out of date` |
| Versión | Cambiar `SchemaVersion.CURRENT` a `1.0.1` | `[schema-check] version: FAIL — java=1.0.1 schema=1.0.0` |

## 5. Regenerar tipos tras cambiar el contrato

```bash
npm --prefix schema run generate
git diff schema/generated/
```

Consultar `docs/SCHEMA.md` para decidir el incremento de versión y redactar la nota de migración.
