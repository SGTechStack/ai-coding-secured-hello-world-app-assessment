import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// Cross-origin deployment: frontend and backend run on separate origins (see
// the backend's app.cors.allowed-origins and the frontend's
// VITE_API_BASE_URL), so there is no build-output wiring into the backend and
// no dev-server API proxy -- requests genuinely cross the origin boundary,
// exercising real CORS + credentials rather than masking it behind a proxy.
export default defineConfig({
  plugins: [react()],
  // Pinned to match the backend's dev-profile CORS allow-list
  // (app.cors.allowed-origins=http://localhost:3000) -- Vite's own default
  // of 5173 would otherwise get CORS-blocked against that origin.
  server: {
    port: 3000,
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    css: true,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'],
      // Coverage gate per workshop standard (>=80%).
      thresholds: {
        lines: 80,
        functions: 80,
        branches: 80,
        statements: 80,
      },
      include: ['src/**/*.{ts,tsx}'],
      exclude: ['src/main.tsx', 'src/test/**', 'src/**/*.d.ts'],
    },
  },
});
