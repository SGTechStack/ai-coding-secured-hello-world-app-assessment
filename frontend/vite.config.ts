import react from '@vitejs/plugin-react'
import { defineConfig, type Plugin } from 'vite'

// The built SPA loads only its own bundled assets and calls the API on VITE_API_ORIGIN.
// Build-only: the dev server injects inline scripts for React Fast Refresh. frame-ancestors and
// upgrade-insecure-requests can't be set in a <meta> tag, so whatever serves dist/ must send them
// as headers.
function contentSecurityPolicy(apiOrigin: string): string {
  return (
    `default-src 'self'; connect-src 'self' ${apiOrigin}; img-src 'self' data:; ` +
    "object-src 'none'; base-uri 'self'; form-action 'self'"
  )
}

/** Fails a production build without an API origin and adds the CSP that allows calls to it. */
function apiOriginContentSecurityPolicy(): Plugin {
  let apiOrigin = ''
  return {
    name: 'content-security-policy',
    apply: 'build',
    configResolved(config) {
      apiOrigin = config.env.VITE_API_ORIGIN ?? ''
      if (!/^https?:\/\/[^\s/;']+$/.test(apiOrigin)) {
        throw new Error(
          'VITE_API_ORIGIN must be set to the API origin, e.g. https://api.example.com',
        )
      }
    },
    transformIndexHtml: () => [
      {
        tag: 'meta',
        attrs: {
          'http-equiv': 'Content-Security-Policy',
          content: contentSecurityPolicy(apiOrigin),
        },
        injectTo: 'head-prepend',
      },
    ],
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), apiOriginContentSecurityPolicy()],
  server: {
    // Must match the backend's dev app.security.cors.allowed-origins.
    port: 5173,
    strictPort: true,
  },
})
