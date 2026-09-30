## Findings

| Finding | Required action | Status |
| --- | --- | --- |
| KB unavailable. Reviewer finding. The stated Node floor is below what the frontend toolchain actually accepts, and the line is new in this change, so criterion 3 ('following the README from a clean checkout starts both applications in dev') rests on it. Installed engines ranges: vitest 5.0.2 declares node '^22.12.0 \|\| ^24.0.0 \|\| >=26.0.0' -- Node 20 is outside it entirely, so the README's own `cd frontend && npm test` command is unsupported on the version the README tells an operator is enough; vite 8.3.1, oxlint 1.86.0 and @vitejs/plugin-react 6.1.1 declare '^20.19.0 \|\| >=22.12.0', so Node 20.0-20.18 and all of 21.x are outside every tool's range, and 23.x/25.x are outside vitest's. The implementer's clean-checkout verification ran on this machine's Node 26.10.0, which satisfies every range, so the floor was never exercised.<br><br><details><summary><strong>Verify in</strong></summary><br><code>README.md, Requirements section (new line: &#x27;- **Node.js 20 or later** and npm.&#x27;)</code></details> | Raise the stated requirement to what the toolchain declares -- e.g. '**Node.js 22.12 or later** (an even-numbered LTS: 22.12+, 24 or 26; the toolchain does not support Node 20 or the odd-numbered releases)' -- or, if Node 20 support is intended, pin a vitest version whose engines range includes it. One-line documentation fix; no code change. | Resolved |


## Full do-work log

Open locally: `artifacts/do-work/16-readme-deployment-notes.html`

Includes reviewer findings and human decisions.
