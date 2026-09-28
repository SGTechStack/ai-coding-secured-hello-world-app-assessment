import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const e2eRoot = path.resolve(here, '..');

/**
 * Environment knobs for the suite. Defaults match the dev profile in
 * backend/src/main/resources/application*.yml and frontend/vite.config.ts.
 */
export const env = {
  frontendUrl: process.env.E2E_FRONTEND_URL ?? 'http://localhost:3000',
  backendUrl: process.env.E2E_BACKEND_URL ?? 'http://localhost:8080',

  /** Bootstrap admin credentials from app.admin.* (Story 12). */
  bootstrapAdminUsername: process.env.E2E_BOOTSTRAP_ADMIN_USERNAME ?? 'admin',
  bootstrapAdminPassword: process.env.E2E_BOOTSTRAP_ADMIN_PASSWORD ?? 'ChangeMe123456!',
  /**
   * The seeded admin is forced to change its password on first login; the
   * global setup rotates it to this value so later runs against the same
   * backend process can still log in.
   */
  rotatedAdminPassword: process.env.E2E_ADMIN_PASSWORD ?? 'E2e-Rotated-Admin-Pass-2026',

  /** Lockout policy from app.security.lockout.* */
  lockoutMaxAttempts: Number(process.env.E2E_LOCKOUT_MAX_ATTEMPTS ?? 5),
  lockoutDurationMinutes: Number(process.env.E2E_LOCKOUT_DURATION_MINUTES ?? 15),

  /** H2 console (dev profile only) used as a read/write probe into persisted state. */
  h2JdbcUrl: process.env.E2E_H2_JDBC_URL ?? 'jdbc:h2:mem:securedhelloworld',
  h2User: process.env.E2E_H2_USER ?? 'sa',
  h2Password: process.env.E2E_H2_PASSWORD ?? '',

  /** Backend structured (ECS) log file, written by the dev profile. */
  backendLogFile: process.env.E2E_BACKEND_LOG ?? path.resolve(e2eRoot, '../backend/logs/spring.log'),

  skipWebServer: process.env.E2E_SKIP_WEBSERVER === '1',

  authDir: path.resolve(e2eRoot, '.auth'),
  rootAdminStateFile: path.resolve(e2eRoot, '.auth/root-admin.json'),
  bootstrapResultFile: path.resolve(e2eRoot, '.auth/bootstrap-result.json'),
};
