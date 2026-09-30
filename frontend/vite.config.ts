import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv, type Plugin } from 'vite'

const SECURITY_TXT_PATH = '/.well-known/security.txt'
const SECURITY_TXT_VALIDITY_DAYS = 180

/**
 * True only for `http(s)://host[:port]` exactly: no path (not even `/`), query, fragment, credentials
 * or wildcard host (which would widen the CSP's connect-src).
 */
function isBareOrigin(value: string): boolean {
  try {
    const url = new URL(value)
    return (
      (url.protocol === 'http:' || url.protocol === 'https:') &&
      url.origin === value &&
      /^([a-z0-9-]+\.)*[a-z0-9-]+$|^\[[0-9a-f:.]+\]$/.test(url.hostname)
    )
  } catch {
    return false
  }
}

/**
 * Security plumbing for the SPA:
 * - writes the Content-Security-Policy meta tag into index.html, pointing connect-src at the API origin
 * - serves /.well-known/security.txt (RFC 9116) in dev and emits it in production builds
 * - fails a production build when VITE_SECURITY_CONTACT or VITE_API_ORIGIN is missing
 */
function spaSecurity(mode: string, command: 'serve' | 'build'): Plugin {
  const env = loadEnv(mode, process.cwd(), 'VITE_')
  const apiOrigin = env.VITE_API_ORIGIN
  const contact = env.VITE_SECURITY_CONTACT

  if (command === 'build' && mode === 'production') {
    const missing = [!contact && 'VITE_SECURITY_CONTACT', !apiOrigin && 'VITE_API_ORIGIN'].filter(Boolean)
    if (missing.length > 0) {
      throw new Error(`Production build requires ${missing.join(' and ')} to be set.`)
    }
  }
  if (!apiOrigin || !isBareOrigin(apiOrigin)) {
    throw new Error(
      'VITE_API_ORIGIN must be a bare origin (scheme, host, optional port; no path, query or fragment), e.g. http://localhost:8080',
    )
  }
  if (!contact || /[\r\n]/.test(contact)) {
    throw new Error('VITE_SECURITY_CONTACT must be set to a single-line contact URI')
  }

  const directives = [`default-src 'self'`, `connect-src 'self' ${apiOrigin}`, `object-src 'none'`]
  if (command === 'serve') {
    // Dev server only: Vite injects an inline React-refresh script and inline <style> tags.
    // Production builds carry the strict policy above with no 'unsafe-inline'.
    directives.push(`script-src 'self' 'unsafe-inline'`, `style-src 'self' 'unsafe-inline'`)
  }
  const csp = directives.join('; ')

  const securityTxt = () => {
    const expires = new Date(Date.now() + SECURITY_TXT_VALIDITY_DAYS * 24 * 60 * 60 * 1000)
    return `Contact: ${contact}\nExpires: ${expires.toISOString()}\nPreferred-Languages: en\n`
  }

  return {
    name: 'spa-security',
    transformIndexHtml: {
      order: 'pre',
      handler: (html) => ({
        html,
        tags: [
          {
            tag: 'meta',
            attrs: { 'http-equiv': 'Content-Security-Policy', content: csp },
            injectTo: 'head-prepend',
          },
        ],
      }),
    },
    configureServer(server) {
      server.middlewares.use(SECURITY_TXT_PATH, (_req, res) => {
        res.setHeader('Content-Type', 'text/plain; charset=utf-8')
        res.end(securityTxt())
      })
    },
    generateBundle() {
      this.emitFile({ type: 'asset', fileName: SECURITY_TXT_PATH.slice(1), source: securityTxt() })
    },
  }
}

// SPA on http://localhost:3000, API on its own origin. No dev proxy: calls are genuinely cross-origin.
export default defineConfig(({ mode, command }) => ({
  plugins: [react(), spaSecurity(mode, command)],
  server: { port: 3000, strictPort: true },
  preview: { port: 3000, strictPort: true },
}))
