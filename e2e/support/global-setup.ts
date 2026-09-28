import fs from 'node:fs';
import bcrypt from 'bcryptjs';
import { ApiClient } from './api-client';
import { env } from './env';
import { H2Probe, lit } from './h2-probe';

export interface BootstrapResult {
  /**
   * True if the configured app.admin.* credentials logged in successfully —
   * in this run, or in an earlier run against the same backend process
   * (after which the password was rotated).
   */
  bootstrapCredentialsAccepted: boolean;
  /** BCrypt hash of the seeded admin as first observed, before rotation. */
  seededPasswordHash?: string;
  /** bcrypt.compare(configured password, seeded hash). */
  seededHashMatchesConfiguredPassword?: boolean;
}

/**
 * Creates the "root admin" session every admin-related scenario uses to
 * promote its own freshly registered admin accounts. Reusing one stored
 * session (instead of logging the root admin in per worker) keeps us well
 * under app.security.max-concurrent-sessions.
 *
 * The seeded admin must change its password on first login
 * (ForcePasswordChangeFilter). This rotates it once to env.rotatedAdminPassword;
 * later runs against the same backend process log in with that instead.
 */
export default async function globalSetup(): Promise<void> {
  fs.mkdirSync(env.authDir, { recursive: true });
  const previous: BootstrapResult | undefined = fs.existsSync(env.bootstrapResultFile)
    ? JSON.parse(fs.readFileSync(env.bootstrapResultFile, 'utf8'))
    : undefined;

  const api = await ApiClient.create();
  const db = await H2Probe.connect();
  try {
    const [adminRow] = await db.query(
      `SELECT PASSWORD_HASH FROM USERS WHERE USERNAME = ${lit(env.bootstrapAdminUsername)}`,
    );
    if (!adminRow) throw new Error(`Bootstrap admin "${env.bootstrapAdminUsername}" was not seeded`);

    let result: BootstrapResult;
    const bootstrapLogin = await api.login(env.bootstrapAdminUsername, env.bootstrapAdminPassword);
    if (bootstrapLogin.status === 200) {
      const seededHash = adminRow.PASSWORD_HASH ?? '';
      result = {
        bootstrapCredentialsAccepted: true,
        seededPasswordHash: seededHash,
        seededHashMatchesConfiguredPassword: await bcrypt.compare(env.bootstrapAdminPassword, seededHash),
      };
      const change = await api.post('/api/auth/change-password', {
        currentPassword: env.bootstrapAdminPassword,
        newPassword: env.rotatedAdminPassword,
      });
      if (change.status !== 200) {
        throw new Error(`Rotating bootstrap admin password failed: ${change.status} ${change.text}`);
      }
    } else {
      await api.loginOrThrow(env.bootstrapAdminUsername, env.rotatedAdminPassword);
      // Same backend process as an earlier run: keep what that run observed.
      result = previous ?? { bootstrapCredentialsAccepted: false };
    }

    const hello = await api.hello();
    if (hello.status !== 200) throw new Error(`Root admin cannot reach /api/hello: ${hello.status} ${hello.text}`);
    if ((hello.json as { role?: string }).role !== 'ADMIN') throw new Error('Root admin session is not ADMIN');

    await api.ctx.storageState({ path: env.rootAdminStateFile });
    fs.writeFileSync(env.bootstrapResultFile, JSON.stringify(result, null, 2));
  } finally {
    await db.dispose();
    await api.dispose();
  }
}
