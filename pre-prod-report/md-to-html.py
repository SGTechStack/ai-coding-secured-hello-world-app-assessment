#!/usr/bin/env python3
"""Minimal Markdown -> HTML converter (stdlib only).

Supports: headings, tables, fenced code blocks (incl. mermaid),
bold/italic/inline-code, links, unordered/ordered lists, paragraphs.
Not a full CommonMark implementation -- sufficient for structured
technical documents produced by this skill.
"""
import html
import re
import sys


def inline(text: str) -> str:
    text = html.escape(text, quote=False)
    text = re.sub(r"`([^`]+)`", r"<code>\1</code>", text)
    text = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", text)
    text = re.sub(r"(?<!\*)\*([^*]+)\*(?!\*)", r"<em>\1</em>", text)
    text = re.sub(r"\[([^\]]+)\]\(([^)]+)\)", r'<a href="\2">\1</a>', text)
    return text


def convert(md: str) -> str:
    lines = md.split("\n")
    out = []
    i = 0
    n = len(lines)
    in_list = None  # 'ul' | 'ol' | None

    def close_list():
        nonlocal in_list
        if in_list:
            out.append(f"</{in_list}>")
            in_list = None

    while i < n:
        line = lines[i]

        # Fenced code block
        m = re.match(r"^```(\w*)\s*$", line)
        if m:
            lang = m.group(1)
            close_list()
            code_lines = []
            i += 1
            while i < n and not re.match(r"^```\s*$", lines[i]):
                code_lines.append(lines[i])
                i += 1
            i += 1  # skip closing ```
            code = "\n".join(code_lines)
            cls = f' class="language-{html.escape(lang)}"' if lang else ""
            if lang == "mermaid":
                out.append(f'<pre class="mermaid">{html.escape(code)}</pre>')
            else:
                out.append(f"<pre><code{cls}>{html.escape(code)}</code></pre>")
            continue

        # Blockquote (used for metadata header lines like "> Generated: ...")
        m = re.match(r"^>\s?(.*)$", line)
        if m:
            close_list()
            out.append(f"<blockquote>{inline(m.group(1))}</blockquote>")
            i += 1
            continue

        # Headings
        m = re.match(r"^(#{1,6})\s+(.*)$", line)
        if m:
            close_list()
            level = len(m.group(1))
            out.append(f"<h{level}>{inline(m.group(2))}</h{level}>")
            i += 1
            continue

        # Table (header + separator row)
        if "|" in line and i + 1 < n and re.match(r"^\s*\|?[\s:|-]+\|?\s*$", lines[i + 1]) and "-" in lines[i + 1]:
            close_list()
            header_cells = [c.strip() for c in line.strip().strip("|").split("|")]
            out.append("<table><thead><tr>" + "".join(f"<th>{inline(c)}</th>" for c in header_cells) + "</tr></thead><tbody>")
            i += 2
            while i < n and "|" in lines[i] and lines[i].strip():
                row_cells = [c.strip() for c in lines[i].strip().strip("|").split("|")]
                out.append("<tr>" + "".join(f"<td>{inline(c)}</td>" for c in row_cells) + "</tr>")
                i += 1
            out.append("</tbody></table>")
            continue

        # Unordered list
        m = re.match(r"^[-*]\s+(.*)$", line)
        if m:
            if in_list != "ul":
                close_list()
                out.append("<ul>")
                in_list = "ul"
            out.append(f"<li>{inline(m.group(1))}</li>")
            i += 1
            continue

        # Ordered list
        m = re.match(r"^\d+\.\s+(.*)$", line)
        if m:
            if in_list != "ol":
                close_list()
                out.append("<ol>")
                in_list = "ol"
            out.append(f"<li>{inline(m.group(1))}</li>")
            i += 1
            continue

        # Blank line
        if not line.strip():
            close_list()
            i += 1
            continue

        # Paragraph
        close_list()
        out.append(f"<p>{inline(line)}</p>")
        i += 1

    close_list()
    return "\n".join(out)


TEMPLATE = """<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>{title}</title>
<script src="https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js"></script>
<style>
body {{ font-family: -apple-system, Segoe UI, Helvetica, Arial, sans-serif; max-width: 1000px; margin: 2rem auto; padding: 0 1.5rem; line-height: 1.55; color: #1a1a1a; }}
h1, h2, h3, h4 {{ border-bottom: 1px solid #ddd; padding-bottom: 0.3rem; margin-top: 2rem; }}
table {{ border-collapse: collapse; width: 100%; margin: 1rem 0; font-size: 0.92rem; }}
th, td {{ border: 1px solid #ccc; padding: 0.4rem 0.6rem; text-align: left; vertical-align: top; }}
th {{ background: #f2f2f2; }}
code {{ background: #f5f5f5; padding: 0.15rem 0.35rem; border-radius: 3px; font-size: 0.9em; }}
pre {{ background: #f5f5f5; padding: 1rem; overflow-x: auto; border-radius: 4px; }}
pre code {{ background: none; padding: 0; }}
blockquote {{ color: #555; border-left: 3px solid #ccc; margin: 0.3rem 0; padding-left: 0.8rem; font-size: 0.9rem; }}
pre.mermaid {{ background: #fff; border: 1px solid #ddd; }}
</style>
</head>
<body>
{body}
<script>mermaid.initialize({{ startOnLoad: true }});</script>
</body>
</html>
"""


def main():
    if len(sys.argv) != 3:
        print("usage: md-to-html.py <input.md> <output.html>", file=sys.stderr)
        sys.exit(1)
    src, dst = sys.argv[1], sys.argv[2]
    with open(src, "r", encoding="utf-8") as f:
        md = f.read()
    title_match = re.search(r"^#\s+(.*)$", md, re.MULTILINE)
    title = title_match.group(1) if title_match else src
    body_html = convert(md)
    with open(dst, "w", encoding="utf-8") as f:
        f.write(TEMPLATE.format(title=html.escape(title), body=body_html))
    print(f"Wrote {dst}")


if __name__ == "__main__":
    main()
