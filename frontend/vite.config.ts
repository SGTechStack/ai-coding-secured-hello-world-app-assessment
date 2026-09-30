import { loadEnv } from 'vite';
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

/**
 * A production bundle with no API base URL would silently fall back to
 * same-origin relative requests -- wrong for this cross-origin deployment.
 * Fail the build instead. `loadEnv` also picks up `VITE_*` variables from the
 * process environment (which override `.env.production`).
 */
function assertProductionApiBaseUrl(mode: string): void {
  const value = loadEnv(mode, process.cwd(), 'VITE_').VITE_API_BASE_URL?.trim();
  if (!value) {
    throw new Error(
      'VITE_API_BASE_URL must be set for a production build (e.g. VITE_API_BASE_URL=https://api.example.com npm run build).',
    );
  }
  let url: URL;
  try {
    url = new URL(value);
  } catch {
    throw new Error(`VITE_API_BASE_URL is not a valid absolute URL: ${value}`);
  }
  const isLocalhost = url.hostname === 'localhost' || url.hostname === '127.0.0.1';
  if (url.protocol !== 'https:' && !isLocalhost) {
    throw new Error(`VITE_API_BASE_URL must use https:// in production: ${value}`);
  }
}

// Cross-origin deployment: frontend and backend run on separate origins (see
// the backend's app.cors.allowed-origins and the frontend's
// VITE_API_BASE_URL), so there is no build-output wiring into the backend and
// no dev-server API proxy -- requests genuinely cross the origin boundary,
// exercising real CORS + credentials rather than masking it behind a proxy.
export default defineConfig(({ command, mode }) => {
  if (command === 'build' && mode === 'production') {
    assertProductionApiBaseUrl(mode);
  }

  return {
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
  };
});
