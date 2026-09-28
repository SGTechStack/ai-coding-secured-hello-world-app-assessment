import { createServer } from 'node:net'
import { defineConfig, devices } from '@playwright/test'

// A free port picked once by the runner; workers inherit it through the environment.
async function freePort(): Promise<number> {
  return new Promise((resolve, reject) => {
    const srv = createServer()
    srv.once('error', reject)
    srv.listen(0, '127.0.0.1', () => {
      const { port } = srv.address() as { port: number }
      srv.close(() => resolve(port))
    })
  })
}
process.env.PW_PREVIEW_PORT ??= String(await freePort())
const port = Number(process.env.PW_PREVIEW_PORT)

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: { baseURL: `http://localhost:${port}`, trace: 'retain-on-failure' },
  // Level E runs on Chromium and Firefox only (R-FE-006).
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'firefox', use: { ...devices['Desktop Firefox'] } },
  ],
  // `vite preview` of the production build is the verification surface for the document policy (ADR-060).
  webServer: {
    command: `npm run build && npx vite preview --port ${port} --strictPort`,
    url: `http://localhost:${port}`,
    reuseExistingServer: false,
    timeout: 180_000,
  },
})
