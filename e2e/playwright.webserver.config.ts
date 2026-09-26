import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { defineConfig } from '@playwright/test'

import base from './playwright.config'

/**
 * Self-contained run config for story campaigns: Playwright itself starts the
 * dev backend (fresh in-memory H2) and the Vite SPA, waits for readiness, and
 * stops both when the run ends. Extends the foundation config unchanged.
 *
 *   npx playwright test -c playwright.webserver.config.ts <spec-dir>
 */
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const backendPort = Number(process.env.E2E_BACKEND_PORT ?? 18080)
const frontendPort = Number(process.env.E2E_FRONTEND_PORT ?? 13000)
const jar = path.join(root, 'backend', 'target', 'hello-auth-backend-0.0.1-SNAPSHOT.jar')

process.env.E2E_API_URL ??= `http://localhost:${backendPort}`

export default defineConfig({
  ...base,
  use: { ...base.use, baseURL: `http://localhost:${frontendPort}` },
  webServer: [
    {
      command: `java -jar "${jar}" --spring.profiles.active=dev --server.port=${backendPort} --app.cors.allowed-origins=http://localhost:${frontendPort}`,
      url: `http://localhost:${backendPort}/actuator/health`,
      reuseExistingServer: false,
      timeout: 120_000,
      stdout: 'pipe',
      stderr: 'pipe',
    },
    {
      command: `npm run dev -- --port ${frontendPort} --strictPort`,
      cwd: path.join(root, 'frontend'),
      env: { VITE_API_BASE: `http://localhost:${backendPort}` },
      url: `http://localhost:${frontendPort}`,
      reuseExistingServer: false,
      timeout: 120_000,
    },
  ],
})
