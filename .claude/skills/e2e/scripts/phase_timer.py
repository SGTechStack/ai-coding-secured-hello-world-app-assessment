#!/usr/bin/env python3
"""Record Story-E2E phase boundaries without manual JSON editing."""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

PHASES = ("analyze", "discovery", "author", "preflight", "freeze", "run", "report", "review", "teardown")


def now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def load(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {"version": 1, "phases": []}
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise SystemExit(f"{path}: invalid timing JSON: {error}") from error
    if data.get("version") != 1 or not isinstance(data.get("phases"), list):
        raise SystemExit(f"{path}: expected {{\"version\": 1, \"phases\": [...]}}")
    return data


def write(path: Path, data: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    markdown = path.with_suffix(".md")
    rows = ["# Phase timings", "", "| Phase | Start (UTC) | End (UTC) | Attempt | Cause |", "| --- | --- | --- | --- | --- |"]
    for phase in data["phases"]:
        rows.append(
            "| {name} | {started_at} | {ended_at} | {attempt} | {cause} |".format(
                name=phase["name"],
                started_at=phase["started_at"],
                ended_at=phase.get("ended_at", "in progress"),
                attempt=phase.get("attempt", 1),
                cause=phase.get("cause", ""),
            )
        )
    markdown.write_text("\n".join(rows) + "\n", encoding="utf-8")


def close_open(data: dict[str, Any]) -> None:
    for phase in reversed(data["phases"]):
        if "ended_at" not in phase:
            phase["ended_at"] = now()
            return


def open_phase(data: dict[str, Any], name: str, attempt: int, cause: str | None) -> None:
    phase: dict[str, Any] = {"name": name, "started_at": now(), "attempt": attempt}
    if cause:
        phase["cause"] = cause
    data["phases"].append(phase)


def phase_args(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--file", required=True, type=Path, help="campaign phase-timings.json path")
    parser.add_argument("--phase", required=True, choices=PHASES, help="canonical Story-E2E phase")
    parser.add_argument("--attempt", type=int, default=1)
    parser.add_argument("--cause", help="short reason for a retry or recovery")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="action", required=True)
    start = commands.add_parser("start", help="start the first phase")
    phase_args(start)
    next_phase = commands.add_parser("next", help="finish the current phase and start another")
    phase_args(next_phase)
    run = commands.add_parser("run", help="time one command as a phase")
    phase_args(run)
    run.add_argument("command", nargs=argparse.REMAINDER, help="command after --")
    finish = commands.add_parser("finish", help="finish the current phase")
    finish.add_argument("--file", required=True, type=Path)
    args = parser.parse_args()

    data = load(args.file)
    if args.action == "finish":
        close_open(data)
        write(args.file, data)
        return 0

    if args.attempt < 1:
        raise SystemExit("--attempt must be at least 1")
    if args.action == "run" and not args.command:
        raise SystemExit("run requires a command after --")
    close_open(data)
    open_phase(data, args.phase, args.attempt, args.cause)
    write(args.file, data)
    if args.action != "run":
        return 0
    command = args.command[1:] if args.command[0] == "--" else args.command
    try:
        return subprocess.run(command, check=False).returncode
    finally:
        data = load(args.file)
        close_open(data)
        write(args.file, data)


if __name__ == "__main__":
    raise SystemExit(main())
