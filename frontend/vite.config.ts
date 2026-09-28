import path from 'node:path'
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

// Document headers (ADR-059; ADR-060). The meta tag in index.html carries VITE_CSP; the dev and preview servers send
// the same policy as a header plus frame-ancestors, which a meta-delivered policy cannot carry (R-HDR-010).
export default defineConfig(({ mode }) => {
  const { VITE_CSP } = loadEnv(mode, import.meta.dirname, 'VITE_')
  if (!VITE_CSP) {
    throw new Error(`VITE_CSP is not set for mode "${mode}"; the document would ship without a CSP`)
  }
  const headers = {
    'Content-Security-Policy': `${VITE_CSP}; frame-ancestors 'none'`,
    'Referrer-Policy': 'no-referrer',
    'X-Content-Type-Options': 'nosniff',
  }

  return {
    plugins: [react(), tailwindcss()],
    resolve: { alias: { '@': path.resolve(import.meta.dirname, 'src') } },
    server: { port: 5173, strictPort: true, headers },
    preview: { headers },
    build: {
      // No inline script and no data: URIs in the built document (R-BLD-004; T-BLD-003).
      chunkImportMap: false,
      assetsInlineLimit: 0,
    },
  }
})
