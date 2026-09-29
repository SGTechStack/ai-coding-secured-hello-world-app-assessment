import fs from 'node:fs'
import { readLifecycle } from '../fixtures/lifecycle'

const HEALTHY_STREAK = 3

async function healthy(apiUrl: string, webUrl: string): Promise<boolean> {
  try {
    const api = await fetch(`${apiUrl}/actuator/health`, { signal: AbortSignal.timeout(3000) })
    const web = await fetch(webUrl, { signal: AbortSignal.timeout(3000) })
    return api.ok && ((await api.json()) as { status?: string }).status === 'UP' && web.ok
  } catch {
    return false
  }
}

/** Runs once per test run, never per config load. */
export default async function globalSetup(): Promise<void> {
  if (process.env.E2E_CAMPAIGN_SUITE) {
    for (const dir of [process.env.ALLURE_RESULTS_DIR!, process.env.PLAYWRIGHT_OUTPUT_DIR!]) {
      if (fs.existsSync(dir) && fs.readdirSync(dir).length > 0) {
        throw new Error(`campaign output directory must be empty before a campaign run: ${dir}`)
      }
    }
  }
  const lifecycle = readLifecycle()
  for (let streak = 0, attempt = 0; streak < HEALTHY_STREAK; attempt++) {
    if (attempt >= 20) throw new Error(`lifecycle ${lifecycle.id} is not healthy at ${lifecycle.webUrl}`)
    streak = (await healthy(lifecycle.apiUrl, lifecycle.webUrl)) ? streak + 1 : 0
    if (streak < HEALTHY_STREAK) await new Promise((resolve) => setTimeout(resolve, 500))
  }
}
