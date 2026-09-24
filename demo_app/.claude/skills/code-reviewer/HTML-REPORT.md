# HTML Report Format

The compliance review is rendered as a single self-contained HTML file written to `artifacts/code-reviewer/{slug}-compliance.html`. Tailwind comes from a CDN — no inline styles, no separate stylesheet.

## Scaffold

```html
<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <title>Code Review — {slug}</title>
    <script src="https://cdn.tailwindcss.com"></script>
  </head>
  <body class="bg-stone-50 text-slate-900 font-sans">
    <div class="sticky top-0 z-10 bg-white border-b border-slate-200 shadow-sm px-6 py-3 flex items-center gap-3 flex-wrap">
      <!-- overall verdict badge + per-check badges -->
    </div>
    <main class="max-w-5xl mx-auto px-6 py-10 space-y-6">
      <!-- summary section: header, summary table, and high-priority findings -->
      <!-- one <details> card per check -->
    </main>
  </body>
</html>
```

## Summary Section

Placed inside `<main>` before the per-check cards. It consists of:
- **Header**: Main title (e.g., "Consolidated Code Review & Compliance Report") and a brief subtitle.
- **Summary Table**: Lists each check, its status (`PASS` / `FAIL`), finding counts, and a high-level summary of gaps.
- **High-Priority Findings**: Highlights critical or high-severity issues (e.g., concurrency, security, silent failures) with file links.

Example structure:
```html
<div class="rounded-lg border border-slate-200 bg-white shadow-sm p-6 space-y-6">
  <div>
    <h2 class="text-xl font-bold text-slate-900">Executive Summary</h2>
    <p class="text-xs text-slate-500 mt-1">Overall compliance evaluation status across all active checks.</p>
  </div>
  <table class="w-full text-sm text-left border border-slate-100">
    <!-- table headers and rows of status/counts per check -->
  </table>
  <div class="space-y-3 pt-3 border-t border-slate-100">
    <h3 class="text-sm font-bold text-slate-900 uppercase tracking-wide">High-Priority Issues</h3>
    <!-- list of critical/high issues with file links -->
  </div>
</div>
```



## Sticky summary bar

Stays fixed while scrolling. Contains:

- **Overall verdict badge** — `PASS` = `bg-emerald-100 text-emerald-800`, `WARN` = `bg-amber-100 text-amber-800`, `FAIL` = `bg-red-100 text-red-800`. All badges use `text-xs font-semibold uppercase tracking-wider px-3 py-1 rounded-full`.
- **One badge per check** with its status and finding counts (Critical / High / Medium / Low) in the same colour scheme.
- **Branch name** as a `font-mono text-sm text-slate-500` label pushed to the right.

## Per-check card

Each check is a `<details>` element styled as a card:

```html
<details class="rounded-lg border border-slate-200 bg-white shadow-sm">
  <summary class="flex items-center justify-between px-5 py-4 cursor-pointer select-none">
    <span class="font-medium">{check name}</span>
    <!-- status badge -->
  </summary>
  <!-- findings table -->
</details>
```

- Collapsed by default if `PASS`; add `open` attribute if `WARN` or `FAIL`.

## Findings table

`w-full text-sm border-t border-slate-100`. Columns: **Severity** | **File** | **Line** | **Description**. Rows sorted Critical → High → Medium → Low.

Header row: `text-xs uppercase tracking-wider text-slate-500 bg-slate-50 px-4 py-2`.

Severity cell colours:
- Critical — `text-red-700 font-semibold`
- High — `text-orange-600 font-semibold`
- Medium — `text-amber-600`
- Low — `text-slate-500`

File paths in `font-mono text-xs`. If a check has zero findings:

```html
<td colspan="4" class="px-4 py-3 text-slate-400 text-center">No findings</td>
```

## Notes

- Do not include timestamps — git history is the audit trail.
- No scripts beyond the Tailwind CDN tag. The report is fully static.
