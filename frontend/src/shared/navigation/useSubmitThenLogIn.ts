import { useState } from 'react'
import { useNavigate } from 'react-router'
import { SERVER_UNAVAILABLE } from '../api/http.ts'
import { loginStatus } from './loginStatus.ts'

/** How a form request ended: done, rejected with the server's message, or no usable answer. */
export type SubmissionResult =
  { status: 'done' } | { status: 'rejected'; message: string } | { status: 'unavailable' }

/**
 * Form state for pages that send the visitor to /login with a status message once their request
 * succeeds (registration, password reset). While a request runs `submitting` is true; a failure shows
 * the server's message, or the unavailable message, and re-enables the form.
 */
export function useSubmitThenLogIn(successMessage: string) {
  const navigate = useNavigate()
  const [error, setError] = useState<string>()
  const [submitting, setSubmitting] = useState(false)

  async function submit(request: () => Promise<SubmissionResult>) {
    setSubmitting(true)
    setError(undefined)
    const result = await request()
    if (result.status === 'done') {
      await navigate('/login', { replace: true, state: loginStatus(successMessage) })
      return
    }
    setError(result.status === 'rejected' ? result.message : SERVER_UNAVAILABLE)
    setSubmitting(false)
  }

  return { error, setError, submitting, submit }
}
