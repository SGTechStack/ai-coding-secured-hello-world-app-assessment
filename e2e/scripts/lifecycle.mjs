// Harness-owned stack lifecycle for the Secured Hello World E2E foundation.
//
// Commands:
//   node e2e/scripts/lifecycle.mjs up     -> start backend(:8080)+frontend(:5173), health-gate, record state
//   node e2e/scripts/lifecycle.mjs reset  -> restart ONLY the backend (fresh in-mem H2 + re-seeded admin), re-gate
//   node e2e/scripts/lifecycle.mjs down   -> stop ONLY processes this lifecycle started, wait for ports/tree release
//   node e2e/scripts/lifecycle.mjs status -> print recorded lifecycle state
//
// Contract:
//   * Every `up` mints a unique UTC lifecycle id, printed to stdout and stored in
//     e2e/.runtime/lifecycle.json (a declared, lifecycle-owned runtime dir).
//   * Never a fixed sleep between runs: we POLL health/read-back and require several
//     consecutive successes before returning; teardown polls until ports are free.
//   * A declared target state always has a harness-owned read-back check; a non-zero
//     exit means a real failure, not a benign tool diagnostic.
//   * Teardown targets only the PIDs/ports this lifecycle created.

import { spawn, exec } from 'node:child_process';
import { promisify } from 'node:util';
import { mkdirSync, writeFileSync, readFileSync, existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve, join } from 'node:path';
import net from 'node:net';

const execAsync = promisify(exec);

const HERE = dirname(fileURLToPath(import.meta.url));
const E2E_ROOT = resolve(HERE, '..');
const REPO_ROOT = resolve(E2E_ROOT, '..');
const RUNTIME_DIR = join(E2E_ROOT, '.runtime');
const STATE_FILE = join(RUNTIME_DIR, 'lifecycle.json');

const BACKEND_PORT = 8080;
const FRONTEND_PORT = 5173;
const BACKEND_HEALTH = `http://localhost:${BACKEND_PORT}/api/health`;
const FRONTEND_URL = `http://localhost:${FRONTEND_PORT}/`;

const HEALTH_CONSECUTIVE = 3; // consecutive successes required before "ready"
const HEALTH_INTERVAL_MS = 1000; // poll cadence (NOT a fixed readiness sleep)
const HEALTH_TIMEOUT_MS = 180_000; // hard cap for the whole health gate
const PORT_RELEASE_TIMEOUT_MS = 60_000;

const isWindows = process.platform === 'win32';

function log(message) {
  process.stdout.write(`[lifecycle] ${message}\n`);
}

function nowUtcId() {
  return new Date().toISOString().replace(/[:.]/g, '-');
}

function readState() {
  if (!existsSync(STATE_FILE)) return null;
  try {
    return JSON.parse(readFileSync(STATE_FILE, 'utf8'));
  } catch {
    return null;
  }
}

function writeState(state) {
  mkdirSync(RUNTIME_DIR, { recursive: true });
  writeFileSync(STATE_FILE, `${JSON.stringify(state, null, 2)}\n`, 'utf8');
}

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

// node's spawn(..., { shell: true }) on Windows joins argv into a single command
// line WITHOUT quoting each element first — an arg containing a space (this repo's
// own path does: "...\Jil Ong Sze Min\...") silently splits into multiple argv
// tokens for the child. Quote any arg that needs it before handing it to the shell.
function quoteForWindowsShell(arg) {
  if (/[\s"]/.test(arg)) {
    return `"${arg.replace(/"/g, '\\"')}"`;
  }
  return arg;
}

// Spawn a detached-tracked child in its own process group so we can kill the whole tree.
function startProcess(command, args, options) {
  const shellSafeArgs = isWindows ? args.map(quoteForWindowsShell) : args;
  const child = spawn(command, shellSafeArgs, {
    cwd: options.cwd,
    env: { ...process.env, ...(options.env ?? {}) },
    stdio: 'ignore',
    shell: isWindows, // resolve mvn.cmd / npm.cmd on Windows
    detached: !isWindows, // own process group on POSIX; Windows uses taskkill /T
    windowsHide: true,
  });
  child.unref();
  return child;
}

// Check a single address family. Vite on this machine can bind IPv6-only
// ([::1]:5173) while leaving the IPv4 loopback free, so a check scoped to
// 127.0.0.1 alone reports a port "free" while something is still bound to it
// via ::1 — exactly the condition that let a stale dev server survive
// teardown and then broke the next `npm run dev` with EADDRINUSE. Only an
// EADDRINUSE on THIS host counts as busy; any other bind error (e.g. IPv6
// unavailable in this environment) is treated as free so hosts without IPv6
// still work.
async function isHostPortFree(port, host) {
  return new Promise((resolveFree) => {
    const tester = net.createServer();
    tester.once('error', (error) => resolveFree(error?.code !== 'EADDRINUSE'));
    tester.once('listening', () => tester.close(() => resolveFree(true)));
    tester.listen(port, host);
  });
}

async function isPortFree(port) {
  const [v4Free, v6Free] = await Promise.all([
    isHostPortFree(port, '127.0.0.1'),
    isHostPortFree(port, '::1'),
  ]);
  return v4Free && v6Free;
}

async function httpOk(url) {
  try {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 4000);
    const response = await fetch(url, { signal: controller.signal });
    clearTimeout(timer);
    return response.ok;
  } catch {
    return false;
  }
}

// Poll a read-back check until we get HEALTH_CONSECUTIVE successes in a row.
async function waitReady(label, check) {
  const deadline = Date.now() + HEALTH_TIMEOUT_MS;
  let streak = 0;
  while (Date.now() < deadline) {
    // eslint-disable-next-line no-await-in-loop
    const ok = await check();
    streak = ok ? streak + 1 : 0;
    if (streak >= HEALTH_CONSECUTIVE) {
      log(`${label} ready (${HEALTH_CONSECUTIVE} consecutive checks)`);
      return true;
    }
    // eslint-disable-next-line no-await-in-loop
    await sleep(HEALTH_INTERVAL_MS);
  }
  return false;
}

async function taskkillPid(pid) {
  await new Promise((done) => {
    const killer = spawn('taskkill', ['/PID', String(pid), '/T', '/F'], {
      stdio: 'ignore',
      shell: true,
      windowsHide: true,
    });
    killer.on('exit', () => done());
    killer.on('error', () => done());
  });
}

async function killTree(pid) {
  if (!pid) return;
  try {
    if (isWindows) {
      await taskkillPid(pid);
    } else {
      try {
        process.kill(-pid, 'SIGTERM');
      } catch {
        /* group may already be gone */
      }
    }
  } catch {
    /* best-effort */
  }
}

// Windows-only: find PID(s) CURRENTLY LISTENING on a port this lifecycle owns.
// The PID node's `spawn(..., {shell:true})` returns is the wrapper cmd.exe/mvn
// shell it launched; mvn's spring-boot:run plugin forks a child JVM to run the
// app, and that shell can exit on its own once it hands off, orphaning the
// live java tree under a PID `taskkill /T` can no longer discover via the
// stale root. Killing whatever is actually bound to the owned port reaches the
// real listener regardless of how the intermediate process tree drifted.
//
// Deliberately `netstat -ano` with NO `-p tcp` filter: on this Windows build,
// `-p tcp` silently drops every IPv6 listener (confirmed: 33 LISTENING lines
// with the filter vs 52 without, and zero `[::` entries with it) — which is
// exactly how Vite's IPv6-only `[::1]:5173` bind escaped this lookup entirely.
// The regex below already anchors on a literal `TCP` prefix, so UDP rows are
// excluded without needing the netstat-level filter.
async function findWindowsPidsOnPort(port) {
  try {
    const { stdout } = await execAsync('netstat -ano');
    const pids = new Set();
    for (const line of stdout.split(/\r?\n/)) {
      const match = line.trim().match(/^TCP\s+\S*:(\d+)\s+\S+\s+LISTENING\s+(\d+)$/i);
      if (match && Number(match[1]) === port) {
        pids.add(match[2]);
      }
    }
    return [...pids];
  } catch {
    return [];
  }
}

async function killByPortWindows(port) {
  const pids = await findWindowsPidsOnPort(port);
  await Promise.all(pids.map(taskkillPid));
  return pids.length > 0;
}

// Ensure a set of harness-owned ports are free, actively re-killing whatever is
// still bound to them (Windows only — POSIX process-group SIGTERM above is
// reliable there). Never a fixed sleep: poll + kill until free or timeout.
async function ensurePortsFree(ports) {
  const deadline = Date.now() + PORT_RELEASE_TIMEOUT_MS;
  while (Date.now() < deadline) {
    // eslint-disable-next-line no-await-in-loop
    const states = await Promise.all(ports.map(isPortFree));
    if (states.every(Boolean)) return true;
    if (isWindows) {
      // eslint-disable-next-line no-await-in-loop
      await Promise.all(ports.map((port) => killByPortWindows(port)));
    }
    // eslint-disable-next-line no-await-in-loop
    await sleep(HEALTH_INTERVAL_MS);
  }
  return false;
}

async function waitPortsFree(ports) {
  return ensurePortsFree(ports);
}

async function startBackend() {
  log('starting backend (mvn spring-boot:run, dev profile, port 8080)');
  const child = startProcess('mvn', ['-q', '-f', join(REPO_ROOT, 'backend', 'pom.xml'), 'spring-boot:run'], {
    cwd: REPO_ROOT,
    env: { SPRING_PROFILES_ACTIVE: 'dev' },
  });
  const ready = await waitReady('backend', () => httpOk(BACKEND_HEALTH));
  if (!ready) {
    await killTree(child.pid);
    throw new Error('backend failed health gate');
  }
  return child.pid;
}

async function startFrontend() {
  log('starting frontend (vite dev, port 5173)');
  const child = startProcess('npm', ['--prefix', join(REPO_ROOT, 'frontend'), 'run', 'dev'], {
    cwd: REPO_ROOT,
  });
  const ready = await waitReady('frontend', () => httpOk(FRONTEND_URL));
  if (!ready) {
    await killTree(child.pid);
    throw new Error('frontend failed read-back gate');
  }
  return child.pid;
}

async function commandUp() {
  const existing = readState();
  if (existing) {
    log(`existing lifecycle ${existing.lifecycleId} recorded; tearing it down first`);
    await commandDown();
  }
  const backendFree = await isPortFree(BACKEND_PORT);
  const frontendFree = await isPortFree(FRONTEND_PORT);
  if (!backendFree || !frontendFree) {
    throw new Error(
      `required ports busy before start (backend ${BACKEND_PORT} free=${backendFree}, frontend ${FRONTEND_PORT} free=${frontendFree})`,
    );
  }
  const lifecycleId = `lc-${nowUtcId()}`;
  const backendPid = await startBackend();
  const frontendPid = await startFrontend();
  const state = {
    lifecycleId,
    startedAt: new Date().toISOString(),
    backend: { pid: backendPid, port: BACKEND_PORT, health: BACKEND_HEALTH },
    frontend: { pid: frontendPid, port: FRONTEND_PORT, baseUrl: FRONTEND_URL },
    baseUrl: FRONTEND_URL,
    ports: [BACKEND_PORT, FRONTEND_PORT],
  };
  writeState(state);
  log(`lifecycle id: ${lifecycleId}`);
  log(`base URL: ${FRONTEND_URL}`);
  log(`backend health: ${BACKEND_HEALTH}`);
  log('UP ok');
}

async function commandReset() {
  const state = readState();
  if (!state) throw new Error('no lifecycle recorded; run `up` first');
  log(`resetting lifecycle ${state.lifecycleId}: restarting backend for a fresh in-memory H2 + re-seeded admin`);
  await killTree(state.backend?.pid);
  const freed = await waitPortsFree([BACKEND_PORT]);
  if (!freed) throw new Error(`backend port ${BACKEND_PORT} not released during reset`);
  const backendPid = await startBackend();
  state.backend.pid = backendPid;
  state.resetAt = new Date().toISOString();
  writeState(state);
  // Read back the frontend too so we return only on a fully-healthy stack.
  const frontendOk = await waitReady('frontend', () => httpOk(FRONTEND_URL));
  if (!frontendOk) throw new Error('frontend read-back failed after reset');
  log('RESET ok');
}

async function commandDown() {
  const state = readState();
  if (!state) {
    log('no lifecycle recorded; nothing to tear down');
    return;
  }
  log(`tearing down lifecycle ${state.lifecycleId} (pids ${state.backend?.pid}, ${state.frontend?.pid})`);
  await killTree(state.frontend?.pid);
  await killTree(state.backend?.pid);
  const freed = await waitPortsFree(state.ports ?? [BACKEND_PORT, FRONTEND_PORT]);
  writeState({ ...state, stoppedAt: new Date().toISOString(), teardownPortsFree: freed });
  if (!freed) throw new Error('ports not released after teardown');
  log('DOWN ok');
}

function commandStatus() {
  const state = readState();
  if (!state) {
    log('no lifecycle recorded');
    return;
  }
  process.stdout.write(`${JSON.stringify(state, null, 2)}\n`);
}

async function main() {
  const command = process.argv[2];
  switch (command) {
    case 'up':
      await commandUp();
      break;
    case 'reset':
      await commandReset();
      break;
    case 'down':
      await commandDown();
      break;
    case 'status':
      commandStatus();
      break;
    default:
      process.stderr.write('usage: node e2e/scripts/lifecycle.mjs <up|reset|down|status>\n');
      process.exit(2);
  }
}

main().catch((error) => {
  process.stderr.write(`[lifecycle] ERROR: ${error.message}\n`);
  process.exit(1);
});
