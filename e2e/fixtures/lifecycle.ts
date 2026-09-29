import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

/** The running stack, as recorded by scripts/lifecycle.mjs. */
export interface Lifecycle {
  id: string
  dir: string
  mode: string
  webUrl: string
  apiUrl: string
  /** Backend log file; the development email stub writes reset links here. */
  appLog: string
  admin: { username: string; email: string; password: string }
}

const E2E_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const POINTER = path.join(E2E_DIR, '.runtime', 'current.json')

export function findLifecycle(): Lifecycle | null {
  if (!fs.existsSync(POINTER)) return null
  const { dir } = JSON.parse(fs.readFileSync(POINTER, 'utf8')) as { dir: string }
  return JSON.parse(fs.readFileSync(path.join(dir, 'state.json'), 'utf8')) as Lifecycle
}

export function readLifecycle(): Lifecycle {
  const lifecycle = findLifecycle()
  if (!lifecycle) {
    throw new Error('No E2E lifecycle is running. Use `npm --prefix e2e test` or `npm --prefix e2e run lifecycle:start`.')
  }
  return lifecycle
}
