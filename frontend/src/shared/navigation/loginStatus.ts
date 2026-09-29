/** Router navigation state carrying a one-off message for the login page, e.g. after registering. */
interface LoginStatusState {
  loginStatus: string
}

export function loginStatus(message: string): LoginStatusState {
  return { loginStatus: message }
}

export function readLoginStatus(state: unknown): string | undefined {
  if (typeof state !== 'object' || state === null || !('loginStatus' in state)) return undefined
  const message = (state as LoginStatusState).loginStatus
  return typeof message === 'string' ? message : undefined
}
