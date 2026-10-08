// Shared helpers of the @static-inference/schema generator and checker.
// Everything here must stay deterministic (FR-018): no dates, sorted iteration, stable output.
import { readFileSync, readdirSync, statSync } from "node:fs";
import { dirname, join, relative, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";
import $RefParser from "@apidevtools/json-schema-ref-parser";

export const PACKAGE_DIR = resolve(dirname(fileURLToPath(import.meta.url)), "..");
export const VERSION = readFileSync(join(PACKAGE_DIR, "VERSION"), "utf8").trim();
export const VERSION_DIR = join(PACKAGE_DIR, VERSION);
export const BASE_IRI = `https://static-inference.dev/schema/${VERSION}/`;

export const BANNER =
  "/* eslint-disable */\n" +
  "// GENERATED FILE — DO NOT EDIT.\n" +
  `// Generated from @static-inference/schema contract ${VERSION} (schema/${VERSION}/*.schema.json)\n` +
  "// by schema/gen/generate.mjs. Regenerate with: npm --prefix schema run generate\n";

/**
 * Contract entry points: kind (as used by `check --kind`) → schema file, root type name, parse function.
 * The five MCP responses and the three documents (FR-017).
 */
export const ENTRIES = [
  { kind: "dependency-graph", file: "dependency-graph.schema.json", type: "DependencyGraph", fn: "parseDependencyGraph" },
  { kind: "architecture", file: "architecture.schema.json", type: "ConsolidatedArchitecture", fn: "parseConsolidatedArchitecture" },
  { kind: "entrypoints", file: "entrypoints.schema.json", type: "EntrypointsDocument", fn: "parseEntrypointsDocument" },
  { kind: "mcp/analyze_project", file: "mcp/analyze_project.response.schema.json", type: "AnalyzeProjectResponse", fn: "parseAnalyzeProjectResponse" },
  { kind: "mcp/get_dependency_graph", file: "mcp/get_dependency_graph.response.schema.json", type: "GetDependencyGraphResponse", fn: "parseGetDependencyGraphResponse" },
  { kind: "mcp/get_architecture_proposal", file: "mcp/get_architecture_proposal.response.schema.json", type: "GetArchitectureProposalResponse", fn: "parseGetArchitectureProposalResponse" },
  { kind: "mcp/get_api_contracts", file: "mcp/get_api_contracts.response.schema.json", type: "GetApiContractsResponse", fn: "parseGetApiContractsResponse" },
  { kind: "mcp/get_component_metrics", file: "mcp/get_component_metrics.response.schema.json", type: "GetComponentMetricsResponse", fn: "parseGetComponentMetricsResponse" },
];

/** All schema files of the current version, relative to VERSION_DIR, sorted. */
export function schemaFiles(dir = VERSION_DIR) {
  const out = [];
  const walk = (d) => {
    for (const name of readdirSync(d).sort()) {
      const full = join(d, name);
      if (statSync(full).isDirectory()) walk(full);
      else if (name.endsWith(".schema.json")) out.push(relative(dir, full).split(sep).join("/"));
    }
  };
  walk(dir);
  return out;
}

/** json-schema-ref-parser options resolving the contract `$id` base to local files (offline). */
export function refOptions(dir = VERSION_DIR) {
  return {
    resolve: {
      http: false,
      contract: {
        order: 1,
        canRead: (file) => file.url.startsWith(BASE_IRI),
        read: (file) => readFileSync(join(dir, file.url.slice(BASE_IRI.length).split("#")[0]), "utf8"),
      },
    },
  };
}

/** Reads a schema file of the current version. */
export function readSchema(file, dir = VERSION_DIR) {
  return JSON.parse(readFileSync(join(dir, file), "utf8"));
}

const ANNOTATIONS = new Set(["$schema", "$id", "$ref", "title", "description"]);

/** True when a root schema is a pure reference (only annotations + `$ref`), e.g. most MCP responses. */
export function isPureReference(schema) {
  return typeof schema.$ref === "string" && Object.keys(schema).every((k) => ANNOTATIONS.has(k));
}

/**
 * Dereferences the given root files in ONE pass so every shared definition keeps a single object identity
 * (generated declarations are then emitted once). Returns file → dereferenced schema.
 */
export async function dereferenceAll(files, dir = VERSION_DIR) {
  const bundle = { $id: BASE_IRI + "__bundle__.json", properties: {} };
  for (const file of files) bundle.properties[file] = { $ref: BASE_IRI + file };
  const resolved = await $RefParser.dereference(BASE_IRI + "__bundle__.json", bundle, refOptions(dir));
  return new Map(files.map((file) => [file, resolved.properties[file]]));
}

/**
 * Flattens `allOf` + `unevaluatedProperties: false` compositions of object schemas into a single closed
 * object (used by EntrypointsDocument), without mutating shared definitions.
 */
export function flattenAllOf(schema) {
  if (!schema || !Array.isArray(schema.allOf)) return schema;
  const merged = { type: "object", properties: {}, required: [] };
  for (const part of schema.allOf.map(flattenAllOf)) {
    Object.assign(merged.properties, part.properties ?? {});
    for (const r of part.required ?? []) if (!merged.required.includes(r)) merged.required.push(r);
  }
  for (const [k, v] of Object.entries(schema)) {
    if (k !== "allOf" && k !== "unevaluatedProperties") merged[k] = v;
  }
  if (schema.unevaluatedProperties === false) merged.additionalProperties = false;
  return merged;
}

/** Contract kind of a document file, inferred from its name (CLI artifacts, MCP fixtures, invalid fixtures). */
export function kindForFile(path) {
  const name = path.split(/[\\/]/).pop();
  const parent = path.split(/[\\/]/).slice(-2, -1)[0];
  if (parent === "mcp") return "mcp/" + name.replace(/\.json$/, "");
  if (name.endsWith("_architecture.json") || name.startsWith("architecture-")) return "architecture";
  if (name.endsWith("_entrypoints.json") || name.startsWith("entrypoints-")) return "entrypoints";
  if (name.endsWith(".json")) return "dependency-graph";
  return null;
}

/** Formats zod issues as `<dotted.path>: <message>` lines (path "(root)" for the document itself). */
export function formatIssues(issues) {
  return issues.map((i) => {
    const path = i.path.length ? i.path.join(".") : "(root)";
    const keys = i.keys ? ` [${i.keys.join(", ")}]` : "";
    return `${path}: ${i.message}${keys}`;
  });
}

/** Recursively lists `*.json` files below a directory (sorted), or [] when it does not exist. */
export function jsonFiles(dir) {
  const out = [];
  const walk = (d) => {
    let names;
    try {
      names = readdirSync(d).sort();
    } catch {
      return;
    }
    for (const name of names) {
      const full = join(d, name);
      if (statSync(full).isDirectory()) walk(full);
      else if (name.endsWith(".json")) out.push(full);
    }
  };
  walk(dir);
  return out;
}
