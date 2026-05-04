# Skills

Distributable agent skills shipped with the `static-inference` analyzer. One skill per supported AI agent — same analysis logic, agent-specific install paths and MCP config format.

| Skill | Agent | Install path | MCP config |
|-------|-------|-------------|------------|
| [static-inference-expert-claude](./static-inference-expert-claude) | Claude Code | `.claude/skills/` | `.mcp.json` |
| [static-inference-expert-kiro](./static-inference-expert-kiro) | Kiro | `.kiro/skills/` | `.kiro/settings/mcp.json` |

Both skills drive end-to-end project analysis through the MCP, apply the same decision rules tied to JSON attributes (viability, cohesion, coupling, sensitive_data, code_issues, layer, tables_used), and emit an evidence-backed extraction report. Batch scripts (`triage-proposals.sh`, `risk-report.sh`) are identical across both.

See each skill's `INSTALL.md` for agent-specific setup steps.
