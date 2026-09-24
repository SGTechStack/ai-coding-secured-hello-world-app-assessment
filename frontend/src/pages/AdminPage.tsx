import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'

import { useAuth } from '@/auth/auth-context'
import {
  ApiError,
  deleteAdminUser,
  listAdminUsers,
  setUserEnabled,
  setUserRole,
  type AdminUser,
} from '@/lib/api'
import { BrandMark } from '@/components/brand-mark'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'

type LoadState =
  | { status: 'loading' }
  | { status: 'ok' }
  | { status: 'error'; message: string }

/**
 * The admin user-management panel. Lists every account and offers the three
 * ratified mutations — enable/disable, role change, delete. The acting
 * admin's own row is disabled client-side (the API rejects self-targeting
 * anyway — this is UX, not the guard).
 */
export default function AdminPage() {
  const { user } = useAuth()
  const [users, setUsers] = useState<AdminUser[]>([])
  const [loadState, setLoadState] = useState<LoadState>({ status: 'loading' })
  const [actionError, setActionError] = useState<string | null>(null)
  /** Row currently mutating — its buttons disable while in flight. */
  const [busyId, setBusyId] = useState<number | null>(null)
  /** Bump to re-run the list fetch (the Retry control). */
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    let cancelled = false
    listAdminUsers()
      .then((rows) => {
        if (!cancelled) {
          setUsers(rows)
          setLoadState({ status: 'ok' })
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setLoadState({
            status: 'error',
            message:
              err instanceof ApiError
                ? err.message
                : 'Could not reach the API.',
          })
        }
      })
    return () => {
      cancelled = true
    }
  }, [reloadKey])

  async function run(id: number, action: () => Promise<AdminUser | void>) {
    setActionError(null)
    setBusyId(id)
    try {
      const updated = await action()
      if (updated) {
        setUsers((rows) => rows.map((row) => (row.id === id ? updated : row)))
      } else {
        // Delete — drop the row.
        setUsers((rows) => rows.filter((row) => row.id !== id))
      }
    } catch (err) {
      setActionError(
        err instanceof ApiError ? err.message : 'The change failed. Try again.',
      )
    } finally {
      setBusyId(null)
    }
  }

  function onDelete(row: AdminUser) {
    if (!window.confirm(`Delete ${row.username}? This cannot be undone.`)) {
      return
    }
    void run(row.id, () => deleteAdminUser(row.id))
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-linear-to-b from-primary/10 via-background to-background p-6">
      <Card className="w-full max-w-4xl">
        <CardHeader>
          <BrandMark />
          <CardTitle className="text-2xl">User management</CardTitle>
          <CardDescription>
            Every account, with status, role, and deletion controls. You cannot
            modify your own account.
          </CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {actionError && (
            <div
              role="alert"
              className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive"
            >
              {actionError}
            </div>
          )}
          {loadState.status === 'loading' && (
            <p className="text-sm text-muted-foreground">Loading users…</p>
          )}
          {loadState.status === 'error' && (
            <div className="flex items-center justify-between gap-3">
              <p className="text-sm text-destructive">{loadState.message}</p>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setReloadKey((key) => key + 1)}
              >
                Retry
              </Button>
            </div>
          )}
          {loadState.status === 'ok' && (
            <div className="overflow-x-auto rounded-lg border border-border">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="border-b border-border bg-muted text-xs uppercase tracking-wider text-muted-foreground">
                    <th className="px-4 py-3 font-medium">User</th>
                    <th className="px-4 py-3 font-medium">Role</th>
                    <th className="px-4 py-3 font-medium">Status</th>
                    <th className="px-4 py-3 font-medium">Created</th>
                    <th className="px-4 py-3 text-right font-medium">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {users.map((row) => {
                    const isSelf = row.username === user?.username
                    const busy = busyId === row.id
                    return (
                      <tr
                        key={row.id}
                        className="border-b border-border transition-colors last:border-0 hover:bg-muted/50"
                      >
                        <td className="px-4 py-3">
                          <div className="font-medium">
                            {row.username}
                            {isSelf && (
                              <span className="ml-2 text-xs text-muted-foreground">
                                (you)
                              </span>
                            )}
                          </div>
                          <div className="text-xs text-muted-foreground">
                            {row.email}
                          </div>
                        </td>
                        <td className="px-4 py-3">
                          <span
                            className={
                              row.role === 'ADMIN'
                                ? 'inline-flex items-center rounded-full bg-primary/10 px-2.5 py-0.5 text-xs font-semibold text-primary'
                                : 'inline-flex items-center rounded-full bg-muted px-2.5 py-0.5 text-xs font-medium text-muted-foreground'
                            }
                          >
                            {row.role}
                          </span>
                        </td>
                        <td className="px-4 py-3">
                          <span
                            className={
                              row.enabled
                                ? 'inline-flex items-center rounded-full bg-emerald-500/10 px-2.5 py-0.5 text-xs font-medium text-emerald-700 dark:text-emerald-400'
                                : 'inline-flex items-center rounded-full bg-destructive/10 px-2.5 py-0.5 text-xs font-medium text-destructive'
                            }
                          >
                            {row.enabled ? 'Enabled' : 'Disabled'}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-muted-foreground">
                          {new Date(row.createdAt).toLocaleDateString()}
                        </td>
                        <td className="px-4 py-3">
                          <div className="flex justify-end gap-2">
                            <Button
                              variant="outline"
                              size="sm"
                              disabled={isSelf || busy}
                              onClick={() =>
                                void run(row.id, () =>
                                  setUserEnabled(row.id, !row.enabled),
                                )
                              }
                            >
                              {row.enabled ? 'Disable' : 'Enable'}
                            </Button>
                            <Button
                              variant="outline"
                              size="sm"
                              disabled={isSelf || busy}
                              onClick={() =>
                                void run(row.id, () =>
                                  setUserRole(
                                    row.id,
                                    row.role === 'ADMIN' ? 'USER' : 'ADMIN',
                                  ),
                                )
                              }
                            >
                              {row.role === 'ADMIN' ? 'Revoke admin' : 'Make admin'}
                            </Button>
                            <Button
                              variant="destructive"
                              size="sm"
                              disabled={isSelf || busy}
                              onClick={() => onDelete(row)}
                            >
                              Delete
                            </Button>
                          </div>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
          <p className="text-sm text-muted-foreground">
            <Link to="/" className="font-medium text-primary hover:underline">
              Back to hello
            </Link>
          </p>
        </CardContent>
      </Card>
    </main>
  )
}
