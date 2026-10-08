// node:test suite of the generated contract package (US4: TS-028..TS-033). Run: npm --prefix schema test
import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, readFileSync, readdirSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
// @ts-ignore -- plain ESM helper without type declarations
import { BANNER, ENTRIES, PACKAGE_DIR, formatIssues, jsonFiles, kindForFile } from "../gen/lib.mjs";
// @ts-ignore -- plain ESM generator without type declarations
import { generate } from "../gen/generate.mjs";
import { validatorsByKind } from "../generated/validators.ts";
import * as generated from "../generated/index.ts";

const GENERATED = join(PACKAGE_DIR, "generated");
const FIXTURES = join(PACKAGE_DIR, "fixtures");
const SAMPLES = join(PACKAGE_DIR, "..", "target", "schema-samples");

const read = (path: string) => JSON.parse(readFileSync(path, "utf8"));

test("TS-028: types exist for the three documents, AnalyzeProjectResponse and Component", () => {
  const types = readFileSync(join(GENERATED, "types.ts"), "utf8");
  for (const name of ["DependencyGraph", "ConsolidatedArchitecture", "EntrypointsDocument", "AnalyzeProjectResponse", "Component"]) {
    assert.match(types, new RegExp(`export (interface|type) ${name}\\b`), `missing type ${name}`);
  }
});

test("TS-028: a runtime validator exists for each document and each MCP response", () => {
  const kinds = ENTRIES.map((e: { kind: string }) => e.kind);
  assert.equal(kinds.length, 8);
  for (const kind of kinds) assert.ok(validatorsByKind[kind], `no validator for ${kind}`);
  for (const fn of ["parseDependencyGraph", "parseConsolidatedArchitecture", "parseEntrypointsDocument", "parseAnalyzeProjectResponse", "parseComponent"]) {
    assert.equal(typeof (generated as Record<string, unknown>)[fn], "function", `missing ${fn}`);
  }
});

for (const file of jsonFiles(join(FIXTURES, "valid"))) {
  const kind = kindForFile(file);
  test(`TS-029: valid fixture ${file.slice(FIXTURES.length + 1)} is accepted as ${kind}`, () => {
    const result = validatorsByKind[kind].safeParse(read(file));
    assert.ok(result.success, result.success ? "" : formatIssues(result.error.issues).join("\n"));
  });
}

const EXPECTED_INVALID_PATHS: Record<string, string> = {
  "graph-missing-component-id.json": "components.0.id",
  "graph-layer-out-of-catalogue.json": "components.0.layer",
  "graph-unknown-property.json": "components.0",
  "graph-missing-schema-version.json": "meta.schema_version",
  "architecture-bad-viability.json": "proposals.0.viability",
};

for (const name of readdirSync(join(FIXTURES, "invalid")).sort()) {
  const file = join(FIXTURES, "invalid", name);
  const kind = kindForFile(file);
  test(`TS-030: invalid fixture ${name} is rejected as ${kind} naming its path`, () => {
    const result = validatorsByKind[kind].safeParse(read(file));
    assert.equal(result.success, false, `${name} was accepted`);
    const paths = result.success ? [] : result.error.issues.map((i: { path: (string | number)[] }) => i.path.join("."));
    assert.ok(EXPECTED_INVALID_PATHS[name], `no expected path declared for ${name}`);
    assert.ok(paths.includes(EXPECTED_INVALID_PATHS[name]), `expected ${EXPECTED_INVALID_PATHS[name]} in ${paths.join(", ")}`);
  });
}

test("TS-031: every producer sample in target/schema-samples is accepted", (t) => {
  const samples = jsonFiles(SAMPLES);
  if (samples.length === 0) {
    t.skip("no producer samples (run the Java/.NET contract tests first)");
    return;
  }
  for (const file of samples) {
    const result = validatorsByKind[kindForFile(file)].safeParse(read(file));
    assert.ok(result.success, `${file}: ${result.success ? "" : formatIssues(result.error.issues).join("; ")}`);
  }
});

test("TS-032: regenerating twice is byte-identical to the committed generated/ files", async () => {
  const first = mkdtempSync(join(tmpdir(), "si-schema-gen-a-"));
  const second = mkdtempSync(join(tmpdir(), "si-schema-gen-b-"));
  const files: string[] = await generate(first);
  await generate(second);
  for (const name of files) {
    const committed = readFileSync(join(GENERATED, name), "utf8");
    assert.equal(readFileSync(join(first, name), "utf8"), committed, `generated/${name} is out of date`);
    assert.equal(readFileSync(join(second, name), "utf8"), committed, `generation of ${name} is not deterministic`);
  }
});

test("TS-033: every generated file starts with the generated/do-not-edit banner", () => {
  for (const name of readdirSync(GENERATED).sort()) {
    const content = readFileSync(join(GENERATED, name), "utf8");
    assert.ok(content.startsWith(BANNER), `${name} lacks the banner`);
    assert.match(content, /GENERATED FILE — DO NOT EDIT/);
  }
});
