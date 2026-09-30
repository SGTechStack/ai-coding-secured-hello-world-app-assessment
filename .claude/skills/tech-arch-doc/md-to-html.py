#!/usr/bin/env python3
"""
md-to-html.py — Deterministic Markdown-to-HTML converter for tech architecture docs
====================================================================================

Converts a Markdown source file into a self-contained, styled HTML file.
Uses only Python standard library — no external dependencies.

Same input always produces the same output (no timestamps injected by this script;
timestamps come from the Markdown source).

Usage:
  python3 md-to-html.py <input.md> <output.html>
"""

from __future__ import annotations

import html
import re
import sys
from pathlib import Path


# ── Markdown parsing ────────────────────────────────────────────────────────

def _escape(text: str) -> str:
    """HTML-escape text content."""
    return html.escape(text, quote=True)


def _inline(text: str) -> str:
    """Process inline Markdown: bold, italic, code, links."""
    t = _escape(text)
    # code spans (backtick) — process first to avoid conflicts
    t = re.sub(r'`([^`]+)`', r'<code>\1</code>', t)
    # bold + italic
    t = re.sub(r'\*\*\*(.+?)\*\*\*', r'<strong><em>\1</em></strong>', t)
    # bold
    t = re.sub(r'\*\*(.+?)\*\*', r'<strong>\1</strong>', t)
    # italic
    t = re.sub(r'\*(.+?)\*', r'<em>\1</em>', t)
    # links [text](url)
    t = re.sub(r'\[([^\]]+)\]\(([^)]+)\)', r'<a href="\2">\1</a>', t)
    return t


def _parse_table(lines: list[str]) -> str:
    """Parse a Markdown table (lines include header, separator, and data rows)."""
    if len(lines) < 2:
        return ""

    def split_row(line: str) -> list[str]:
        line = line.strip()
        if line.startswith("|"):
            line = line[1:]
        if line.endswith("|"):
            line = line[:-1]
        return [cell.strip() for cell in line.split("|")]

    headers = split_row(lines[0])
    # lines[1] is the separator row — skip it
    rows = [split_row(line) for line in lines[2:]]

    out = ['<div class="table-wrap"><table>']
    out.append("<thead><tr>")
    for h in headers:
        out.append(f"<th>{_inline(h)}</th>")
    out.append("</tr></thead>")
    out.append("<tbody>")
    for row in rows:
        out.append("<tr>")
        for i, cell in enumerate(row):
            # Pad row if fewer cells than headers
            out.append(f"<td>{_inline(cell)}</td>")
        # Fill missing cells
        for _ in range(len(headers) - len(row)):
            out.append("<td></td>")
        out.append("</tr>")
    out.append("</tbody></table></div>")
    return "\n".join(out)


def _is_table_sep(line: str) -> bool:
    """Check if a line is a Markdown table separator (|---|---|)."""
    stripped = line.strip()
    if not stripped.startswith("|"):
        return False
    return bool(re.match(r'^[\s|:\-]+$', stripped))


def convert(md_text: str) -> str:
    """Convert Markdown text to HTML body content."""
    lines = md_text.split("\n")
    out: list[str] = []
    i = 0
    in_list = False
    in_code_block = False
    code_block_lines: list[str] = []
    code_lang = ""

    while i < len(lines):
        line = lines[i]

        # Fenced code blocks
        if line.strip().startswith("```"):
            if not in_code_block:
                in_code_block = True
                code_lang = line.strip()[3:].strip()
                code_block_lines = []
                i += 1
                continue
            else:
                in_code_block = False
                if code_lang == "mermaid":
                    # Mermaid blocks: emit as <pre class="mermaid"> for client-side rendering
                    mermaid_content = "\n".join(code_block_lines)
                    out.append(f'<pre class="mermaid">{mermaid_content}</pre>')
                else:
                    lang_attr = f' class="language-{_escape(code_lang)}"' if code_lang else ""
                    code_content = _escape("\n".join(code_block_lines))
                    out.append(f'<pre><code{lang_attr}>{code_content}</code></pre>')
                i += 1
                continue

        if in_code_block:
            code_block_lines.append(line)
            i += 1
            continue

        # Close open list if non-list line
        if in_list and not re.match(r'^(\s*[-*+]|\s*\d+\.)\s', line) and line.strip():
            out.append("</ul>")
            in_list = False

        # Blank line
        if not line.strip():
            if in_list:
                out.append("</ul>")
                in_list = False
            i += 1
            continue

        # Headings
        heading_match = re.match(r'^(#{1,6})\s+(.+)$', line)
        if heading_match:
            level = len(heading_match.group(1))
            text = heading_match.group(2)
            slug = re.sub(r'[^a-z0-9]+', '-', text.lower()).strip('-')
            out.append(f'<h{level} id="{slug}">{_inline(text)}</h{level}>')
            i += 1
            continue

        # Blockquotes
        if line.strip().startswith(">"):
            bq_lines = []
            while i < len(lines) and lines[i].strip().startswith(">"):
                bq_lines.append(re.sub(r'^>\s?', '', lines[i]))
                i += 1
            out.append(f'<blockquote><p>{_inline(" ".join(l.strip() for l in bq_lines))}</p></blockquote>')
            continue

        # Tables — detect header + separator pattern
        if (
            "|" in line
            and i + 1 < len(lines)
            and _is_table_sep(lines[i + 1])
        ):
            table_lines = [line]
            i += 1
            while i < len(lines) and ("|" in lines[i] or _is_table_sep(lines[i])):
                table_lines.append(lines[i])
                i += 1
            out.append(_parse_table(table_lines))
            continue

        # Unordered list items
        list_match = re.match(r'^(\s*)[-*+]\s+(.+)$', line)
        if list_match:
            if not in_list:
                out.append("<ul>")
                in_list = True
            out.append(f"<li>{_inline(list_match.group(2))}</li>")
            i += 1
            continue

        # Ordered list items
        ol_match = re.match(r'^(\s*)\d+\.\s+(.+)$', line)
        if ol_match:
            if not in_list:
                out.append("<ul>")
                in_list = True
            out.append(f"<li>{_inline(ol_match.group(2))}</li>")
            i += 1
            continue

        # Horizontal rule
        if re.match(r'^[-*_]{3,}\s*$', line):
            out.append("<hr>")
            i += 1
            continue

        # Paragraph
        para_lines = []
        while i < len(lines) and lines[i].strip() and not re.match(r'^#{1,6}\s', lines[i]) and not lines[i].strip().startswith(">") and not re.match(r'^[-*+]\s', lines[i]) and not re.match(r'^\d+\.\s', lines[i]) and not lines[i].strip().startswith("```") and not ("|" in lines[i] and i + 1 < len(lines) and _is_table_sep(lines[i + 1])):
            para_lines.append(lines[i])
            i += 1
        if para_lines:
            out.append(f"<p>{_inline(' '.join(l.strip() for l in para_lines))}</p>")
            continue

        i += 1

    if in_list:
        out.append("</ul>")

    return "\n".join(out)


# ── HTML template ───────────────────────────────────────────────────────────

CSS = """
:root {
  --bg: #ffffff;
  --fg: #1a1a2e;
  --muted: #6b7280;
  --border: #e5e7eb;
  --accent: #2563eb;
  --accent-light: #dbeafe;
  --surface: #f9fafb;
  --code-bg: #f3f4f6;
  --severity-critical: #dc2626;
  --severity-high: #ea580c;
  --severity-medium: #d97706;
  --severity-low: #65a30d;
}

@media (prefers-color-scheme: dark) {
  :root {
    --bg: #0f172a;
    --fg: #e2e8f0;
    --muted: #94a3b8;
    --border: #334155;
    --accent: #60a5fa;
    --accent-light: #1e3a5f;
    --surface: #1e293b;
    --code-bg: #1e293b;
  }
}

* { margin: 0; padding: 0; box-sizing: border-box; }

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
  line-height: 1.7;
  color: var(--fg);
  background: var(--bg);
  max-width: 960px;
  margin: 0 auto;
  padding: 2rem 1.5rem;
}

h1 {
  font-size: 2rem;
  font-weight: 700;
  margin-bottom: 0.5rem;
  padding-bottom: 0.75rem;
  border-bottom: 3px solid var(--accent);
}

h2 {
  font-size: 1.5rem;
  font-weight: 600;
  margin-top: 2.5rem;
  margin-bottom: 1rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid var(--border);
  color: var(--accent);
}

h3 {
  font-size: 1.2rem;
  font-weight: 600;
  margin-top: 1.75rem;
  margin-bottom: 0.75rem;
}

h4, h5, h6 {
  font-size: 1.05rem;
  font-weight: 600;
  margin-top: 1.25rem;
  margin-bottom: 0.5rem;
}

p { margin-bottom: 1rem; }

blockquote {
  border-left: 4px solid var(--accent);
  background: var(--surface);
  padding: 0.75rem 1rem;
  margin: 1rem 0;
  border-radius: 0 6px 6px 0;
  color: var(--muted);
  font-size: 0.95rem;
}

blockquote p { margin-bottom: 0; }

a {
  color: var(--accent);
  text-decoration: none;
}

a:hover { text-decoration: underline; }

code {
  font-family: 'SF Mono', 'Fira Code', 'Cascadia Code', 'Consolas', monospace;
  font-size: 0.875em;
  background: var(--code-bg);
  padding: 0.15em 0.4em;
  border-radius: 4px;
}

pre {
  background: var(--code-bg);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 1rem;
  overflow-x: auto;
  margin: 1rem 0;
}

pre code {
  background: none;
  padding: 0;
  font-size: 0.85rem;
  line-height: 1.5;
}

.table-wrap {
  overflow-x: auto;
  margin: 1rem 0;
  border-radius: 8px;
  border: 1px solid var(--border);
}

table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}

thead {
  background: var(--surface);
}

th {
  text-align: left;
  padding: 0.75rem 1rem;
  font-weight: 600;
  border-bottom: 2px solid var(--border);
  white-space: nowrap;
}

td {
  padding: 0.6rem 1rem;
  border-bottom: 1px solid var(--border);
  vertical-align: top;
}

tbody tr:last-child td { border-bottom: none; }

tbody tr:hover { background: var(--surface); }

ul, ol {
  margin: 0.5rem 0 1rem 1.5rem;
}

li {
  margin-bottom: 0.35rem;
}

hr {
  border: none;
  border-top: 1px solid var(--border);
  margin: 2rem 0;
}

/* Severity badges in vulnerability tables */
td:first-child {
  font-weight: 600;
  text-transform: uppercase;
  font-size: 0.8rem;
  letter-spacing: 0.03em;
}

/* Mermaid diagrams */
.mermaid {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 1.5rem;
  margin: 1rem 0;
  text-align: center;
  overflow-x: auto;
}

/* Print styles */
@media print {
  body { max-width: none; padding: 1cm; }
  h2 { page-break-before: always; }
  h2:first-of-type { page-break-before: auto; }
  table { font-size: 0.8rem; }
  pre { white-space: pre-wrap; word-break: break-all; }
}
"""


MERMAID_VERSION = "11.4.1"


def build_html(title: str, body: str, has_mermaid: bool) -> str:
    """Wrap converted body in a full HTML document."""
    mermaid_script = ""
    if has_mermaid:
        mermaid_script = f"""
<script type="module">
import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@{MERMAID_VERSION}/dist/mermaid.esm.min.mjs';
mermaid.initialize({{ startOnLoad: true, theme: 'default', securityLevel: 'strict' }});
</script>"""

    return f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{_escape(title)}</title>
<style>
{CSS}
</style>
</head>
<body>
{body}{mermaid_script}
</body>
</html>"""


# ── CLI entry point ─────────────────────────────────────────────────────────

def main() -> None:
    if len(sys.argv) != 3:
        print("Usage: python3 md-to-html.py <input.md> <output.html>", file=sys.stderr)
        sys.exit(1)

    input_path = Path(sys.argv[1])
    output_path = Path(sys.argv[2])

    if not input_path.exists():
        print(f"Error: input file not found: {input_path}", file=sys.stderr)
        sys.exit(1)

    md_text = input_path.read_text(encoding="utf-8")

    # Extract title from first H1, or use filename
    title_match = re.search(r'^#\s+(.+)$', md_text, re.MULTILINE)
    title = title_match.group(1) if title_match else input_path.stem

    body = convert(md_text)
    has_mermaid = "```mermaid" in md_text
    html_doc = build_html(title, body, has_mermaid)

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(html_doc, encoding="utf-8")
    print(f"Wrote {output_path} ({len(html_doc):,} bytes)")


if __name__ == "__main__":
    main()
