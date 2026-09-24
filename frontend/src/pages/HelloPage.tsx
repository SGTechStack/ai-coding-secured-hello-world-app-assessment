import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'

import { useAuth } from '@/auth/auth-context'
import { getHello } from '@/lib/api'
import { BrandMark } from '@/components/brand-mark'
import { Button, buttonVariants } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'

type GreetingState =
  | { status: 'loading' }
  | { status: 'ok'; message: string }
  | { status: 'error' }

/** Protected landing page — proves session auth + personalized greeting. */
export default function HelloPage() {
  const { user, signOut } = useAuth()
  const [greeting, setGreeting] = useState<GreetingState>({ status: 'loading' })
  const [signingOut, setSigningOut] = useState(false)

  useEffect(() => {
    let cancelled = false
    getHello()
      .then((body) => {
        if (!cancelled) {
          setGreeting({ status: 'ok', message: body.message })
        }
      })
      .catch(() => {
        if (!cancelled) {
          setGreeting({ status: 'error' })
        }
      })
    return () => {
      cancelled = true
    }
  }, [])

  async function onSignOut() {
    setSigningOut(true)
    // Ends the server-side session and drops local auth state; RequireAuth
    // then routes to /login — no explicit navigate needed.
    await signOut()
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-linear-to-b from-primary/10 via-background to-background p-6">
      <Card className="w-full max-w-md">
        <CardHeader>
          <BrandMark />
          <CardTitle className="text-2xl">Protected hello</CardTitle>
          <CardDescription>
            Signed in as <span className="font-medium text-foreground">{user?.username}</span>{' '}
            ({user?.role})
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div
            role="status"
            aria-live="polite"
            className="rounded-lg border border-border bg-muted px-4 py-3 font-mono text-sm"
          >
            {greeting.status === 'loading' && 'Contacting /api/hello…'}
            {greeting.status === 'ok' && greeting.message}
            {greeting.status === 'error' && 'Could not reach the API on localhost:8080'}
          </div>
        </CardContent>
        <CardFooter className="flex-col items-stretch gap-3">
          {user?.role === 'ADMIN' && (
            <Link
              to="/admin"
              className={cn(buttonVariants({ variant: 'secondary' }), 'w-full')}
            >
              Admin panel
            </Link>
          )}
          <Button
            variant="outline"
            className="w-full"
            onClick={onSignOut}
            disabled={signingOut}
          >
            {signingOut ? 'Signing out…' : 'Sign out'}
          </Button>
        </CardFooter>
      </Card>
    </main>
  )
}
