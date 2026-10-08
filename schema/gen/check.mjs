#!/usr/bin/env node
// Verifies the contract package (FR-015..FR-018). Run through tsx: npm --prefix schema run check [-- options]
//   (no options)            generated/ up to date + VERSION/$id/const coherence + fixtures + producer samples
//   --samples-only          only ../target/schema-samples/**
//   --file <path> --kind <kind>   validate one document with the generated validators
import { mkdtempSync, readFileSync, readdirSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, relative, resolve } from "node:path";
import { BASE_IRI, PACKAGE_DIR, VERSION, VERSION_DIR, formatIssues, jsonFiles, kindForFile, readSchema, schemaFiles } from "./lib.mjs";
import { generate } from "./generate.mjs";
import { validatorsByKind } from "../generated/validators.ts";

const SAMPLES = resolve(PACKAGE_DIR, "..", "target", "schema-samples");
const failures = [];
const log = (msg) => console.log(`[schema] ${msg}`);
const fail = (msg) => {
  failures.push(msg);
  console.log(`[schema] FAIL ${msg}`);
};

function validate(file, kind) {
  const validator = validatorsByKind[kind];
  if (!validator) throw new Error(`unknown kind '${kind}' (known: ${Object.keys(validatorsByKind).join(", ")})`);
  const result = validator.safeParse(JSON.parse(readFileSync(file, "utf8")));
  return result.success ? [] : formatIssues(result.error.issues);
}

function checkVersionCoherence() {
  for (const file of schemaFiles()) {
    const schema = readSchema(file);
    if (schema.$id !== BASE_IRI + file) fail(`version: ${file} $id ${schema.$id} != ${BASE_IRI + file}`);
  }
  const constant = readSchema("common.schema.json").$defs.SchemaVersion.const;
  if (constant !== VERSION) fail(`version: SchemaVersion const ${constant} != VERSION ${VERSION}`);
  if (failures.length === 0) log(`version ${VERSION}: VERSION, $id and const agree`);
}

async function checkGenerated() {
  const tmp = mkdtempSync(join(tmpdir(), "si-schema-check-"));
  const files = await generate(tmp);
  let stale = 0;
  for (const name of files) {
    let committed = null;
    try {
      committed = readFileSync(join(PACKAGE_DIR, "generated", name), "utf8");
    } catch {
      committed = null;
    }
    if (committed !== readFileSync(join(tmp, name), "utf8")) {
      stale++;
      fail(`generated/${name} out of date — run: npm --prefix schema run generate`);
    }
  }
  if (stale === 0) log(`generated/ up to date (${files.join(", ")})`);
}

function checkFixtures() {
  for (const file of jsonFiles(join(PACKAGE_DIR, "fixtures", "valid"))) {
    const errors = validate(file, kindForFile(file));
    if (errors.length) fail(`fixture ${relative(PACKAGE_DIR, file)} rejected: ${errors.join("; ")}`);
  }
  for (const file of jsonFiles(join(PACKAGE_DIR, "fixtures", "invalid"))) {
    if (validate(file, kindForFile(file)).length === 0) fail(`invalid fixture ${relative(PACKAGE_DIR, file)} accepted`);
  }
  log(`fixtures checked (${readdirSync(join(PACKAGE_DIR, "fixtures", "invalid")).length} invalid)`);
}

function checkSamples() {
  const samples = jsonFiles(SAMPLES);
  let accepted = 0;
  for (const file of samples) {
    const errors = validate(file, kindForFile(file));
    if (errors.length) fail(`sample ${relative(SAMPLES, file)} rejected: ${errors.join("; ")}`);
    else accepted++;
  }
  log(samples.length ? `${accepted} sample(s) accepted of ${samples.length}` : `no producer samples in ${SAMPLES}`);
}

const args = process.argv.slice(2);
const opt = (name) => (args.includes(name) ? args[args.indexOf(name) + 1] : undefined);
try {
  if (opt("--file")) {
    const file = resolve(opt("--file"));
    const kind = opt("--kind") ?? kindForFile(file);
    const errors = validate(file, kind);
    if (errors.length) {
      console.log(`[schema] ${file} is NOT a valid ${kind}:`);
      for (const e of errors) console.log(`  ${e}`);
      process.exit(1);
    }
    log(`${file} is a valid ${kind}`);
  } else if (args.includes("--samples-only")) {
    checkSamples();
  } else {
    log(`contract ${VERSION} (${relative(PACKAGE_DIR, VERSION_DIR)}/)`);
    checkVersionCoherence();
    await checkGenerated();
    checkFixtures();
    checkSamples();
  }
} catch (e) {
  fail(e.message);
}
if (failures.length) {
  console.log(`[schema] RESULT: FAIL (${failures.length})`);
  process.exit(1);
}
