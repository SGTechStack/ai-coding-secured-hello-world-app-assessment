import { redirect } from '@tanstack/react-router';

export const SESSION_EXPIRED_EVENT = 'session:expired';
export const RETURN_TO_KEY = 'demo:return-to';

/** Role name (as sent by /api/v1/me) that unlocks the Admin pages. */
export const ADMIN_ROLE = 'ADMIN';

export function requireRole(roles: string[], role: string): void {
  if (!roles.includes(role)) {
    throw redirect({ to: '/forbidden' });
  }
}
