#!/usr/bin/env node
// Generates schema/generated/{types,validators,index}.ts from the contract (FR-017, FR-018).
// Usage: node gen/generate.mjs [--out <dir>]
import { mkdirSync, writeFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { compile } from "json-schema-to-typescript";
import { jsonSchemaToZod } from "json-schema-to-zod";
import { format } from "prettier";
import { BANNER, ENTRIES, PACKAGE_DIR, VERSION, dereferenceAll, flattenAllOf, isPureReference, readSchema } from "./lib.mjs";

/**
 * Loads every entry. Pure-reference roots (MCP responses that are a `$ref` to a document or definition)
 * become aliases of the referenced type instead of duplicated declarations.
 */
async function loadRoots() {
  const resolved = await dereferenceAll(ENTRIES.map((e) => e.file));
  return ENTRIES.map((entry) => {
    const raw = readSchema(entry.file);
    const schema = resolved.get(entry.file);
    if (isPureReference(raw)) {
      const target = ENTRIES.find((e) => raw.$ref.endsWith("/" + e.file) || raw.$ref === "../" + e.file);
      const aliasOf = target ? target.type : raw.$ref.split("/").pop();
      return { ...entry, aliasOf, description: raw.description, schema };
    }
    return { ...entry, schema: flattenAllOf(schema) };
  });
}

/** Collects every titled object definition reachable from the roots, in deterministic (title) order. */
function collectDefinitions(roots) {
  const byTitle = new Map();
  const visit = (node, isRoot) => {
    if (!node || typeof node !== "object") return;
    if (Array.isArray(node)) return node.forEach((n) => visit(n, false));
    if (node.title && !isRoot && !byTitle.has(node.title)) byTitle.set(node.title, node);
    for (const [key, value] of Object.entries(node)) {
      if (key !== "enum" && key !== "const" && key !== "required") visit(value, false);
    }
  };
  roots.forEach((r) => visit(r.schema, true));
  return new Map([...byTitle.entries()].sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0)));
}

async function generateTypes(allRoots) {
  const roots = allRoots.filter((r) => !r.aliasOf);
  // One synthetic root so shared definitions are declared once (deduplicated by title).
  const bundle = {
    title: "StaticInferenceContract",
    description: `Every document and MCP response of contract ${VERSION}.`,
    type: "object",
    additionalProperties: false,
    properties: Object.fromEntries(roots.map((r) => [r.type, r.schema])),
    required: roots.map((r) => r.type),
  };
  const ts = await compile(bundle, "StaticInferenceContract", {
    bannerComment: "",
    additionalProperties: false,
    declareExternallyReferenced: true,
    unreachableDefinitions: false,
    ignoreMinAndMaxItems: true,
    strictIndexSignatures: false,
    format: true,
    style: { singleQuote: false, semi: true, printWidth: 120, trailingComma: "all" },
  });
  const aliases = allRoots
    .filter((r) => r.aliasOf)
    .map((r) => `/**\n * ${r.description}\n */\nexport type ${r.type} = ${r.aliasOf};`)
    .join("\n\n");
  return BANNER + "\n" + ts.trimEnd() + "\n\n" + aliases + "\n";
}

/** Variable name of the zod schema of a titled definition. */
const schemaVar = (title) => `${title.charAt(0).toLowerCase()}${title.slice(1)}Schema`;

function generateValidators(roots, definitions) {
  const titleOf = new Map([...definitions.entries()].map(([title, node]) => [node, title]));
  const toZod = (node, ownTitle) => {
    const code = jsonSchemaToZod(node, {
      zodVersion: 3,
      parserOverride: (schema, refs) => {
        const title = titleOf.get(schema);
        if (title && title !== ownTitle && refs.path.length > 0) {
          return `z.lazy(() => ${schemaVar(title)})`;
        }
        return undefined;
      },
    });
    return { code };
  };

  const lines = [BANNER, 'import { z } from "zod";', ""];
  for (const [title, node] of definitions) {
    const { code } = toZod(node, title);
    lines.push(`/** Validator of the \`${title}\` definition. */`);
    lines.push(`export const ${schemaVar(title)}: z.ZodTypeAny = ${code};`, "");
  }
  for (const root of roots) {
    lines.push(`/** Validator of \`${root.type}\` (kind \`${root.kind}\`, ${root.file}). */`);
    if (root.aliasOf) {
      lines.push(`export const ${schemaVar(root.type)}: z.ZodTypeAny = ${schemaVar(root.aliasOf)};`, "");
    } else {
      const { code } = toZod(root.schema, root.type);
      lines.push(`export const ${schemaVar(root.type)}: z.ZodTypeAny = ${code};`, "");
    }
  }
  lines.push("/** Validators by contract kind (as accepted by `npm run check -- --kind`). */");
  lines.push("export const validatorsByKind: Record<string, z.ZodTypeAny> = {");
  for (const root of roots) lines.push(`  ${JSON.stringify(root.kind)}: ${schemaVar(root.type)},`);
  lines.push("};", "");
  for (const root of roots) {
    lines.push(`/** Parses (and validates) a \`${root.type}\`; throws a ZodError naming the failing path. */`);
    lines.push(`export const ${root.fn} = (doc: unknown): T.${root.type} => ${schemaVar(root.type)}.parse(doc) as T.${root.type};`);
  }
  lines.push(`/** Parses (and validates) a single \`Component\`. */`);
  lines.push(`export const parseComponent = (doc: unknown): T.Component => componentSchema.parse(doc) as T.Component;`);
  lines.splice(lines.indexOf('import { z } from "zod";') + 1, 0, 'import type * as T from "./types.ts";');
  return lines.join("\n").trimEnd() + "\n";
}

function generateIndex() {
  return (
    BANNER +
    "\n" +
    'export * from "./types.ts";\n' +
    'export * from "./validators.ts";\n' +
    `export const CONTRACT_VERSION = ${JSON.stringify(VERSION)} as const;\n`
  );
}

export async function generate(outDir) {
  const roots = await loadRoots();
  const definitions = collectDefinitions(roots);
  const files = {
    "types.ts": await generateTypes(roots),
    "validators.ts": await format(generateValidators(roots, definitions), {
      parser: "typescript",
      printWidth: 120,
      trailingComma: "all",
    }),
    "index.ts": generateIndex(),
  };
  mkdirSync(outDir, { recursive: true });
  for (const [name, content] of Object.entries(files)) writeFileSync(join(outDir, name), content, "utf8");
  return Object.keys(files);
}

const isMain = process.argv[1] && resolve(process.argv[1]) === resolve(new URL(import.meta.url).pathname);
if (isMain) {
  const outIndex = process.argv.indexOf("--out");
  const outDir = outIndex > 0 ? resolve(process.argv[outIndex + 1]) : join(PACKAGE_DIR, "generated");
  const written = await generate(outDir);
  console.log(`[schema] generated ${written.join(", ")} in ${outDir}`);
}
