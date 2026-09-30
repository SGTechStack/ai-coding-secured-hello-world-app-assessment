import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [react()],
  // The API's CORS allow-list names this exact origin in the dev profile.
  server: { port: 5173, strictPort: true },
  test: {
    environment: 'jsdom',
    // e2e/ belongs to Playwright.
    include: ['src/**/*.test.{ts,tsx}'],
    setupFiles: ['./src/test-setup.ts'],
  },
});
