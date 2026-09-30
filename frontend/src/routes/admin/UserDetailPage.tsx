import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import {
  deleteUser,
  getUser,
  issuePasswordReset,
  listRoles,
  setUserEnabled,
  setUserRole,
  unlockUser,
  type UserDetail,
} from '../../api/endpoints'
import { useAuth } from '../../auth/useAuth'
import { ErrorBanner, NoticeBanner } from '../../components/Feedback'
import { describeError } from '../../components/errorText'
import { textField } from '../../components/formFields'

/**
 * One account, and every administrator action on it (stories 1.14, 1.16–1.20).
 *
 * **Self-actions are not hidden, they are attempted and refused.** The server's `SelfActionGuard` answers
 * 403 `SELF_ACTION_NOT_ALLOWED`, the interceptor deliberately does not log the caller out for it, and the
 * message appears in place. Hiding the buttons instead would make the guard untested from the UI's side
 * and would leave an administrator wondering why the controls vanished. The one concession is the note
 * below, so the refusal is not a surprise.
 */
export function UserDetailPage(): ReactNode {
  const { userId = '' } = useParams()
  const navigate = useNavigate()
  const { user: self } = useAuth()

  const [target, setTarget] = useState<UserDetail | null>(null)
  const [roles, setRoles] = useState<string[]>([])
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const load = useCallback(async () => {
    try {
      setTarget(await getUser(userId))
      setRoles(await listRoles())
    } catch (failure) {
      setError(describeError(failure, 'That account could not be loaded.'))
    }
  }, [userId])

  useEffect(() => {
    void load()
  }, [load])

  /** Every action shares this shape: clear both banners, act, reload, report. */
  async function run(action: () => Promise<void>, success: string): Promise<void> {
    setError(null)
    setNotice(null)
    setBusy(true)
    try {
      await action()
      await load()
      setNotice(success)
    } catch (failure) {
      setError(describeError(failure, 'That action failed.'))
    } finally {
      setBusy(false)
    }
  }

  if (!target) {
    return (
      <section className="card">
        <ErrorBanner message={error} />
        {!error && <p className="muted">Loading…</p>}
      </section>
    )
  }

  const isSelf = self?.username === target.username

  return (
    <section className="card">
      <h1>{target.username}</h1>
      <p className="muted">
        <Link to="/admin/users">Back to users</Link>
      </p>
      <ErrorBanner message={error} />
      <NoticeBanner message={notice} />
      {isSelf && (
        <NoticeBanner message="This is your own account. Disable, role change, unlock, reset and delete are refused on it." />
      )}

      <dl className="detail">
        <dt>Email</dt>
        <dd>{target.email}</dd>
        <dt>Role</dt>
        <dd>{target.role}</dd>
        <dt>Status</dt>
        <dd>{target.enabled ? 'enabled' : `disabled since ${target.disabledAt ?? 'unknown'}`}</dd>
        <dt>Forced password change</dt>
        <dd>{target.requirePasswordChange ? 'yes' : 'no'}</dd>
        <dt>Failed sign-ins</dt>
        <dd>{target.failedLoginAttempts}</dd>
        <dt>Locked until</dt>
        {/* Lock state is derived from locked_until alone -- there is no boolean beside it, and a past
            timestamp means the lock has already lifted with no sweep and no write. */}
        <dd>{target.lockedUntil ?? 'not locked'}</dd>
        <dt>Last sign-in</dt>
        <dd>{target.lastLoginAt ?? 'never'}</dd>
        <dt>Created</dt>
        <dd>{target.createdAt}</dd>
      </dl>

      <div className="actions">
        <button
          type="button"
          disabled={busy}
          onClick={() =>
            void run(
              () => setUserEnabled(target.id, !target.enabled),
              target.enabled
                ? 'Account disabled. Its sessions were ended.'
                : 'Account enabled. The user must choose a new password.',
            )
          }
        >
          {target.enabled ? 'Disable' : 'Enable'}
        </button>

        <button
          type="button"
          disabled={busy || target.lockedUntil === null}
          onClick={() => void run(() => unlockUser(target.id), 'Lock cleared and the counter reset.')}
        >
          Unlock
        </button>

        <button
          type="button"
          disabled={busy}
          onClick={() =>
            void run(async () => {
              const issued = await issuePasswordReset(target.id)
              // Shown because there is no mail server in this application. It does not lift a lock: the
              // unlock endpoint is the only remedy for that (Std:131 over Priv:441-442).
              setNotice(
                issued.token
                  ? `Reset issued. Token: ${issued.token}`
                  : 'Reset issued. The link was sent to the user.',
              )
            }, 'Reset issued.')
          }
        >
          Issue password reset
        </button>

        <button
          type="button"
          className="danger"
          disabled={busy}
          onClick={() =>
            void run(async () => {
              await deleteUser(target.id)
              void navigate('/admin/users', { replace: true })
            }, 'Account deleted.')
          }
        >
          Delete
        </button>
      </div>

      <form
        onSubmit={(event) => {
          event.preventDefault()
          const chosen = textField(new FormData(event.currentTarget), 'role')
          void run(
            () => setUserRole(target.id, chosen),
            'Role changed. The account must sign in again.',
          )
        }}
      >
        <label htmlFor="role">Role</label>
        <select id="role" name="role" defaultValue={target.role}>
          {roles.map((role) => (
            <option key={role} value={role}>
              {role}
            </option>
          ))}
        </select>
        <button type="submit" disabled={busy}>
          Change role
        </button>
      </form>
    </section>
  )
}
