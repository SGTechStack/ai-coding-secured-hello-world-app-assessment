#!/usr/bin/env node
/**
 * Harness-owned lifecycle for the E2E stack.
 *
 *   start   build and start a fresh, uniquely identified stack (own database, own ports)
 *   stop    stop only the processes this lifecycle started, then wait until they and their ports are gone
 *   reset   stop the current lifecycle and start a fresh one (fresh database, fresh in-memory throttles)
 *   status  print the current lifecycle and run the health read-back
 *   run     start, run `playwright test <args>`, always stop; exits with the test exit code
 *
 * Isolation: every lifecycle gets its own directory under e2e/.runtime/<id>/ holding its database, logs,
 * built frontend, and the backend built from a copy of its sources, so no developer file or build output
 * is written. The ports differ from the developer servers (8080/3000), so a running dev stack is never
 * touched. Teardown only signals process groups recorded at start, and only while their command line
 * still refers to this lifecycle's directory (a reused PID is never signalled).
 */
import { execFileSync, spawn } from 'node:child_process'
import crypto from 'node:crypto'
import fs from 'node:fs'
import net from 'node:net'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const E2E_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const REPO_DIR = path.resolve(E2E_DIR, '..')
const RUNTIME_ROOT = path.join(E2E_DIR, '.runtime')
const POINTER = path.join(RUNTIME_ROOT, 'current.json')
const API_PORT = Number(process.env.E2E_API_PORT ?? 18080)
const WEB_PORT = Number(process.env.E2E_WEB_PORT ?? 13000)
const HEALTHY_STREAK = 3
const START_TIMEOUT_MS = 180_000
const STOP_TIMEOUT_MS = 30_000

const log = (message) => process.stderr.write(`[lifecycle] ${message}\n`)
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

function readState() {
  if (!fs.existsSync(POINTER)) return null
  const { dir } = JSON.parse(fs.readFileSync(POINTER, 'utf8'))
  const stateFile = path.join(dir, 'state.json')
  return fs.existsSync(stateFile) ? JSON.parse(fs.readFileSync(stateFile, 'utf8')) : { dir, pids: {} }
}

function isAlive(pid) {
  if (!pid) return false
  try {
    process.kill(pid, 0)
    return true
  } catch {
    return false
  }
}

/** True only if `pid` is alive and still one of this lifecycle's processes. */
function isOurs(pid, state) {
  if (!isAlive(pid)) return false
  let commandLine
  try {
    commandLine = fs.readFileSync(`/proc/${pid}/cmdline`, 'utf8').replaceAll('\0', ' ')
  } catch {
    commandLine = execFileSync('ps', ['-o', 'command=', '-p', String(pid)], { encoding: 'utf8' })
  }
  return commandLine.includes(state.dir)
}

function portInUse(port) {
  const probe = (host) =>
    new Promise((resolve) => {
      const socket = net.connect({ port, host })
      socket.once('connect', () => {
        socket.destroy()
        resolve(true)
      })
      socket.once('error', () => resolve(false))
      socket.setTimeout(1000, () => {
        socket.destroy()
        resolve(false)
      })
    })
  return Promise.all([probe('127.0.0.1'), probe('::1')]).then((results) => results.some(Boolean))
}

async function healthOnce(state) {
  try {
    const api = await fetch(`${state.apiUrl}/actuator/health`, { signal: AbortSignal.timeout(3000) })
    const apiBody = api.ok ? await api.json() : null
    const web = await fetch(state.webUrl, { signal: AbortSignal.timeout(3000) })
    const webBody = web.ok ? await web.text() : ''
    return apiBody?.status === 'UP' && webBody.includes('<div id="root">')
  } catch {
    return false
  }
}

/** Read-back for "running": several consecutive healthy checks, never a fixed sleep. */
async function waitHealthy(state, timeoutMs) {
  const deadline = Date.now() + timeoutMs
  let streak = 0
  while (Date.now() < deadline) {
    streak = (await healthOnce(state)) ? streak + 1 : 0
    if (streak >= HEALTHY_STREAK) return true
    for (const [name, pid] of Object.entries(state.pids)) {
      if (!isOurs(pid, state)) throw new Error(`${name} process ${pid} exited during startup; see ${state.dir}/${name}.log`)
    }
    await sleep(1000)
  }
  return false
}

/** Read-back for "released": tracked processes gone and both ports free. */
async function waitReleased(state, timeoutMs) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const alive = Object.values(state.pids ?? {}).some((pid) => isOurs(pid, state))
    const busy = (await portInUse(API_PORT)) || (await portInUse(WEB_PORT))
    if (!alive && !busy) return true
    await sleep(500)
  }
  return false
}

function findJar(target) {
  const jar = fs
    .readdirSync(target)
    .find((name) => name.endsWith('.jar') && !name.endsWith('-plain.jar') && !name.endsWith('.original'))
  if (!jar) throw new Error('backend jar not found after build')
  return path.join(target, jar)
}

function spawnDetached(command, args, { cwd, env, logFile }) {
  const out = fs.openSync(logFile, 'a')
  const child = spawn(command, args, { cwd, env: { ...process.env, ...env }, detached: true, stdio: ['ignore', out, out] })
  child.unref()
  return child.pid
}

async function start() {
  const existing = readState()
  if (existing && Object.values(existing.pids ?? {}).some((pid) => isOurs(pid, existing))) {
    throw new Error(`lifecycle ${existing.id} is already running; stop it first (npm run lifecycle:stop)`)
  }
  if (existing) {
    log(`removing stale lifecycle record ${existing.dir}`)
    fs.rmSync(existing.dir, { recursive: true, force: true })
    fs.rmSync(POINTER, { force: true })
  }
  for (const port of [API_PORT, WEB_PORT]) {
    if (await portInUse(port)) throw new Error(`port ${port} is in use by another process; the harness will not touch it`)
  }

  const id = `e2e-${new Date().toISOString().replace(/[-:]/g, '').replace(/\..+/, 'Z')}-${crypto.randomBytes(3).toString('hex')}`
  const dir = path.join(RUNTIME_ROOT, id)
  fs.mkdirSync(path.join(dir, 'db'), { recursive: true })
  const apiUrl = `http://localhost:${API_PORT}`
  const webUrl = `http://localhost:${WEB_PORT}`
  const admin = {
    username: 'e2e_admin',
    email: 'e2e_admin@example.com',
    password: `E2e-${crypto.randomBytes(12).toString('base64url')}`,
  }

  // Build from a copy of the sources so backend/target and any running developer build stay untouched.
  const backendSrc = path.join(dir, 'backend-src')
  for (const entry of ['pom.xml', 'mvnw', '.mvn', 'src']) {
    fs.cpSync(path.join(REPO_DIR, 'backend', entry), path.join(backendSrc, entry), { recursive: true })
  }
  log(`building backend in ${path.relative(REPO_DIR, backendSrc)}`)
  execFileSync('./mvnw', ['-q', '-B', '-DskipTests', 'package'], { cwd: backendSrc, stdio: 'inherit' })
  const jar = path.join(dir, 'hello-auth.jar')
  fs.copyFileSync(findJar(path.join(backendSrc, 'target')), jar)

  log(`building frontend into ${path.relative(REPO_DIR, dir)}/web`)
  const webEnv = { VITE_API_BASE_URL: apiUrl }
  execFileSync('npx', ['--no-install', 'vite', 'build', '--outDir', path.join(dir, 'web'), '--emptyOutDir', '--logLevel', 'warn'], {
    cwd: path.join(REPO_DIR, 'frontend'),
    env: { ...process.env, ...webEnv },
    stdio: 'inherit',
  })

  const backendPid = spawnDetached(
    'java',
    [
      '-Djava.net.preferIPv4Stack=true',
      '-jar', jar,
      '--spring.profiles.active=dev',
      `--server.port=${API_PORT}`,
      '--server.address=127.0.0.1',
      `--spring.datasource.url=jdbc:h2:file:${path.join(dir, 'db', 'hello-auth')}`,
      `--app.cors.allowed-origins=${webUrl}`,
      `--app.password-reset.reset-url=${webUrl}/reset-password`,
      `--logging.file.name=${path.join(dir, 'app.log')}`,
    ],
    {
      cwd: dir,
      env: { APP_ADMIN_USERNAME: admin.username, APP_ADMIN_EMAIL: admin.email, APP_ADMIN_PASSWORD: admin.password },
      logFile: path.join(dir, 'backend.log'),
    },
  )
  const webPid = spawnDetached(
    'npx',
    ['--no-install', 'vite', 'preview', '--outDir', path.join(dir, 'web'), '--port', String(WEB_PORT), '--strictPort', '--host', '127.0.0.1'],
    { cwd: path.join(REPO_DIR, 'frontend'), env: webEnv, logFile: path.join(dir, 'web.log') },
  )

  const state = {
    id,
    dir,
    mode: 'standard',
    apiUrl,
    webUrl,
    appLog: path.join(dir, 'app.log'),
    admin,
    pids: { backend: backendPid, web: webPid },
    startedAt: new Date().toISOString(),
  }
  fs.writeFileSync(path.join(dir, 'state.json'), JSON.stringify(state, null, 2))
  fs.writeFileSync(POINTER, JSON.stringify({ id, dir }, null, 2))

  if (!(await waitHealthy(state, START_TIMEOUT_MS))) {
    await stop()
    throw new Error(`stack did not become healthy within ${START_TIMEOUT_MS / 1000}s`)
  }
  log(`lifecycle ${id} is healthy (${HEALTHY_STREAK} consecutive checks)`)
  process.stdout.write(`E2E_LIFECYCLE_ID=${id}\nE2E_BASE_URL=${webUrl}\nE2E_API_URL=${apiUrl}\n`)
  return state
}

async function stop({ keep = false } = {}) {
  const state = readState()
  if (!state) {
    log('no lifecycle is running')
    return
  }
  for (const pid of Object.values(state.pids ?? {})) {
    if (isOurs(pid, state)) {
      try {
        process.kill(-pid, 'SIGTERM')
      } catch {
        // already gone
      }
    }
  }
  if (!(await waitReleased(state, STOP_TIMEOUT_MS / 2))) {
    for (const pid of Object.values(state.pids ?? {})) {
      if (!isOurs(pid, state)) continue
      try {
        process.kill(-pid, 'SIGKILL')
      } catch {
        // already gone
      }
    }
  }
  if (!(await waitReleased(state, STOP_TIMEOUT_MS / 2))) {
    throw new Error(`lifecycle ${state.id} did not release its processes/ports (${API_PORT}, ${WEB_PORT})`)
  }
  fs.rmSync(POINTER, { force: true })
  if (keep) {
    log(`lifecycle ${state.id} stopped; runtime kept at ${state.dir}`)
  } else {
    fs.rmSync(state.dir, { recursive: true, force: true })
    log(`lifecycle ${state.id} stopped and removed`)
  }
}

async function status() {
  const state = readState()
  if (!state) {
    process.stdout.write('no lifecycle is running\n')
    return 1
  }
  const healthy = await waitHealthy(state, 10_000).catch(() => false)
  process.stdout.write(`lifecycle ${state.id} (${state.mode}) at ${state.webUrl}: ${healthy ? 'healthy' : 'UNHEALTHY'}\n`)
  return healthy ? 0 : 1
}

async function run(args) {
  const state = await start()
  const evidenceRoot = path.join(REPO_DIR, 'artifacts', 'e2e', 'runs', state.id)
  const env = {
    ...process.env,
    E2E_LIFECYCLE_ID: state.id,
    E2E_BASE_URL: state.webUrl,
    ALLURE_RESULTS_DIR: process.env.ALLURE_RESULTS_DIR ?? path.join(evidenceRoot, 'allure-results'),
    PLAYWRIGHT_OUTPUT_DIR: process.env.PLAYWRIGHT_OUTPUT_DIR ?? path.join(evidenceRoot, 'test-results'),
  }
  let code = 1
  try {
    code = await new Promise((resolve) => {
      const child = spawn('npx', ['--no-install', 'playwright', 'test', ...args], { cwd: E2E_DIR, env, stdio: 'inherit' })
      child.on('exit', (exitCode) => resolve(exitCode ?? 1))
    })
  } finally {
    await stop({ keep: code !== 0 })
  }
  log(`evidence: ${path.relative(REPO_DIR, evidenceRoot)}`)
  return code
}

const [command, ...rest] = process.argv.slice(2)
const commands = {
  start: async () => (await start(), 0),
  stop: async () => (await stop({ keep: rest.includes('--keep') }), 0),
  reset: async () => (await stop(), await start(), 0),
  status,
  run: () => run(rest[0] === '--' ? rest.slice(1) : rest),
}
if (!commands[command]) {
  process.stderr.write('usage: lifecycle.mjs <start|stop [--keep]|reset|status|run [-- playwright args]>\n')
  process.exit(2)
}
commands[command]()
  .then((code) => process.exit(code))
  .catch((error) => {
    log(`FAILED: ${error.message}`)
    process.exit(1)
  })
