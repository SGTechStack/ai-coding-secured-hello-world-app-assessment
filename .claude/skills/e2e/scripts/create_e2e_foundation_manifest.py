"""Create a portable manifest for shared E2E files that story runs must not modify."""
from __future__ import annotations
import argparse
import hashlib
import json
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("manifest", type=Path)
parser.add_argument("files", nargs="+", type=Path, help="files to protect; paths are stored relative to the manifest")
args = parser.parse_args()
root = args.manifest.parent.resolve()
entries = []
for source in args.files:
    source = source.resolve()
    if not source.is_file():
        raise SystemExit(f"missing file: {source}")
    try:
        relative = source.relative_to(root).as_posix()
    except ValueError:
        raise SystemExit(f"{source} is outside manifest directory {root}")
    entries.append({"path": relative, "sha256": hashlib.sha256(source.read_bytes()).hexdigest()})
args.manifest.write_text(json.dumps({"version": 1, "protected_files": entries}, indent=2) + "\n", encoding="utf-8")
print(f"Wrote {args.manifest} with {len(entries)} protected file(s).")
