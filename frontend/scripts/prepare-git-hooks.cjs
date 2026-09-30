const { spawnSync } = require('node:child_process');
const path = require('node:path');

if (process.env.CI) {
  process.exit(0);
}

const repoRoot = path.resolve(__dirname, '..', '..');

if (process.platform === 'win32') {
  const ps1 = path.join(repoRoot, 'scripts', 'setup-hooks.ps1');
  const result = spawnSync('powershell', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', ps1], {
    stdio: 'inherit',
    cwd: repoRoot,
  });
  process.exit(result.status ?? 1);
} else {
  const script = path.join(repoRoot, 'scripts', 'setup-hooks.sh');
  const result = spawnSync('bash', [script], {
    stdio: 'inherit',
    cwd: repoRoot,
  });
  process.exit(result.status ?? 1);
}
