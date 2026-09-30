import { useQuery } from '@tanstack/react-query'
import { useEffect, useId, useState } from 'react'
import { Button } from '@/components/ui/button'
import { type DemoAccount, DEMO_ACCOUNTS_KEY, fetchDemoAccounts, refreshAfterMs } from '@/lib/auth/demoAccounts'

/**
 * The sign-in page's "Demo accounts" panel, for local demos. It renders only when the backend's dev-only endpoint
 * answers 200 with accounts; outside `dev` it renders nothing at all. Every value shown comes from that response.
 *
 * Button names come from their text (with a visually hidden suffix), never `aria-label`, so the sign-in form's
 * "Username" and "Password" labels stay the only labelled controls with those words.
 */
export function DemoAccountsPanel({ onUse }: { onUse: (username: string, password: string) => void }) {
  const headingId = useId()
  const [copied, setCopied] = useState('')
  const { data: accounts, dataUpdatedAt } = useQuery({
    queryKey: DEMO_ACCOUNTS_KEY,
    queryFn: fetchDemoAccounts,
    retry: false,
    staleTime: 0,
    refetchInterval: (query) => refreshAfterMs(query.state.data),
  })

  if (!accounts?.length) {
    return null
  }

  const copy = async (what: string, value: string) => {
    try {
      await navigator.clipboard.writeText(value)
      setCopied(`Copied ${what}.`)
    } catch {
      setCopied(`The ${what} could not be copied. Select it and copy it by hand.`)
    }
  }

  return (
    <section aria-labelledby={headingId} className="mt-8 flex flex-col gap-3 rounded-md border p-4">
      <h3 id={headingId} className="font-semibold">
        Demo accounts
      </h3>
      <p className="text-sm">Local development only. These accounts and their values are public.</p>
      {accounts.map((account) => (
        <DemoAccountEntry
          key={account.username}
          account={account}
          fetchedAt={dataUpdatedAt}
          onCopy={copy}
          onUse={onUse}
        />
      ))}
      <p aria-live="polite" className="text-sm">
        {copied}
      </p>
    </section>
  )
}

/** The whole seconds left until `expiresAt`, ticking once a second while `expiresAt` is set. */
function useSecondsLeft(expiresAt: number | undefined): number {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    if (expiresAt === undefined) return
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [expiresAt])
  return expiresAt === undefined ? 0 : Math.max(0, Math.ceil((expiresAt - now) / 1000))
}

function DemoAccountEntry({
  account,
  fetchedAt,
  onCopy,
  onUse,
}: {
  account: DemoAccount
  fetchedAt: number
  onCopy: (what: string, value: string) => Promise<void>
  onUse: (username: string, password: string) => void
}) {
  const { username, role, password, totp } = account
  const secondsLeft = useSecondsLeft(totp ? fetchedAt + totp.secondsRemaining * 1000 : undefined)
  return (
    <div className="flex flex-col gap-2 border-t pt-3">
      <h4 className="text-sm font-medium">
        {username} ({role === 'ADMIN' ? 'administrator' : 'user'})
      </h4>
      <dl className="grid grid-cols-[max-content_1fr_max-content] items-center gap-x-4 gap-y-1 text-sm">
        <dt>Username</dt>
        <dd>
          <code>{username}</code>
        </dd>
        <dd>
          <Button type="button" variant="outline" size="sm" onClick={() => onCopy(`${username}'s username`, username)}>
            Copy <span className="sr-only">{username}&apos;s username</span>
          </Button>
        </dd>
        <dt>Password</dt>
        {password ? (
          <>
            <dd>
              <code>{password}</code>
            </dd>
            <dd>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={() => onCopy(`${username}'s password`, password)}
              >
                Copy <span className="sr-only">{username}&apos;s password</span>
              </Button>
            </dd>
          </>
        ) : (
          <dd className="col-span-2">Changed since it was seeded, so not shown.</dd>
        )}
        {totp && (
          <>
            <dt>Code</dt>
            <dd>
              {/* Announced when it refreshes; the countdown stays outside the live region. */}
              <code aria-live="polite" aria-atomic="true">
                {totp.code}
              </code>{' '}
              <span className="text-muted-foreground">(new code in {secondsLeft} s)</span>
            </dd>
            <dd>
              <Button type="button" variant="outline" size="sm" onClick={() => onCopy(`${username}'s code`, totp.code)}>
                Copy <span className="sr-only">{username}&apos;s code</span>
              </Button>
            </dd>
          </>
        )}
      </dl>
      {password && (
        <div>
          <Button type="button" variant="secondary" size="sm" onClick={() => onUse(username, password)}>
            Fill in {username}
          </Button>
        </div>
      )}
    </div>
  )
}
