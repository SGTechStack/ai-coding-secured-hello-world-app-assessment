# Browser storage key and custom event naming conventions

Storage keys use a `demo:` namespace prefix with colon separator and lowercase-hyphenated suffixes (e.g. `demo:theme`, `demo:return-to`). Custom events on `window` use an unprefixed `noun:verb` format (e.g. `session:expired`).

## Storage keys: `demo:<lowercase-hyphenated>`

localStorage and sessionStorage keys are scoped with `demo:` because all same-origin code (including third-party libraries) shares the same storage object. The colon separator is the conventional namespace delimiter for storage keys (Redis, browser extension storage). All-lowercase-hyphenated suffixes avoid camelCase ambiguity.

## Custom events: unprefixed `noun:verb`

Custom events dispatched on `window` do not need an app prefix — they are scoped to the JS runtime and there is no collision risk with other applications. The `noun:verb` idiom reads as a domain event (e.g. `session:expired`) and is visually distinct from storage keys.

## Considered options

**Unified convention for both** — e.g. `demo:session:expired`. Rejected because it erases a meaningful distinction: storage keys are data at rest that outlive the page; events are ephemeral in-memory signals. The different formats communicate that difference.

**Dot separator for storage** (`demo.return-to`) — already in use for `demo.returnTo`. Replaced because dots read as object-path notation in many contexts and colons are the established storage namespace delimiter.

**camelCase suffix for storage** (`demo:returnTo`) — replaced with hyphenated lowercase to match URL/CSS conventions and avoid ambiguity about word boundaries.

## Consequences

Any new localStorage or sessionStorage key must be defined as a named constant in `src/lib/` (not inline) and follow `demo:<lowercase-hyphenated>`. Any new custom event dispatched on `window` must follow `noun:verb` with no prefix.