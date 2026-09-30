# Verification asset — frontend stack and browser platform (ticket 14)

Raised by [ticket 14](../issues/14-frontend-architecture.md). Every external fact that ticket 14's
reasoning rests on, checked against a primary source, per the map's verification rule. Cited from the
ticket by section number; **not restated there**.

Checked 27 September 2026. "Primary" means: library source, official docs, official changelog, the npm
registry, a W3C/WHATWG spec, or MDN's `browser-compat-data` raw JSON. Bundle-size aggregators
(Bundlephobia, bundlejs, pkg-size) are **not primary** and any figure taken from them is labelled as
such rather than used to carry an argument.

Verdict vocabulary: **VERIFIED** / **FALSE** / **PARTLY** / **UNVERIFIED**.

---

## §1 — React 19.3 exists, and StrictMode changed in a way that matters here

**VERIFIED.** `react@19.3.0` is the npm `latest`, published 9 September 2026. The map's stack baseline
holds.

Sources: [React 19.3 release post](https://react.dev/blog/2026/09/09/react-19-3),
[CHANGELOG](https://github.com/facebook/react/blob/main/CHANGELOG.md),
`https://registry.npmjs.org/react/latest`.

The load-bearing part is not the version. Effect double-invocation under StrictMode dates to React 18,
but 19.3's changelog adds two entries that extend *when* it happens: effects are now double-invoked
during hydration, matching client-rendered roots (#35961), and **double-invoked after Fast Refresh**
(#35962). A third entry stops double-invoking effects on moved children (#36948).

**Consequence for ticket 14.** Ticket 22's object-URL revoke defect (`FE-RCP L543–547`) was a
mount-time dev-only annoyance when ticket 22 found it. Under 19.3 it also fires on **every Fast
Refresh**, i.e. on every save while a developer is working on the enrolment screen. This raises the
cost of getting the cleanup effect wrong; it does not change what the correct effect looks like.

---

## §2 — Base UI's `CSPProvider` and `disableStyleElements` are real, and cover less than ticket 20 assumed

**VERIFIED as to existence; ticket 20 is PARTLY overstated.**

Source: [Base UI — CSP Provider](https://base-ui.com/react/utils/csp-provider).

Confirmed: `CSPProvider` takes `nonce?: string` and `disableStyleElements?: boolean` (default `false`).
The components that inject a style element are exactly the two ticket 20 named — `ScrollArea.Viewport`,
and `Select.Popup`/`Select.List` when `alignItemWithTrigger` is set — and the injected element exists to
suppress native scrollbars.

Three things ticket 20 did not record:

1. **`disableStyleElements` does not cover inline `<script>`.** The docs state that script tags are
   opt-in per component, are therefore unaffected by the prop, have no disable flag of their own, and
   that a nonce is required if any component uses one. Ticket 20 established we can never mint a
   per-request nonce on a statically served document. So "wrap in `CSPProvider` with
   `disableStyleElements`" closes the `<style>` half and leaves the `<script>` half open by assumption.
   Ticket 14 owes a negative assertion that nothing it mounts emits an inline script.
2. **There is a third escape.** The docs offer unsetting the inline style directly —
   `<ScrollArea.Viewport style={{ overflow: undefined }}>` — alongside relaxing `style-src-attr` and
   rendering the component client-only. Ticket 20's "disabling them is the only available route" is
   therefore slightly overstated.
3. **`style-src-elem` exists** and would police elements without touching attributes. Relevant to
   ticket 14's declined CSP-precision option.

---

## §3 — CSP, the `style` prop, and the CSSOM

**VERIFIED, with one precision correction that matters for §5.**

Sources: [Base UI — CSP Provider](https://base-ui.com/react/utils/csp-provider),
[React `CSSPropertyOperations.js`](https://github.com/facebook/react/blob/main/packages/react-dom-bindings/src/client/CSSPropertyOperations.js),
[CSP Level 3](https://www.w3.org/TR/CSP3/).

Base UI's docs state that `style-src-attr` governs inline style attributes encountered when parsing
server-prerendered HTML and does not affect client-side JavaScript that sets styles. React's source
confirms the client half: `setValueForStyle` assigns through `style.setProperty(...)` or individual
property setters, never `setAttribute('style', ...)`. The same file contains the *server* serializer,
which emits a real `style="…"` attribute during SSR — so a React `style` prop **is** subject to
`style-src-attr` in prerendered HTML and is not on client updates. We do no server rendering, so the
client behaviour is the only one in play. Ticket 20's "Floating UI's dynamic positioning needs no
relaxation" holds.

**The precision correction.** "CSP never applies to the CSSOM" is not literally true. CSP3 gates a set
of CSSOM algorithms — inserting or parsing a rule, a declaration block, a selector group, "all
invocations of CSSOM's various `cssText` setters and `insertRule` methods" — on `'unsafe-eval'`, and
then concedes in its own editorial notes that this needs better explanation and that CSSOM offers no
hooks to implement it. So: individual property mutations are not addressed by the spec at all;
`cssText` and `insertRule` are nominally `'unsafe-eval'`-gated and unenforced in practice. State it
that way rather than flatly. This is the exact API §5 depends on.

---

## §4 — shadcn/ui on Base UI is the default and stable, and ships the components ticket 22 said were missing

**Ticket 22's stack-mismatch finding is FALSE on the OTP half and PARTLY right on the dialog half.**

Sources: [shadcn/ui changelog — Base UI as the default](https://ui.shadcn.com/docs/changelog/2026-07-base-ui-default),
[Input OTP](https://ui.shadcn.com/docs/components/input-otp),
[Dialog](https://ui.shadcn.com/docs/components/dialog),
[Alert Dialog](https://ui.shadcn.com/docs/components/alert-dialog).

Base UI became shadcn/ui's default component library in July 2026 — described as stable, at Base UI
1.6.0. `npx shadcn init` defaults to Base UI; Radix remains supported via `-b radix` and is not
deprecated; a migration skill is published.

- **OTP.** The Base UI distribution ships `InputOTP`, `InputOTPGroup`, `InputOTPSlot` and
  `InputOTPSeparator`, documented as built on top of `input-otp`, with `REGEXP_ONLY_DIGITS` imported
  from that package. Ticket 22's "shadcn-on-Base-UI has no `input-otp`-based OTP primitive in that
  shape" is wrong. Precision: OTP is **not** a Base UI primitive — it is the same `input-otp` wrapper
  in both distributions — so "Base UI's OTP component" is also wrong.
- **Dialog.** Dialog and Alert Dialog both exist on Base UI. But the corpus imports `AlertDialog`
  straight from `@radix-ui/react-alert-dialog`, and the changelog flags prop-shape changes in the move
  (`asChild` becomes `render`) and ships a migration skill precisely because behaviour differs. So
  **re-hosting the dialog is real work.** What is false in ticket 22's sentence is "with zero corpus
  guidance": full Base UI docs and a documented migration path now exist.

---

## §5 — `input-otp` injects a stylesheet via `insertRule`, and fails silently when CSP blocks the element

**VERIFIED, and the failure mode is worse than ticket 14's first draft claimed.**

Sources: [`packages/input-otp/src/input.tsx`](https://github.com/guilhermerodz/input-otp/blob/master/packages/input-otp/src/input.tsx),
`types.ts`, and the published `input-otp@1.5.0` tarball (`dist/index.mjs`).
Version 1.5.0; peer range admits React 19.

In a mount-only effect the component creates a `<style id="input-otp-style">`, sets a `nonce` attribute
if the `nonce` prop was supplied, appends it to `document.head`, and then adds rules through
`sheet.insertRule`, each call wrapped in a helper named `safeInsertRule` that catches and emits
`console.warn('input-otp could not insert CSS rule:', rule)`. The helper's own comment explains the
intent: some engines reject individual cosmetic selectors, and that must not look like an application
error. The behaviour survives minification into the published build.

**Three distinct failure paths, and only one of them warns:**

| What fails | Signal | Result |
|---|---|---|
| One `insertRule` call throws (engine rejects a selector) | `console.warn`, per rule | that one cosmetic rule missing |
| **The `<style>` element is blocked by CSP** | **none — completely silent** | all cosmetic rules missing |
| — | — | functional layout unaffected |

The silent path is the one that matters: if CSP blocks the element, `styleEl.sheet` is `null`, the whole
guarded block is skipped, and `safeInsertRule` is **never reached**, so no warning is emitted.

**Two corrections to ticket 14's first draft follow.** First, an assertion of the form "the console
emits no `input-otp` warning" *passes* in the blocked case and therefore cannot detect the failure it
exists to detect; the assertion has to be positive — the injected sheet exists and carries rules.
Second, "the field renders unstyled" is an overstatement: the injected sheet carries only cosmetic
rules (transparent `::selection`, autofill de-styling, an iOS `-webkit-touch-callout` caret/letter-spacing
fix, and `pointer-events: all` for password-manager badges), while the functional layout comes from
inline `style` objects applied through the CSSOM and unaffected by `style-src`. The degradation is
visual integrity — visible selection highlight over a transparent input, UA autofill colours, iOS
alignment drift, possibly unclickable password-manager badges — not a broken input.

Props, from source rather than README: `nonce?: string`, no default, applied only to the injected style
tag, and usable only on first render since the tag is created once per document.
`noScriptCSSFallback?: string | null`, **defaulting to a populated module constant**, rendered as a
plain `<style>` inside `<noscript>` which **does not receive the nonce**. Passing `null` — not
`undefined` — disables it.

---

## §6 — TanStack Query retries queries three times by default, and it applies to 4xx

**VERIFIED.**

Sources: [Important Defaults](https://tanstack.com/query/v5/docs/framework/react/guides/important-defaults),
[Query Retries](https://tanstack.com/query/v5/docs/framework/react/guides/query-retries),
[`UseMutationOptions`](https://tanstack.com/query/v5/docs/framework/react/reference/interfaces/UseMutationOptions).

Queries default to `retry: 3` with exponential backoff, `staleTime: 0`, `gcTime: 5 minutes`. Mutations
default to `retry: 0`. The retry decision keys off **the query function throwing**, not off an HTTP
status — the docs describe retry purely in terms of the function failing. Nothing exempts 4xx. So a
`fetch` wrapper that throws on `!res.ok` turns every 401 into four requests.

**Note on how ticket 14 may cite this.** It establishes the *mechanism*, not a budget breach. See §13.

---

## §7 — zxcvbn-ts: registry sizes, and the dictionaries are already prefix-compressed

**VERIFIED.** `@zxcvbn-ts/core@4.2.0` (August 2026) — actively maintained.

Primary figures, from the npm registry (`registry.npmjs.org/@zxcvbn-ts/language-common`, `.../language-en`):

| Package | Version | `unpackedSize` | tarball |
|---|---|---|---|
| `@zxcvbn-ts/language-common` | 4.1.3 | 1,891,187 B | 917,764 B |
| `@zxcvbn-ts/language-en` | 4.1.1 | 5,444,769 B | 2,549,523 B |

Earlier minified+gzipped figures of ~221 kB and ~605 kB came from Bundlephobia and are **not primary**;
they are withdrawn and nothing rests on them.

**The dictionaries ship already compressed, unconditionally.** Both packages depend on
`@zxcvbn-ts/dictionary-compression`, and the repo's Rollup JSON plugin compresses every array-valued
wordlist at build time and emits a `decompress(...)` call in its place. The algorithm is **incremental
(prefix) encoding**: each entry stores the shared-prefix length with its immediate predecessor as a
single `A`–`Z` character, capped at 25. Verified in the published artifact — `language-en`'s
`dist/firstnames.json.cjs` opens with a `decompress("Aaaren…")` call, and dist files are measurably
smaller than the `src/` wordlists in the same tarball (e.g. `lastnames` 873,021 → 473,094 B).

Sources: [`scripts/jsonPlugin.mjs`](https://github.com/zxcvbn-ts/zxcvbn/blob/master/scripts/jsonPlugin.mjs),
[`dictionary-compression/src/compress.ts`](https://github.com/zxcvbn-ts/dictionary-compression/blob/main/src/compress.ts),
[lazy-loading guide](https://github.com/zxcvbn-ts/zxcvbn/blob/master/docs/guide/lazy-loading/README.md),
which recommends lazy loading on the grounds that the dictionaries are sizable and gives a concrete
`await import(...)` pattern.

Three caveats worth carrying:

- "No compression win available" is too strong. Prefix encoding is not a trie — entries share prefixes
  only with their immediate predecessor, capped at 25 characters — so a trie or FST would compress
  further. Accurate statement: **the obvious cheap win is already taken.**
- This applies to **4.x only**. The 3.x line declares no compression dependency and ships uncompressed
  dictionaries, so the version must be pinned at 4.x for this to be true.
- Prefix-encoded text still gzips, so transfer savings are not additive with what gzip already finds.
- **UNVERIFIED:** no statement about compression was located on the zxcvbn-ts docs site. The evidence
  is repo build scripts, the helper package, and the published tarball — all primary. Do not cite the
  docs site for this.

---

## §8 — Vite options

Current stable `vite@8.3.1`; Vite 8 is Rolldown-based and `build.rollupOptions` is a deprecated alias
of `build.rolldownOptions`.

- **`%VITE_X%` HTML replacement — VERIFIED as a feature, UNVERIFIED as to version.**
  [Env and Mode](https://vite.dev/guide/env-and-mode) documents HTML constant replacement with
  `%CONST_NAME%`, leaving unknown names untouched. **No introduction version is stated in the docs and
  no changelog entry was found**, so ticket 14's body claim "supported since 4.2" must not be repeated.
- **`build.assetsInlineLimit` — VERIFIED.** Default 4096 bytes; smaller referenced assets are inlined
  as base64 data URIs; `0` disables. Ignored when `build.lib` is set. So ticket 20's `0` is a real
  change from the default, and it is what keeps `data:` out of `img-src` and `font-src`.
- **`build.modulePreload.polyfill` — VERIFIED, and ticket 20's remedy is aimed imprecisely.** Default
  `{ polyfill: true }`; the polyfill is injected into each HTML entry's **proxy module**, i.e. into the
  bundle graph rather than necessarily as an inline `<script>`. Ticket 20 offers
  `modulePreload.polyfill: false` as the fix "if an inline script appears". The real control is the
  check itself — grep the built `index.html` — and the named remedy is speculative.
- **`build.chunkImportMap` — VERIFIED as real, PARTLY verified as to mechanism.**
  [Build options](https://vite.dev/config/build-options) lists it as `boolean`, **default `false`**,
  marked experimental, introduced in Vite 8.1. Ticket 20's constraint "keep it false" is therefore an
  assertion of the default, not a change. The claim that it emits an **inline**
  `<script type="importmap">` is strongly evidenced but not formally confirmed: the original PR's docs
  wording spoke of injecting an importmap, and Vite's HTML plugin carries an `importMapRE` matching
  `<script … type="importmap">` plus a hook gated on the option that relocates it. No doc line or
  emitted-output sample confirms it is inline.
- **`server.headers` / `preview.headers` — VERIFIED.** Both exist, typed `OutgoingHttpHeaders`.

---

## §9 — `qrcode.react` renders two paths, no style attribute, no `data:`

**VERIFIED.** Version 4.2.0; registry `unpackedSize` 114,980 B, tarball 29,099 B (whole package,
including CJS + ESM + types + maps). Peer range admits React 19. Any "6 kB gzipped" figure is a
third-party aggregator estimate and is **not** used here.

Source: [`src/index.tsx`](https://github.com/zpao/qrcode.react/blob/main/src/index.tsx),
`registry.npmjs.org/qrcode.react/latest`.

`QRCodeSVG` renders an `<svg role="img" viewBox=…>` containing an optional `<title>`, then exactly
**two** `<path>` elements — a background rect path filled `bgColor` and a foreground path filled
`fgColor`, both `shapeRendering="crispEdges"`. The author's comment records the strategy: one path for
the dark modules over a light rect, two DOM nodes regardless of QR version. Verified properties:

- **No `<style>` element and no CSSOM injection anywhere in the file.**
- **No library-generated `style` attribute** — colours ride on `fill`. Caveat: `QRCodeSVG` does not
  destructure `style` out of its props, so a caller-supplied `style` passes through to the `<svg>` via
  `{...otherProps}`. Don't pass one.
- **No `data:` URL generated.** `imageSettings.src` is passed through to `href` unmodified.
- **`imageSettings` adds a third node**, an `<image href=…>` — which *would* need an `img-src`
  allowance. Do not pass `imageSettings`. `excavate` changes the foreground path's `d` but not the path
  count.

So a client-rendered SVG QR needs no `img-src` allowance at all — neither `blob:` nor `data:`.

---

## §10 — `Referrer-Policy` via a meta tag

**VERIFIED.** `<meta name="referrer" content="no-referrer">` is spec-supported by the HTML Standard and
marked Baseline widely available since January 2020.

Sources: [MDN `<meta name="referrer">`](https://developer.mozilla.org/en-US/docs/Web/HTML/Reference/Elements/meta/name/referrer),
[MDN `Referrer-Policy`](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Referrer-Policy).

Three behavioural differences from the header, all of which bear on where the tag goes:

- A meta governs only its own document and takes effect as the parser reaches it, so a **late** tag
  leaves earlier subresource requests on the default policy. It belongs early in `<head>`.
- Inserting the tag dynamically makes behaviour unpredictable, per MDN. It must be static markup.
- Where several policies conflict, `no-referrer` wins.

Also worth recording: with no policy set at all the modern default is already
`strict-origin-when-cross-origin`, so the tag narrows rather than rescues.

---

## §11 — `Clear-Site-Data` support is uneven, and the one directive ticket 08 needs most is the weakest

**VERIFIED** from [MDN browser-compat-data raw JSON](https://raw.githubusercontent.com/mdn/browser-compat-data/main/http/headers/Clear-Site-Data.json).
Header itself: Chrome 61, Firefox 63, Safari 17.

| Directive | Chrome | Firefox | Safari | Notes |
|---|---|---|---|---|
| `cookies` | 61 | 63 | 17 | clean, no partial flags |
| `storage` | 61 | 63 | 17 | clean, no partial flags |
| `cache` | **127, still `partial_implementation`** | **138** (present 63–94, then removed) | 17 | Chrome notes two open bugs: stale cache unless the tab is reloaded ([crbug 364634040](https://crbug.com/364634040)) and seconds-long hangs ([crbug 40233601](https://crbug.com/40233601)) |
| `executionContexts` | **never shipped** (`false`) | 63, removed 68 | 17, removed 18.3 | supported by nothing current |
| `clientHints` | 117 | no | no | experimental |
| `*` wildcard | 117, still partial | 63 | 17 | inherits the `cache` caveats |

The file also carries a `secure_context_required` sub-feature (Chrome 61 / Firefox 63 / Safari 17), so
the header is ignored on insecure origins in all three engines. `localhost` is a potentially trustworthy
origin under Secure Contexts and should therefore qualify, but **I found no primary source confirming
`Clear-Site-Data` specifically on `http://localhost`, and did not test a browser — UNVERIFIED.** Ticket
08 records it as observable in dev; treat that as untested rather than wrong. The spec separately
requires the header be ignored on responses served by a service worker.

**Handback to ticket 08.** It prescribes `"cache","cookies","storage"`. `cookies` and `storage` are
clean everywhere; **`cache` is the one carrying a partial implementation in current Chrome with a
documented seconds-long-hang bug, plus a 44-version Firefox hole.** Since ticket 08 already concluded
the SPA's own unconditional clearing is the real control, `cache` is the directive buying least and
risking most.

---

## §12 — `fetch` sends `Accept: */*` when none is supplied

**VERIFIED.** The [Fetch Standard](https://fetch.spec.whatwg.org/) sets `Accept` to `*/*` when the
header list does not contain one, except for specific request destinations (document, image, style,
text, json) — none of which a plain `fetch()` call matches.

**Consequence.** Sending nothing yields `*/*`, which is permissive enough to defeat ticket 06's 406
worry but may also satisfy a framework matcher looking for an HTML-ish `Accept` (ticket 23 §2's
browser-request matcher finding). An explicit `Accept: application/json, application/problem+json`
avoids both without depending on how that matcher treats `*/*` — a Spring Security 7.1.x source fact
this asset does **not** verify, and does not need to.

---

## §13 — What this asset changed, and what it does not support

**Reversals.** §4 overturns ticket 22's stack-mismatch finding on the OTP half and narrows it on the
dialog half. §5 makes the `input-otp` CSP interaction silent rather than warned, which invalidates a
console-quietness assertion. §7 withdraws two size figures and establishes that the cheap compression
win is already taken. §8 withdraws ticket 14's "since Vite 4.2" claim and downgrades ticket 20's
inline-script remedy to speculative. §11 identifies `cache` as the weak directive in ticket 08's header.
§1 escalates ticket 22's revoke defect from mount-time to every Fast Refresh.

**A claim this asset does NOT support.** §6 establishes only that the library retries three times and
that the retry fires on 4xx. It does **not** establish that this breaches any recorded budget or sizing:
ticket 09's ten-row table has no row for `GET /api/profile`, and ticket 21's disk sizing is symbolic
(`90 × daily`, `daily × lead_days`) with no bytes-per-event or events-per-day constant anywhere. Any
claim that a retry policy is *forced* by tickets 09 or 21 is unsupported and must not be recorded.

**Still unverified, and flagged as such wherever cited.** The Vite `%VITE_X%` introduction version (§8);
whether `build.chunkImportMap` emits an inline importmap (§8); `Clear-Site-Data` on `http://localhost`
(§11); a docs-site statement on zxcvbn dictionary compression (§7); and how Spring Security 7.1.x's
entry-point matcher treats `*/*` (§12, deliberately routed around rather than resolved).
