/** @type {import('dependency-cruiser').IConfiguration} */
module.exports = {
  forbidden: [
    {
      name: "fe-no-circular",
      comment:
        "Circular dependencies make the build order unpredictable and increase coupling.",
      severity: "error",
      from: {},
      to: { circular: true },
    },
  ],

  options: {
    doNotFollow: {
      path: "node_modules",
    },

    includeOnly: "^src/",

    moduleSystems: ["es6", "cjs"],

    reporterOptions: {
      dot: {
        collapsePattern: "^node_modules/[^/]+/",
      },
      archi: {
        collapsePattern: "^(node_modules|src/[^/]+/[^/]+)/",
      },
    },
  },
};
