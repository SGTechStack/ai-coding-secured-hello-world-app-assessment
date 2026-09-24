import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'

import { ApiError, requestPasswordReset } from '@/lib/api'
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

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [sentMessage, setSentMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      // The server always answers with the same generic sentence — show it
      // verbatim rather than confirming the account exists.
      setSentMessage(await requestPasswordReset(email))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Request failed. Is the API up?')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-linear-to-b from-primary/10 via-background to-background p-6">
      <Card className="w-full max-w-sm">
        <CardHeader>
          <BrandMark />
          <CardTitle className="text-2xl">Reset password</CardTitle>
          <CardDescription>
            Enter the email on your account and we&apos;ll send a reset link.
          </CardDescription>
        </CardHeader>
        {sentMessage ? (
          <CardContent className="flex flex-col gap-4">
            <div
              role="status"
              className="rounded-md border border-emerald-500/40 bg-emerald-500/10 px-3 py-2 text-sm text-emerald-700 dark:text-emerald-400"
            >
              {sentMessage}
            </div>
            <p className="text-sm text-muted-foreground">
              There&apos;s no real mail server in this build — the link is logged by the
              API&apos;s stubbed EmailService.
            </p>
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
                <Label htmlFor="email">Email</Label>
                <Input
                  id="email"
                  type="email"
                  autoComplete="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                />
              </div>
            </CardContent>
            <CardFooter className="flex-col items-stretch gap-3">
              <Button type="submit" disabled={submitting} className="w-full">
                {submitting ? 'Sending…' : 'Send reset link'}
              </Button>
            </CardFooter>
          </form>
        )}
        <CardFooter className="flex-col items-stretch gap-3">
          <p className="text-center text-sm text-muted-foreground">
            Remembered it?{' '}
            <Link to="/login" className="font-medium text-primary hover:underline">
              Sign in
            </Link>
          </p>
        </CardFooter>
      </Card>
    </main>
  )
}
