// For more info, see https://github.com/storybookjs/eslint-plugin-storybook#configuration-flat-config-format
import storybook from 'eslint-plugin-storybook';

// ESLint rule severity policy:
//   'error' — affects correctness or security; must fix before merge
//   'warn'  — affects code quality or developer experience; fix when possible
//   'off'   — rule is incompatible with our patterns (e.g. framework conventions) OR
//             only impacts local dev experience (e.g. HMR speed) with no effect on production

import js from '@eslint/js';
import globals from 'globals';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import tseslint from 'typescript-eslint';
import { defineConfig, globalIgnores } from 'eslint/config';

export default defineConfig([
  globalIgnores(['dist', 'storybook-static/**', 'src/api/generated/**']),
  {
    files: ['**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommended,
      reactHooks.configs.flat.recommended,
      // off: only affects HMR speed in dev (full reload vs fast refresh) — no production impact.
      // TanStack Router exports `Route` and shadcn exports variants alongside components by convention.
      {
        ...reactRefresh.configs.vite,
        rules: { 'react-refresh/only-export-components': 'off' },
      },
    ],
    languageOptions: {
      globals: globals.browser,
    },
  },
  ...storybook.configs['flat/recommended'],
]);
