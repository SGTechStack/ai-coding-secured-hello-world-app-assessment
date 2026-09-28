import js from "@eslint/js";
import globals from "globals";
import tseslint from "typescript-eslint";
import reactHooks from "eslint-plugin-react-hooks";
import reactRefresh from "eslint-plugin-react-refresh";

export default tseslint.config(
  {
    ignores: [
      "node_modules/**",
      "dist/**",
      "playwright-report/**",
      "test-results/**",
      ".dependency-cruiser.cjs",
    ],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ["src/**/*.{ts,tsx}"],
    languageOptions: {
      globals: globals.browser,
      parserOptions: { project: "./tsconfig.json" },
    },
    plugins: {
      "react-hooks": reactHooks,
      "react-refresh": reactRefresh,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      "react-refresh/only-export-components": [
        "warn",
        { allowConstantExport: true },
      ],
      "no-console": "error",
      eqeqeq: ["error", "always"],
    },
  },
  {
    files: ["e2e/**/*.ts", "*.config.ts"],
    languageOptions: { globals: { ...globals.node, ...globals.browser } },
  },
);
