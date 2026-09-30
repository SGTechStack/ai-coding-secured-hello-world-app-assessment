import react from '@vitejs/plugin-react'
import { defineConfig, type Plugin } from 'vite'

/**
 * Vite's dev server injects CSS via inline <style> tags to support HMR —
 * fundamental to how `vite dev` works, not something a dependency config
 * can opt out of. A strict `style-src 'self'` (no 'unsafe-inline' or nonce)
 * blocks that injection outright, so no CSS renders at all under
 * `npm run dev`. This plugin keeps the production CSP (built via
 * `vite build`) strict, and only relaxes style-src for local dev serving.
 *
 * See index.html for the placeholder this substitutes and the full
 * rationale.
 */
function cspDevStyleSrcUnsafeInline(): Plugin {
  return {
    name: 'csp-dev-style-src-unsafe-inline',
    transformIndexHtml(html, ctx) {
      const styleSrc = ctx.server ? "style-src 'self' 'unsafe-inline'" : "style-src 'self'";
      return html.replace('__STYLE_SRC__', styleSrc);
    },
  };
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), cspDevStyleSrcUnsafeInline()],
  server: {
    port: 3000,
  },
})
