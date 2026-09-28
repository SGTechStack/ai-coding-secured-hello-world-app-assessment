/** @type {import('dependency-cruiser').IConfiguration} */
// Architecture rules for the frontend. Run from web/ with `npm run arch`.
//
// The layering mirrors the backend's: a transport layer, a typed API layer over it, and views on top.
// These rules are the half of that design a reader cannot verify by looking, so they are the half
// worth automating.
module.exports = {
  forbidden: [
    {
      name: "no-circular",
      comment:
        "A cycle means neither module can be read, changed or reasoned about on its own, and it makes module initialisation order load-bearing.",
      severity: "error",
      from: {},
      to: { circular: true },
    },
    {
      name: "views-use-the-typed-api",
      comment:
        "Pages and components must not import the fetch wrapper directly. Going through src/api/*.api.ts is what keeps request paths, CSRF handling and error shapes in one place instead of sprinkled through the view layer.",
      severity: "error",
      from: { path: "^src/(pages|components)/" },
      to: { path: "^src/api/client\\.ts$" },
    },
    {
      name: "views-use-no-raw-http-client",
      comment:
        "No page or component may reach for an HTTP library of its own. One transport, with one place that adds credentials and the CSRF header.",
      severity: "error",
      from: { path: "^src/(pages|components)/" },
      to: {
        dependencyTypes: ["npm"],
        path: "^(axios|node-fetch|got|ky|cross-fetch|superagent)$",
      },
    },
    {
      name: "api-layer-is-a-leaf",
      comment:
        "The API layer must not depend on the UI above it. If it did, the request code could not be read, tested or reused without dragging React along.",
      severity: "error",
      from: { path: "^src/api/" },
      to: { path: "^src/(pages|components|auth)/" },
    },
    {
      name: "lib-is-a-leaf",
      comment:
        "src/lib holds utilities. A utility that reaches back into a page or a provider is not a utility, it is a page in the wrong folder.",
      severity: "error",
      from: { path: "^src/lib/" },
      to: { path: "^src/(pages|components|auth)/" },
    },
    {
      name: "ui-primitives-stay-presentational",
      comment:
        "src/components/ui must stay free of data fetching and auth state, so the primitives can be used anywhere and rendered in isolation.",
      severity: "error",
      from: { path: "^src/components/ui/" },
      to: { path: "^src/(api|auth|pages)/" },
    },
    {
      name: "pages-do-not-import-pages",
      comment:
        "Routes are composed in App.tsx. One page importing another turns navigation into an implicit dependency graph that the router no longer describes.",
      severity: "error",
      from: { path: "^src/pages/" },
      to: { path: "^src/pages/" },
    },
    {
      name: "no-orphans",
      comment:
        "A module nothing imports is either dead code or a wiring mistake. Both are worth knowing about.",
      severity: "error",
      from: {
        orphan: true,
        pathNot: ["^src/main\\.tsx$", "\\.d\\.ts$", "^src/index\\.css$"],
      },
      to: {},
    },
    {
      name: "no-unresolvable",
      comment: "An import that does not resolve is a build waiting to break.",
      severity: "error",
      from: {},
      to: { couldNotResolve: true },
    },
  ],

  options: {
    doNotFollow: { path: "node_modules" },
    includeOnly: "^src/",
    tsConfig: { fileName: "./tsconfig.json" },
    // Without this, type-only imports are invisible and a view could depend on the API layer's types
    // while the rules reported a clean graph.
    tsPreCompilationDeps: true,
    moduleSystems: ["es6", "cjs"],
    reporterOptions: {
      dot: { collapsePattern: "^node_modules/[^/]+/" },
      archi: { collapsePattern: "^(node_modules|src/[^/]+/[^/]+)/" },
    },
  },
};
