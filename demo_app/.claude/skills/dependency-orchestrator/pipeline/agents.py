"""Agent SDK wrappers for the two LLM nodes: parser and backward mapper.

Code calls these — not the reverse. The heavy reference material is loaded
once per run via PromptContext and shared across all mapper invocations.
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any, Optional

from claude_agent_sdk import query, ClaudeAgentOptions, ResultMessage

# Skill directory (anchored to this file's location, CWD-independent)
SKILL_DIR = Path(__file__).resolve().parent.parent
CONFIGS_DIR = SKILL_DIR / "configs"
PROMPTS_DIR = SKILL_DIR / "prompts"


class PromptContext:
    """Loads heavy reference material once per run, shared across all mappers."""

    def __init__(self, cfg: Any, filtered_standards: str):
        self.rules = (CONFIGS_DIR / "backwards-mapping-rules.md").read_text()
        self.standards = filtered_standards
        self.durations = (CONFIGS_DIR / "duration-defaults.md").read_text()
        self.cfg = cfg

        # Load foundation templates via file read
        self.templates = ""
        for template_name in cfg.foundation_templates:
            template_path = CONFIGS_DIR / f"{template_name}.md"
            if template_path.exists():
                self.templates += f"\n### {template_name}\n{template_path.read_text()}\n"

        # Build fullstack conventions block
        self.conventions = self._build_conventions(cfg)

    def _build_conventions(self, cfg: Any) -> str:
        if not cfg.is_fullstack:
            return "Single-stack project — no cross-stack conventions needed."

        lines = [
            "**Node ID prefixes** — to avoid collisions across stacks:",
        ]
        if cfg.has_web_frontend:
            lines.append("  - Frontend nodes: prefix `fe_`")
        if cfg.has_backend:
            lines.append("  - Backend nodes: prefix `be_`")
        if cfg.has_mobile:
            lines.append("  - Mobile nodes: prefix `mob_`")

        lines.append("")
        lines.append("**Cross-stack edges:**")
        if cfg.has_web_frontend and cfg.has_backend:
            lines.append("  - `fe_api_client` → `be_api_routes`")
        if cfg.has_mobile and cfg.has_backend:
            lines.append("  - `mob_api_client` → `be_api_routes`")

        if cfg.monorepo and cfg.has_backend and (
            cfg.has_web_frontend or cfg.has_mobile
        ):
            lines.append("")
            lines.append(
                "**Shared setup:** monorepo — merge `be_project_setup` and "
                "`fe_project_setup` into a single `project_setup` node."
            )

        lines.append("")
        lines.append(
            "**Fullstack developers** — all developers work across stacks. "
            "Prefixes distinguish task type, not team assignment."
        )
        return "\n".join(lines)


def _safe_substitute(template: str, **kwargs: str) -> str:
    """Substitute placeholders tolerating literal braces in inlined markdown."""
    result = template
    for key, value in kwargs.items():
        result = result.replace(f"{{{key}}}", value)
    return result


async def _run_agent(prompt: str, options: ClaudeAgentOptions) -> None:
    """Run a Claude agent to completion, consuming all messages."""
    async for message in query(prompt=prompt, options=options):
        # We consume all messages; ResultMessage signals completion
        if isinstance(message, ResultMessage):
            return


async def run_mapper(
    cfg: Any,
    ctx: PromptContext,
    batch_path: str | Path,
    out_path: str | Path,
    *,
    error_context: str = "",
) -> dict:
    """Launch one backward-mapping subagent. Writes directly to out_path."""
    template = (PROMPTS_DIR / "backward-mapper.md").read_text()

    prompt = _safe_substitute(
        template,
        rules=ctx.rules,
        standards=ctx.standards,
        durations=ctx.durations,
        templates=ctx.templates,
        conventions=ctx.conventions,
        batch_path=str(batch_path),
        out_path=str(out_path),
        error_context=error_context,
    )

    options = ClaudeAgentOptions(
        model=cfg.model,
        permission_mode="acceptEdits",
        setting_sources=["project"],
        allowed_tools=["Read", "Bash"],
    )

    await _run_agent(prompt, options)

    # Verify output was written
    out = Path(out_path)
    if not out.exists():
        raise RuntimeError(f"Mapper did not write output to {out_path}")

    with open(out) as f:
        return json.load(f)
