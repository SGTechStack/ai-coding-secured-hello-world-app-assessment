import { problemOf, statusOf } from '../api/client'
import { messageFor } from '../api/errors'

/**
 * One place errors are turned into text, so no screen invents its own wording for a shared condition.
 *
 * Rate limiting and the last-administrator conflict get named messages because the server's `detail` is
 * written for an operator reading a log, not for someone staring at a form.
 */
export function describeError(error: unknown, fallback: string): string {
  const status = statusOf(error)
  const problem = problemOf(error)

  if (status === 429) {
    return 'Too many attempts. Wait a minute and try again.'
  }
  if (problem?.code === 'LAST_USER_MANAGER') {
    return 'That change would leave no enabled user manager, so it was refused.'
  }
  if (problem?.code === 'SELF_ACTION_NOT_ALLOWED') {
    return 'You cannot perform this action on your own account.'
  }
  if (status === 401) {
    // Every authentication failure is the same generic 401 by design: unknown username, wrong password,
    // locked account and disabled account are indistinguishable (Std:247). This message must not
    // speculate about which one it was -- doing so would reintroduce the oracle the backend removed.
    return 'Those credentials were not accepted.'
  }
  return messageFor(problem, fallback)
}
