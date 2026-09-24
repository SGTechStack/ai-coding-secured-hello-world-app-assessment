# HTML Report Format

The Spec compliance report is a single self-contained HTML file written to `artifacts/spec-compliance/spec-compliance-{spec-slug}-{YYYY-MM-DD-HHmm}.html`. Tailwind comes from a CDN — no inline styles, no separate stylesheet.

## Scaffold

```html
<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <title>Spec Compliance: {spec-title} — {YYYY-MM-DD HH:mm}</title>
    <script src="https://cdn.tailwindcss.com"></script>
  </head>
  <body class="bg-stone-50 text-slate-900 font-sans">
    <div class="sticky top-0 z-10 bg-white border-b border-slate-200 shadow-sm px-6 py-3 flex items-center gap-3 flex-wrap">
      <!-- overall verdict badge -->
    </div>
    <main class="max-w-5xl mx-auto px-6 py-10 space-y-6">
      <!-- Status summary count -->
      <!-- FAILs summary block (omit if no FAILs) -->
      <!-- AUTO-FIXs summary block (omit if no AUTO-FIXs) -->
      <!-- Policy audit details card -->
    </main>
  </body>
</html>
```

## Sticky summary bar

Stays fixed while scrolling. Contains:

- **Overall verdict badge** — `PASS` = `bg-emerald-100 text-emerald-800`, `FAIL` = `bg-red-100 text-red-800`. All badges use `text-xs font-semibold uppercase tracking-wider px-3 py-1 rounded-full`.
- **Policy Section Badge** (`Policy (IM8 / ARC)`) with its verdict status in the same colour scheme.
- **Timestamp** as a `font-mono text-sm text-slate-500` label pushed to the right.

## Status summary count

Rendered at the top of `<main>` as colored pill chips, one per verdict category, before the FAILs and AUTO-FIXs blocks:

```html
<div class="flex flex-wrap gap-3">
  <div class="rounded-full bg-emerald-50 border border-emerald-200 px-4 py-1 text-sm font-medium text-emerald-800">✓ PASS — {pass-count}</div>
  <div class="rounded-full bg-blue-50 border border-blue-200 px-4 py-1 text-sm font-medium text-blue-700">⟳ AUTO-FIX — {auto-fix-count}</div>
  <div class="rounded-full bg-red-50 border border-red-200 px-4 py-1 text-sm font-medium text-red-700">✗ FAIL — {fail-count}</div>
  <div class="rounded-full bg-amber-50 border border-amber-200 px-4 py-1 text-sm font-medium text-amber-700">⏱ DEFERRED — {deferred-count}</div>
  <div class="rounded-full bg-slate-50 border border-slate-200 px-4 py-1 text-sm font-medium text-slate-400">— N/A — {na-count} ({na-arc-count} ARC + {na-im8-count} IM8)</div>
</div>
```

## FAILs summary block

Rendered at the top of `<main>` only when at least one FAIL exists:

```html
<div class="rounded-lg border border-red-200 bg-red-50 px-6 py-5">
  <h2 class="text-sm font-semibold text-red-700 uppercase tracking-wider mb-3">Must resolve before implementation begins</h2>
  <ul class="list-disc list-inside space-y-1 text-sm text-slate-700">
    <li><span class="font-medium">Policy:</span> {control} — {reason}</li>
  </ul>
</div>
```

Omit this block entirely if there are no FAILs.

## AUTO-FIXs summary block

Rendered below the FAILs block (or directly at the top of `<main>` if no FAILs) only when at least one AUTO-FIX exists:

```html
<div class="rounded-lg border border-blue-200 bg-blue-50 px-6 py-5">
  <h2 class="text-sm font-semibold text-blue-700 uppercase tracking-wider mb-3">Auto-fix items (clarify details or tighten wording)</h2>
  <ul class="list-disc list-inside space-y-1 text-sm text-slate-700">
    <li><span class="font-medium">Policy:</span> {control} — {reason}</li>
  </ul>
</div>
```

Omit this block entirely if there are no AUTO-FIXs.

## Policy audit details card

The Policy audit results section is a `<details>` element styled as a card:

```html
<details class="rounded-lg border border-slate-200 bg-white shadow-sm">
  <summary class="flex items-center justify-between px-5 py-4 cursor-pointer select-none">
    <span class="font-medium">Policy (IM8 / ARC)</span>
    <!-- status badge -->
  </summary>
  <!-- verdict table -->
</details>
```

- Collapsed by default if no FAILs; add `open` attribute if any FAIL is present.

## Verdict table

`w-full text-sm border-t border-slate-100`. Columns: **Control** | **Verdict** | **Reason**.

Header row: `text-xs uppercase tracking-wider text-slate-500 bg-slate-50 px-4 py-2`.

Verdict cell colours:
- PASS — `text-emerald-700 font-semibold`
- AUTO-FIX — `text-blue-600 font-semibold`
- FAIL — `text-red-700 font-semibold`
- DEFERRED — `text-amber-600 font-semibold`
- N/A — `text-slate-400`

## Notes

- Do not include timestamps in the sticky bar content — the filename encodes the run time.
- No scripts beyond the Tailwind CDN tag. The report is fully static.
- Re-running is safe — a fresh file is written each time.
