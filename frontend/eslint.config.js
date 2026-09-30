import js from '@eslint/js';
import globals from 'globals';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  { ignores: ['dist', 'coverage', 'node_modules'] },
  {
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      ecmaVersion: 2022,
      globals: globals.browser,
    },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
      // Architectural boundary: UI never calls fetch directly; it goes through src/api.
      'no-restricted-globals': ['error', { name: 'fetch', message: 'Use apiFetch from src/api/client.' }],
    },
  },
  {
    files: ['src/api/**/*.ts', 'src/**/*.test.{ts,tsx}', 'src/test/**/*.ts'],
    rules: {
      'no-restricted-globals': 'off',
    },
  },
);
