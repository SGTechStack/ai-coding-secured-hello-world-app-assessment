import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// The frontend runs on its own origin (http://localhost:3000) and talks to the
// backend on http://localhost:8080 directly; CORS is configured server-side.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    strictPort: true,
  },
  preview: {
    port: 3000,
    strictPort: true,
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    include: ['src/**/*.test.{ts,tsx}'],
  },
});
