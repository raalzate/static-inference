# Static Inference — developer entry points.

.PHONY: schema-check schema-generate

## Verify Java, .NET and generated TypeScript types against the output contract (005-json-schema-contract).
schema-check:
	bash scripts/schema-check.sh

## Regenerate schema/generated/ from schema/<VERSION>/*.schema.json.
schema-generate:
	npm --prefix schema run generate
