# depcruise

## Rule object shape

### `forbidden` rule
```javascript
{
  name: "kebab-case-name",             // required; unique within the config
  comment: "One sentence intent.",     // required; plain string, no markdown
  severity: "error" | "warn" | "info" | "ignore",
  from: { /* source matchers */ },
  to:   { /* target matchers */ },
}
```

### `allowed` rule (no name/severity — violations use `allowedSeverity` at top level)
```javascript
{
  comment: "Why this import is permitted.",
  from: { /* source matchers */ },
  to:   { /* target matchers */ },
}
```

### `required` rule (module must import the target)
```javascript
{
  name: "kebab-case-name",
  comment: "One sentence intent.",
  severity: "error" | "warn",
  module: { /* which modules must satisfy the rule */ },
  to:     { /* what they must import */ },
}
```

---

## All matchers (from / to / module)

| Field | Type | What it matches |
|---|---|---|
| `path` | `string \| string[]` | Regex the file path must match. Forward-slashes only. Anchored `^` preferred. |
| `pathNot` | `string \| string[]` | Regex the file path must NOT match. |
| `dependencyTypes` | `string[]` | Import kind must be one of the listed types (see below). |
| `dependencyTypesNot` | `string[]` | Import kind must NOT be one of the listed types. |
| `circular` | `boolean` | `true` → this dependency is part of a cycle. |
| `orphan` | `boolean` | `true` → module has no dependents AND no dependencies (use in `from`). |
| `couldNotResolve` | `boolean` | `true` → import target could not be resolved. |
| `dynamic` | `boolean` | `true` → dependency is a dynamic `import()`. |
| `exoticallyRequired` | `boolean` | `true` → required via non-standard mechanism (eval, string). |
| `moreThanOneDependencyType` | `boolean` | `true` → classified under multiple dependency types simultaneously. |
| `preCompilationOnly` | `boolean` | `true` → TypeScript type-only import (erased at compile time). |
| `reachable` | `boolean` | `true` → target is reachable transitively (use in `required`). |
| `numberOfDependentsLessThan` | `number` | Module has fewer dependents than this (use in `from` or `module`). |
| `numberOfDependentsMoreThan` | `number` | Module has more dependents than this (use in `from` or `module`). |
| `moreUnstable` | `boolean` | `true` → dependency is less stable than the dependent (instability metric). |
| `license` | `string` | Regex matching the package's SPDX license string. |
| `licenseNot` | `string` | Regex the license must NOT match. |

### dependencyTypes reference
```
local           — relative import within the project
npm             — production npm dependency
npm-dev         — devDependency
npm-peer        — peerDependency
npm-optional    — optionalDependency
npm-bundled     — bundledDependency
npm-unknown     — in node_modules but not in package.json
core            — Node.js built-in (fs, path, …)
aliased         — resolved via tsconfig paths / webpack alias
type-only       — TypeScript `import type` (no runtime dep)
```

### Group capture ($1 … $9)
Parentheses in `from.path` capture sub-strings; use `$1`, `$2`, … in `to.path` / `to.pathNot` to reference them.

```javascript
// "a feature must not import a different feature"
{
  name: "features-not-to-features",
  severity: "error",
  from: { path: "(^src/features/)([^/]+)/" },  // $1 = "src/features/", $2 = feature name
  to:   { path: "^$1", pathNot: "$1$2" },
}
```

### `path` as array (OR semantics)
```javascript
from: { pathNot: ["^src/common", "^src/shared"] }  // must NOT match either
to:   { dependencyTypes: ["npm-dev", "npm-unknown"] }  // must match at least one
```

---

## Pattern catalog

Use the ADR intent to pick the right pattern. Replace angle-bracket placeholders with the actual directory names from the project.

---

### 1. No circular dependencies
```javascript
{
  name: "<prefix>-no-circular",
  comment: "Circular dependencies make the build order unpredictable and increase coupling.",
  severity: "error",
  from: {},
  to: { circular: true },
}
```

---

### 2. Layered architecture — layer A must not import layer B
Replace `<layerA>` / `<layerB>` with real directory names (e.g. `domain`, `application`, `infrastructure`, `presentation`, `controller`, `service`, `repository`).
```javascript
{
  name: "<prefix>-<layerA>-not-to-<layerB>",
  comment: "<LayerA> must not depend on <layerB>; dependency flow is <direction>.",
  severity: "error",
  from: { path: "^<srcRoot>/<layerA>" },
  to:   { path: "^<srcRoot>/<layerB>" },
}
```
Generate one rule per forbidden pair. If ADR says "domain must only depend on nothing", produce rules for every layer it must not reach.

---

### 3. Bounded-context / feature isolation (peer folders must not cross-import)
```javascript
// Code outside features must not reach into them
{
  name: "<prefix>-not-into-features",
  comment: "Only the app entry-point may import features directly; cross-feature imports go through shared.",
  severity: "error",
  from: { pathNot: ["^<srcRoot>/features", "^<srcRoot>/app"] },
  to:   { path: "^<srcRoot>/features" },
},
// Features must not import sibling features
{
  name: "<prefix>-features-not-to-features",
  comment: "One feature must not depend on another feature; use shared/ for cross-cutting code.",
  severity: "error",
  from: { path: "(^<srcRoot>/features/)([^/]+)/" },
  to:   { path: "^$1", pathNot: "$1$2" },
}
```

---

### 4. Index-only (barrel) access — no deep imports into a module
```javascript
{
  name: "<prefix>-no-deep-import-into-<module>",
  comment: "Consumers must only import from <module>/index; internal structure is private.",
  severity: "error",
  from: { pathNot: "^<srcRoot>/<module>" },
  to:   { path: "^<srcRoot>/<module>/(?!index\\.[tj]sx?$)" },
}
```

---

### 5. No devDependencies in production code
```javascript
{
  name: "<prefix>-no-devdep-in-prod",
  comment: "Production source must not import devDependencies; they are absent at runtime.",
  severity: "error",
  from: { pathNot: "\\.(spec|test)\\.[tj]sx?$" },
  to:   { dependencyTypes: ["npm-dev"] },
}
```

---

### 6. No unresolvable imports
```javascript
{
  name: "<prefix>-no-unresolvable",
  comment: "Every import must resolve to an existing module; unresolved imports are runtime errors.",
  severity: "error",
  from: {},
  to:   { couldNotResolve: true },
}
```

---

### 7. Orphan modules (unreachable / dead code)
```javascript
{
  name: "<prefix>-no-orphans",
  comment: "Orphan modules are unreachable dead code and should be removed.",
  severity: "warn",
  from: {
    orphan: true,
    pathNot: [
      "\\.d\\.ts$",                           // type declaration files are legitimately standalone
      "\\.(spec|test)\\.[tj]sx?$",            // test files have no dependents by design
      "^<srcRoot>/index\\.[tj]sx?$",          // entry points have no internal dependents
    ],
  },
  to: {},
}
```

---

### 8. No type-only imports in runtime paths
```javascript
{
  name: "<prefix>-no-type-only-in-runtime",
  comment: "Type-only imports are erased at compile time and must not be used where runtime values are needed.",
  severity: "warn",
  from: { path: "^<srcRoot>/runtime" },
  to:   { preCompilationOnly: true },
}
```

---

### 9. Banned npm package
```javascript
{
  name: "<prefix>-no-<banned-package>",
  comment: "Do not use <banned-package>; use <replacement> instead per ADR-XXX.",
  severity: "error",
  from: {},
  to:   { path: "^node_modules/<banned-package>(/|$)" },
}
```

---

### 10. Module must inherit / use a required base (required rule)
```javascript
{
  name: "<prefix>-must-use-<base>",
  comment: "All <type> modules must import <base>.",
  severity: "error",
  module: {
    path: "<naming-pattern>\\.ts$",        // e.g. "-controller\\.ts$"
    pathNot: "<base-file>\\.ts$",          // exclude the base itself
  },
  to: { path: "<base-file>\\.ts$" },
}
```

---

### 11. No dynamic imports in a sensitive layer
```javascript
{
  name: "<prefix>-no-dynamic-imports",
  comment: "Dynamic imports bypass static analysis and are forbidden in <layer>.",
  severity: "warn",
  from: { path: "^<srcRoot>/<layer>" },
  to:   { dynamic: true },
}
```

---

### 12. Deprecated module — no new dependents (cap current count)
```javascript
// Warn for all existing dependents
{
  name: "<prefix>-not-to-<deprecated>",
  comment: "<deprecated> is deprecated; migrate to <replacement>.",
  severity: "warn",
  from: { pathNot: "^<srcRoot>/<deprecated>" },
  to:   { path: "^<srcRoot>/<deprecated>" },
},
// Error if dependent count exceeds the current tally (prevents new ones)
{
  name: "<prefix>-no-new-dependents-on-<deprecated>",
  comment: "Dependent count on <deprecated> must not grow; it is being phased out.",
  severity: "error",
  from: { pathNot: "^<srcRoot>/<deprecated>" },
  module: {
    path: "^<srcRoot>/<deprecated>",
    numberOfDependentsMoreThan: <current-count>,
  },
}
```

---

### 13. Shared utility must be used by multiple modules (usage gate)
```javascript
{
  name: "<prefix>-<util>-must-be-shared",
  comment: "<util> is a shared module and must be used by at least 2 consumers.",
  severity: "warn",
  from: { path: "^<srcRoot>/features/" },
  module: {
    path: "^<srcRoot>/common/<util>",
    numberOfDependentsLessThan: 2,
  },
}
```

---

## Full config file structure

The caller assembles rules from this skill into the following shell. Fill in all `<…>` placeholders.

```javascript
/** @type {import('dependency-cruiser').IConfiguration} */
module.exports = {
  forbidden: [
    // — paste forbidden rule objects here —
  ],

  // required: [
  //   // — paste required rule objects here —
  // ],

  // allowed: [
  //   // — paste allowed rule objects here (rare; prefer forbidden) —
  // ],

  options: {
    doNotFollow: {
      path: "node_modules",
    },

    includeOnly: "^<srcRoot>/",          // e.g. "^src/"  — always set this

    /* TypeScript: include when tsconfig.json exists */
    tsConfig: { fileName: "./tsconfig.json" },
    tsPreCompilationDeps: true,

    moduleSystems: ["es6", "cjs"],

    reporterOptions: {
      dot: {
        collapsePattern: "^node_modules/[^/]+/",
      },
      archi: {
        collapsePattern: "^(node_modules|<srcRoot>/[^/]+/[^/]+)/",
      },
    },
  },
};
```

---

## Naming convention

Rule names must:
- Be kebab-case, lowercase letters/digits/hyphens only
- Start with a short prefix derived from the ADR topic or slug (e.g. `co-` for code-organization, `lr-` for language-runtime)
- Be unique within the config file

Examples: `co-no-circular`, `co-features-not-to-features`, `lr-no-devdep-in-prod`

---

## Safe defaults checklist

Before emitting any rule, verify:

- [ ] `from: {}` is used (not `from: { path: ".+" }`) when matching all modules — it is faster.
- [ ] `path` values are regex strings, not globs. Test: `new RegExp(value)` must not throw.
- [ ] All paths use forward slashes as separators.
- [ ] Layer/directory names in patterns match the **actual** directory names from the project, not generic placeholders.
- [ ] `doNotFollow.path: "node_modules"` is always present in `options`.
- [ ] `includeOnly` is always set to avoid crawling the whole repo.
- [ ] No two rules in the same config have identical `name` values.
- [ ] `comment` is a plain string — no backticks, no markdown formatting.
- [ ] Orphan rules exclude `.d.ts`, test files, and entry-point index files via `pathNot` array.

---

## Common pitfalls

| Pitfall | Fix |
|---|---|
| Config file uses `.js` extension in an ESM project | Rename to `.dependency-cruiser.cjs` |
| TypeScript paths aliases not resolved | Add `tsConfig: { fileName: "./tsconfig.json" }` to `options` |
| Rule matches node_modules unintentionally | Add `from: { pathNot: "^node_modules" }` or use `includeOnly` |
| Circular rule fires on test helpers | Add `pathNot: "\\.(spec\|test)\\.[tj]sx?$"` to `from` |
| Group capture `$1` not working | Verify opening `(` in `from.path`; `$1` must appear in `to.path` or `to.pathNot` only |
| `required` rule flags the base module itself | Add `pathNot: "<base-file>\\.[tj]s$"` to `module` |
| Performance slow on large codebase | Set `moduleSystems: ["esm"]` if project is ESM-only; enable `cache: true` in options |
