import { useQuery } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { DEMO_ACCOUNTS_KEY, fetchDemoAccounts, refreshAfterMs, useSecondsLeft, wasSent } from '@/lib/auth/demoAccounts'
import { fetchProfile, PROFILE_KEY } from '@/lib/auth/session'

/**
 * The dev-only "Demo code" hint in a code prompt: the signed-in demo administrator's current code, as the sign-in
 * page's demo panel shows it, with a button that fills the field (never submits). It renders only when the self-read is
 * already cached and the backend's dev-only endpoint answers 200 with a code for that very username; outside `dev`,
 * or for any other account, it renders nothing. Every value shown comes from that response.
 *
 * A code this tab has just sent can never verify again, so it is held back with the wait for the next one.
 */
export function DemoCodeHint({ onFill }: { onFill: (code: string) => void }) {
  // Read from the cache only: the prompts that show the hint already hold the self-read.
  const { data: profile } = useQuery({ queryKey: PROFILE_KEY, queryFn: fetchProfile, enabled: false })
  const { data: accounts, dataUpdatedAt } = useQuery({
    queryKey: DEMO_ACCOUNTS_KEY,
    queryFn: fetchDemoAccounts,
    enabled: profile !== undefined,
    retry: false,
    staleTime: 0,
    refetchInterval: (query) => refreshAfterMs(query.state.data),
  })
  const totp = accounts?.find((account) => account.seeded && account.username === profile?.username)?.totp
  const secondsLeft = useSecondsLeft(totp ? dataUpdatedAt + totp.secondsRemaining * 1000 : undefined)

  if (!totp) {
    return null
  }
  const sent = wasSent(totp.code)
  return (
    <div role="group" aria-label="Demo code" className="flex flex-col gap-2 rounded-md border p-3 text-sm">
      <p>Local development only: the demo administrator&apos;s current code.</p>
      <p>
        {/* Announced when it changes; the countdown stays outside the live region. */}
        <span aria-live="polite" aria-atomic="true">
          {sent ? (
            'That code was just used and cannot be used again.'
          ) : (
            <>
              Demo code <code>{totp.code}</code>
            </>
          )}
        </span>{' '}
        <span className="text-muted-foreground">
          {sent ? `Next code in ${secondsLeft} s.` : `(new code in ${secondsLeft} s)`}
        </span>
      </p>
      {!sent && (
        <div>
          <Button type="button" variant="secondary" size="sm" onClick={() => onFill(totp.code)}>
            Fill in demo code
          </Button>
        </div>
      )}
    </div>
  )
}
