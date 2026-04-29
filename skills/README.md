# Skills

Distributable Claude Code skills shipped with the `static-inference` analyzer.

Consumers of the MCP can copy these folders into their own project's `.claude/skills/` directory.

| Skill | Purpose |
|-------|---------|
| [static-inference-expert](./static-inference-expert) | Drives end-to-end project analysis through the MCP, applies decision rules tied to JSON attributes (viability, cohesion, coupling, sensitive_data, code_issues), and emits an evidence-backed extraction report. Includes batch scripts (`triage-proposals.sh`, `risk-report.sh`) and a `PostToolUse` hook for auto-reporting. |

See each skill's `INSTALL.md` for setup steps.
