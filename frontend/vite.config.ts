/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv, type Plugin } from 'vite'

/**
 * Content Security Policy for the built app: only our own scripts and styles, and network
 * access only to ourselves and the API. `frame-ancestors` is only honoured as a real header.
 */
function contentSecurityPolicy(apiOrigin: string, { asHeader }: { asHeader: boolean }): string {
  const directives = [
    "default-src 'self'",
    "script-src 'self'",
    "style-src 'self'",
    "img-src 'self' data:",
    `connect-src 'self' ${apiOrigin}`,
    "object-src 'none'",
    "base-uri 'none'",
    "form-action 'self'",
  ]
  if (asHeader) {
    directives.push("frame-ancestors 'none'")
  }
  return directives.join('; ')
}

/** Adds the CSP as a <meta> tag to production builds only: the dev server needs inline scripts for hot reload. */
function cspMetaTag(policy: string): Plugin {
  return {
    name: 'csp-meta-tag',
    apply: 'build',
    transformIndexHtml: () => [
      { tag: 'meta', attrs: { 'http-equiv': 'Content-Security-Policy', content: policy }, injectTo: 'head-prepend' },
    ],
  }
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_')
  const apiOrigin = new URL(env.VITE_API_BASE_URL || 'http://localhost:8080').origin

  return {
    plugins: [react(), cspMetaTag(contentSecurityPolicy(apiOrigin, { asHeader: false }))],
    server: {
      port: 3000,
      strictPort: true,
    },
    preview: {
      port: 3000,
      strictPort: true,
      // What a production web server in front of dist/ should also send.
      headers: {
        'Content-Security-Policy': contentSecurityPolicy(apiOrigin, { asHeader: true }),
        'X-Content-Type-Options': 'nosniff',
        'X-Frame-Options': 'DENY',
        'Referrer-Policy': 'no-referrer',
      },
    },
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.ts'],
    },
  }
})
