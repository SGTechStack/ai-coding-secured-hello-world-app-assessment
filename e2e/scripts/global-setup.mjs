import { spawnSync } from 'node:child_process';
import { resolve } from 'node:path';

const run = (command) => {
  const result = spawnSync(process.execPath, [resolve(import.meta.dirname, 'lifecycle.mjs'), command], { stdio: 'inherit' });
  if (result.status !== 0) throw new Error(`E2E lifecycle failed to ${command}`);
};

/** Starts the lifecycle; Playwright runs the returned function as the global teardown. */
export default async function globalSetup() {
  run('start');
  return () => run('stop');
}
