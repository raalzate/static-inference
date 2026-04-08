<!-- Sync Impact Report
Version: 1.0.0 (initial ratification)
Changes: Initial constitution for Static Inference MCP Server
Follow-up: Run /iikit-01-specify to create feature specification
-->

# Static Inference Constitution

## Core Principles

### I. Backward Compatibility (NON-NEGOTIABLE)

- Existing CLI functionality MUST remain fully operational
  after MCP integration
- All current JSON output formats are public contracts;
  changes require explicit versioning
- No behavioral regression: identical inputs MUST produce
  identical outputs regardless of execution mode (CLI vs MCP)

**Rationale**: Users and downstream systems depend on the
current CLI interface. Breaking it undermines trust and
adoption of the new MCP mode.

### II. Single Source of Truth for Analysis Logic

- All analysis, inference, and metric computation MUST live
  in a shared core layer
- CLI and MCP entry points MUST be thin adapters that
  delegate to the shared core
- Duplication of business logic across entry points is
  forbidden

**Rationale**: Divergent analysis paths produce inconsistent
results. A single core ensures correctness regardless of how
the tool is invoked.

### III. Test-First Development (NON-NEGOTIABLE)

- All new code MUST follow TDD Red-Green-Refactor cycle
- New functionality MUST have failing tests before
  implementation code is written
- Test assertions are the specification; modifying assertions
  to pass tests is forbidden — fix the production code instead
- Existing tests MUST continue passing after every change

**Rationale**: The tool produces architectural recommendations
that inform critical decisions. Untested code risks silently
wrong analysis, which is worse than no analysis at all.

### IV. Deterministic and Reproducible Output

- Given the same source project, the tool MUST produce
  identical results across runs
- No randomness, timestamps, or environment-dependent values
  in analysis output
- MCP tool responses MUST be valid JSON matching documented
  schemas

**Rationale**: Reproducibility is essential for trusting
architectural recommendations. LLM agents need predictable
structured output to reason correctly.

### V. Graceful Degradation and Clear Error Reporting

- Analysis failures on individual components MUST NOT abort
  the entire analysis
- Errors MUST be reported with context: what failed, why,
  and what was skipped
- MCP error responses MUST follow the protocol's error format
  with actionable messages

**Rationale**: Real-world Java projects are messy. Partial
results with clear warnings are more useful than total failure
on the first unparseable file.

### VI. Minimal Footprint

- New dependencies MUST be justified and minimal
- The tool MUST remain a single deployable artifact (fat JAR)
- No runtime infrastructure requirements beyond a JVM
- Complexity MUST be justified by concrete user value

**Rationale**: The tool's strength is zero-setup static
analysis. Adding infrastructure requirements or bloated
dependencies undermines the core value proposition.

## Quality Standards

- All public methods in the shared core MUST have unit tests
- Integration tests MUST verify end-to-end analysis on
  representative sample projects
- MCP tool handlers MUST have contract tests validating
  request/response schemas
- Code coverage for new code MUST not decrease below the
  existing project baseline
- Static analysis warnings MUST be resolved before merging

## Development Workflow

- Every change goes through a feature branch and pull request
- PRs MUST include tests demonstrating the change works
- Breaking changes to JSON output schemas require a version
  bump and migration documentation
- Commit messages follow conventional commits format

## Governance

- This constitution supersedes all other development
  practices for this project
- Amendments require explicit user approval, version
  increment, and migration plan
- All PRs and code reviews MUST verify compliance with
  these principles
- Conflicts between principles are resolved by their
  ordering (I > II > III > IV > V > VI)

**Version**: 1.0.0 | **Ratified**: 2026-03-25 | **Last Amended**: 2026-03-25
