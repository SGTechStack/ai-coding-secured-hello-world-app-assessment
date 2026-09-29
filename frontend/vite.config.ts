import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The frontend runs on its own origin (http://localhost:5173) and talks to the
// backend (http://localhost:8080) cross-origin. No dev proxy is used, so the
// browser performs a real cross-origin, credentialed request that the backend
// CORS allow-list must permit.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
  },
});
