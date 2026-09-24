import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'

import { ApiError, confirmPasswordReset } from '@/lib/api'
import { BrandMark } from '@/components/brand-mark'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

const PASSWORD_MIN_LENGTH = 12

export default function ResetPasswordPage() {
  // The stubbed email links here as /reset-password?token=<token>.
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') ?? ''
  const [password, setPassword] = useState('')
  const [done, setDone] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await confirmPasswordReset(token, password)
      setDone(true)
    } catch (err) {
      // Unknown/expired/spent tokens and policy violations all surface as the
      // server's problem+json detail — show it verbatim.
      setError(err instanceof ApiError ? err.message : 'Reset failed. Is the API up?')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-linear-to-b from-primary/10 via-background to-background p-6">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <BrandMark />
          <CardTitle className="text-2xl">Choose a new password</CardTitle>
          <CardDescription>
            Passwords must be at least {PASSWORD_MIN_LENGTH} characters. Your
            existing sessions are ended when the password changes.
          </CardDescription>
        </CardHeader>
        {!token ? (
          <CardContent className="flex flex-col gap-4">
            <div
              role="alert"
              className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive"
            >
              This reset link is missing its token — request a fresh one.
            </div>
          </CardContent>
        ) : done ? (
          <CardContent className="flex flex-col gap-4">
            <div
              role="status"
              className="rounded-md border border-emerald-500/40 bg-emerald-500/10 px-3 py-2 text-sm text-emerald-700 dark:text-emerald-400"
            >
              Password updated. Sign in with your new password.
            </div>
          </CardContent>
        ) : (
          <form onSubmit={onSubmit}>
            <CardContent className="flex flex-col gap-4">
              {error && (
                <div
                  role="alert"
                  className="rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive"
                >
                  {error}
                </div>
              )}
              <div className="flex flex-col gap-2">
                <Label htmlFor="password">New password</Label>
                <Input
                  id="password"
                  type="password"
                  autoComplete="new-password"
                  required
                  minLength={PASSWORD_MIN_LENGTH}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
              </div>
            </CardContent>
            <CardFooter className="flex-col items-stretch gap-3">
              <Button type="submit" disabled={submitting} className="w-full">
                {submitting ? 'Updating…' : 'Update password'}
              </Button>
            </CardFooter>
          </form>
        )}
        <CardFooter className="flex-col items-stretch gap-3">
          <p className="text-center text-sm text-muted-foreground">
            {done ? (
              <Link to="/login" className="font-medium text-primary hover:underline">
                Sign in
              </Link>
            ) : (
              <>
                Need a new link?{' '}
                <Link
                  to="/forgot-password"
                  className="font-medium text-primary hover:underline"
                >
                  Request one
                </Link>
              </>
            )}
          </p>
        </CardFooter>
      </Card>
    </main>
  )
}
