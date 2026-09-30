// vitest/config, not vite: the `test` block below is not part of Vite's own config type.
import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // A dev proxy rather than cross-origin calls to :8080, and the reason is the session cookie.
    //
    // `server.servlet.session.cookie.secure` is `true` in every profile (spec.md S4, ticket 09) and
    // `SameSite=Lax`. A browser on http://localhost:5173 calling http://localhost:8080 directly makes
    // every API call cross-site, so `SameSite=Lax` withholds the cookie on the POSTs that matter and
    // the app appears to log in and then immediately forget. Proxying makes every call same-origin,
    // which is also how the built bundle is served in production — so dev and prod agree.
    //
    // CORS on the backend is configured and tested regardless (story 1.4): it is the contract for any
    // client that is genuinely cross-origin, not a requirement of this dev setup.
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: false,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    // Narrow by design (spec.md S12): the auth context and the axios interceptor. Everything else on
    // the frontend is implementer discretion, explicitly — and the two named here are the only things
    // no backend test can cover.
    include: ['src/**/*.test.{ts,tsx}'],
  },
})
