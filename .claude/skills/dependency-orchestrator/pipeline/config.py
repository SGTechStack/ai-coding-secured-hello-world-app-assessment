"""RunConfig — the airlock between the conversational skill and the mechanical driver.

Validates `run-config.json` eagerly so bad input fails in the first second,
not 25 minutes into a mapping run.

Story metadata (count, groups, releases, story IDs) is extracted from the
structured YAML stories file at runtime — not stored in run-config.json.
"""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path
from typing import Literal, Optional

from pydantic import BaseModel, Field, PrivateAttr, model_validator

Role = Literal["pm", "dev"]
ProjectType = Literal[
    "web_fullstack",
    "web_frontend_only",
    "mobile_fullstack",
    "mobile_frontend_only",
    "backend_only",
]
Mode = Literal["fresh", "addon_current_release", "addon_new_release"]

DEFAULT_MODEL = "claude-sonnet-4-6"

SKILL_DIR = Path(__file__).resolve().parent.parent
VALIDATE_SCRIPT = SKILL_DIR / "scripts" / "validate-stories.py"

# Each project type maps to the foundation template(s) it loads.
_PROJECT_TYPE_TEMPLATES = {
    "web_fullstack":        ["web-frontend-foundations", "backend-api-foundations"],
    "web_frontend_only":    ["web-frontend-foundations"],
    "mobile_fullstack":     ["mobile-app-foundations", "backend-api-foundations"],
    "mobile_frontend_only": ["mobile-app-foundations"],
    "backend_only":         ["backend-api-foundations"],
}


class FeatureGroup(BaseModel):
    id: str
    name: str
    priority: int = 1


class StoriesMeta(BaseModel):
    """Metadata extracted from the structured YAML stories file."""
    story_count: int
    group_count: int
    release_count: int
    groups: list[dict] = Field(default_factory=list)
    releases: list[dict] = Field(default_factory=list)
    story_ids: list[str] = Field(default_factory=list)


class RunConfig(BaseModel):
    role: Role
    project_name: str
    model: str = DEFAULT_MODEL
    prompt_version: str = "1"
    project_type: Optional[ProjectType] = None
    stories_path: Optional[str] = None
    mode: Mode = "fresh"
    known_constraints: list[str] = Field(default_factory=list)
    team_size: Optional[int] = None
    max_concurrent_mappers: int = 20
    max_batch_attempts: int = 3
    monorepo: bool = True

    # Populated at runtime from the YAML file, not serialized in run-config.json
    _stories_meta: Optional[StoriesMeta] = PrivateAttr(default=None)

    @model_validator(mode="after")
    def _check_role_requirements(self) -> "RunConfig":
        if self.role == "dev":
            if self.team_size is None or self.team_size < 1:
                raise ValueError("role='dev' requires team_size >= 1")
            # stories_path and project_type are optional for dev —
            # only needed when building from scratch (no existing DAG)
            if self.stories_path:
                p = Path(self.stories_path)
                if not p.exists():
                    raise ValueError(f"stories_path does not exist: {self.stories_path}")
        if self.role == "pm":
            if not self.stories_path:
                raise ValueError("role='pm' requires stories_path")
            p = Path(self.stories_path)
            if not p.exists():
                raise ValueError(f"stories_path does not exist: {self.stories_path}")
            if not self.project_type:
                raise ValueError("role='pm' requires project_type")
        return self

    @property
    def stories_meta(self) -> StoriesMeta:
        """Lazy-load and cache stories metadata from the YAML file."""
        if self._stories_meta is None:
            self._stories_meta = _validate_stories_file(self.stories_path)
        return self._stories_meta

    @property
    def story_count(self) -> int:
        return self.stories_meta.story_count

    @property
    def feature_groups(self) -> list[FeatureGroup]:
        return [FeatureGroup(**g) for g in self.stories_meta.groups]

    @property
    def releases(self) -> list[dict]:
        return self.stories_meta.releases

    @property
    def story_ids(self) -> list[str]:
        return self.stories_meta.story_ids

    @property
    def foundation_templates(self) -> list[str]:
        """List of foundation template names for this project type."""
        if not self.project_type:
            return []
        return _PROJECT_TYPE_TEMPLATES.get(self.project_type, [])

    @property
    def has_web_frontend(self) -> bool:
        return "web-frontend-foundations" in self.foundation_templates

    @property
    def has_mobile(self) -> bool:
        return "mobile-app-foundations" in self.foundation_templates

    @property
    def has_backend(self) -> bool:
        return "backend-api-foundations" in self.foundation_templates

    @property
    def is_fullstack(self) -> bool:
        return (self.has_web_frontend or self.has_mobile) and self.has_backend

    @classmethod
    def load(cls, path: str | Path) -> "RunConfig":
        with open(path) as f:
            return cls.model_validate(json.load(f))

    def save(self, path: str | Path) -> None:
        with open(path, "w") as f:
            json.dump(self.model_dump(), f, indent=2)


def _validate_stories_file(stories_path: str) -> StoriesMeta:
    """Run validate-stories.py and parse its JSON output."""
    cmd = [sys.executable, str(VALIDATE_SCRIPT), stories_path]
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        raise ValueError(
            f"Stories file validation failed:\n{result.stderr.strip()}"
        )
    return StoriesMeta.model_validate(json.loads(result.stdout))
