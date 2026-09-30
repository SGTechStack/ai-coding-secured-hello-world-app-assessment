// ESLint flat config for the React + TypeScript frontend.
import js from '@eslint/js';
import pluginQuery from '@tanstack/eslint-plugin-query';
import jsxA11y from 'eslint-plugin-jsx-a11y';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import globals from 'globals';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  // Build output and generated files.
  { ignores: ['dist', 'coverage', '.tanstack', 'src/routeTree.gen.ts'] },

  // Application source (browser, type-aware rules).
  {
    files: ['src/**/*.{ts,tsx}'],
    extends: [
      js.configs.recommended,
      ...tseslint.configs.recommendedTypeChecked,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
      jsxA11y.flatConfigs.recommended,
      ...pluginQuery.configs['flat/recommended'],
    ],
    languageOptions: {
      globals: globals.browser,
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
    rules: {
      '@typescript-eslint/consistent-type-imports': 'error',
      '@typescript-eslint/no-deprecated': 'error',
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
      // TanStack Router guards throw redirect()/notFound() by design; everything else must still throw an Error.
      '@typescript-eslint/only-throw-error': [
        'error',
        {
          allow: [{ from: 'package', package: '@tanstack/router-core', name: ['Redirect', 'NotFoundError'] }],
        },
      ],
    },
  },

  // Test files: relax rules that fight common testing patterns.
  {
    files: ['src/**/*.test.{ts,tsx}', 'src/test-setup.ts'],
    rules: {
      'react-refresh/only-export-components': 'off',
      '@typescript-eslint/no-unsafe-assignment': 'off',
      '@typescript-eslint/no-unsafe-member-access': 'off',
    },
  },

  // Node-side tooling config.
  {
    files: ['*.config.{js,ts}', 'architecture/**/*.ts'],
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    languageOptions: { globals: globals.node },
  },
);
