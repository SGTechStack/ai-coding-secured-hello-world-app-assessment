/// <reference types="vitest/config" />
import { tanstackRouter } from '@tanstack/router-plugin/vite';
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

export default defineConfig(({ command }) => ({
  plugins: [tanstackRouter({ target: 'react', autoCodeSplitting: true }), react(), tailwindcss()],
  html: { cspNonce: command === 'serve' ? 'development-csp-nonce' : '__CSP_NONCE__' },
  // Only reached through a lazy import(); pre-bundle up front so the dev server never serves them as "Outdated
  // Optimize Dep" (504), which left the password strength meter stuck on "Not rated yet". Production is unaffected.
  optimizeDeps: { include: ['@zxcvbn-ts/core', '@zxcvbn-ts/language-common'] },
  server: {
    // IPv4 explicitly: "localhost" can resolve to ::1 only, which IPv4 clients cannot reach.
    host: '127.0.0.1',
    proxy: {
      // Keys starting with ^ are regexes; this matches the same prefixes as separate '/api' and '/csrf' entries.
      '^/(api|csrf)': { target: 'https://127.0.0.1:8443', secure: false },
    },
    // The shared password-policy cases live outside the frontend so the backend suite runs the same file.
    fs: { allow: ['.', '../test-fixtures'] },
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test-setup.ts',
    // The route-tree tests render the full User list and query it by role; under coverage instrumentation
    // several take 5-8s, past vitest's 5s default. ponytail: raise further or speed up rowOf() if they grow.
    testTimeout: 20000,
    // `npm run coverage` exits non-zero below the threshold, which fails the Maven build (backend/pom.xml).
    coverage: {
      provider: 'v8',
      // Every source file, so one no test loads counts as uncovered instead of being left out.
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/**/*.test.{ts,tsx}', 'src/routeTree.gen.ts', 'src/test-setup.ts'],
      reporter: ['text-summary', 'html'],
      thresholds: { lines: 90 },
    },
  },
}));
