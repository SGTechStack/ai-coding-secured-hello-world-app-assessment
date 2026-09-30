import { spawn, spawnSync } from 'node:child_process';
import { mkdir, open, readFile, rm, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '../..');
const runtime = resolve(root, 'artifacts/e2e/runtime');
const stateFile = resolve(runtime, 'lifecycle.json');
const baseUrl = process.env.E2E_BASE_URL ?? 'https://127.0.0.1:8443';
const port = new URL(baseUrl).port;
// The local backend serves a generated self-signed certificate; this script only health-checks it.
process.env.NODE_TLS_REJECT_UNAUTHORIZED = '0';
const state = async () => JSON.parse(await readFile(stateFile, 'utf8'));
const healthy = async () => { try { return (await fetch(`${baseUrl}/csrf`)).ok; } catch { return false; } };
// `spring-boot:run` rebuilds the frontend (npm ci + vite build) before the JVM starts, which takes minutes, not seconds.
const startupTimeoutMs = Number(process.env.E2E_STARTUP_TIMEOUT_MS ?? 5 * 60_000);
const shutdownTimeoutMs = 60_000;
const waitFor = async (expected, timeoutMs, abortReason = () => undefined) => {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (await healthy() === expected) return;
    const reason = abortReason();
    if (reason) throw new Error(reason);
    await new Promise((delay) => setTimeout(delay, 1_000));
  }
  throw new Error(`Health did not become ${expected} within ${Math.round(timeoutMs / 1_000)}s`);
};
const listenerPid = () => {
  const result = spawnSync('netstat.exe', ['-ano'], { encoding: 'utf8' });
  const match = result.stdout.match(new RegExp(`(?:0\\.0\\.0\\.0|\\[::\\]):${port}\\s+\\S+\\s+LISTENING\\s+(\\d+)`));
  if (!match) throw new Error(`The E2E lifecycle did not own a listener on port ${port}`);
  return Number(match[1]);
};

if (process.argv[2] === 'start') {
  await mkdir(runtime, { recursive: true });
  try { const existing = await state(); if (await healthy()) { console.log(`E2E lifecycle ${existing.id}: ${baseUrl}`); process.exit(0); } } catch {}
  const id = `e2e-${crypto.randomUUID()}`;
  const child = spawn('D:\\projects\\builder-day\\maven\\bin\\mvn.cmd', ['spring-boot:run', '-Dspring-boot.run.profiles=local'], {
    cwd: resolve(root, 'backend'), detached: true, stdio: 'ignore', windowsHide: true, shell: true,
    env: {
      ...process.env, JAVA_HOME: 'D:\\projects\\builder-day\\jdk', PATH: `D:\\projects\\builder-day\\node;${process.env.PATH}`,
      // Short enough that the expired-link scenario (tests/reset-password) need not wait 30 minutes.
      PASSWORD_RESET_TOKEN_LIFETIME: process.env.PASSWORD_RESET_TOKEN_LIFETIME ?? '45s',
      // Admin bootstrap seeds the first Admin on this throwaway H2 database so scenarios can sign in as an Admin
      // (e2e/README.md). Fixed defaults that satisfy the username rules and PasswordPolicy; the E2E_ADMIN_* overrides
      // let a run change them. This is the one place a fixed test password is allowed, because it applies only to the
      // local harness backend and never to a deployed environment (spec / ADR 0009 §7).
      ADMIN_USERNAME: process.env.E2E_ADMIN_USERNAME ?? 'e2eadmin',
      ADMIN_PASSWORD: process.env.E2E_ADMIN_PASSWORD ?? 'Str0ng!Passw0rd-e2e',
    },
  });
  let exitCode;
  child.on('exit', (code) => { exitCode = code; });
  child.unref();
  // Fail fast when Maven exits (build error, port in use) instead of waiting out the whole timeout.
  await waitFor(true, startupTimeoutMs, () => exitCode === undefined ? undefined
    : `Backend exited with code ${exitCode} before becoming healthy; see backend/logs/spring.log`);
  await writeFile(stateFile, JSON.stringify({ id, pid: child.pid, listenerPid: listenerPid(), baseUrl }));
  console.log(`E2E lifecycle ${id}: ${baseUrl}`);
} else if (process.argv[2] === 'stop') {
  let current; try { current = await state(); } catch { process.exit(0); }
  spawnSync('taskkill.exe', ['/PID', String(current.listenerPid ?? current.pid), '/T', '/F'], { stdio: 'ignore' });
  await waitFor(false, shutdownTimeoutMs);
  await rm(stateFile, { force: true });
  console.log(`Stopped E2E lifecycle ${current.id}`);
} else throw new Error('Use start or stop');
