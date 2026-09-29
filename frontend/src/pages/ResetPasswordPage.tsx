import { useEffect, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { confirmPasswordReset, type ConfirmPasswordResetResult } from '../api/auth'
import { Field, type FieldError } from '../components/Field'
import { passwordRuleErrors } from '../components/passwordRules'

type Failure = Extract<ConfirmPasswordResetResult, { ok: false }>

const GENERIC_ERROR = 'Something went wrong. Please try again later.'

const FAILURE_MESSAGES: Record<Failure['reason'], string> = {
  token_invalid: 'This reset link is invalid or has expired. Request a new one.',
  password_policy: 'The new password does not meet the password policy.',
  password_history: 'The new password was used recently. Choose a password other than your last 3.',
  validation: 'Enter a new password.',
  rate_limited: 'Too many attempts. Please try again later.',
  error: GENERIC_ERROR,
}

type Errors = { form?: string; violations: FieldError[] }

const NO_ERRORS: Errors = { violations: [] }

/** The Reset Token from the URL fragment, e.g. `#token=abc`; empty when there is none. */
function tokenFromHash(): string {
  const match = /#token=(.+)$/.exec(window.location.hash)
  return match ? decodeURIComponent(match[1]) : ''
}

/**
 * Reset-password screen: reads the Reset Token from the URL fragment and then removes it from the
 * address bar (story 103), so it is never left in browser history. Success ends every Session of the
 * Account, so it goes to the login page with a message, like Password Change.
 */
export function ResetPasswordPage() {
  const navigate = useNavigate()
  const [token] = useState(tokenFromHash)
  const [newPassword, setNewPassword] = useState('')
  const [errors, setErrors] = useState<Errors>(NO_ERRORS)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (window.location.hash) {
      window.history.replaceState(null, '', window.location.pathname + window.location.search)
    }
  }, [])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setErrors(NO_ERRORS)
    try {
      const result = await confirmPasswordReset(token, newPassword)
      if (result.ok) {
        navigate('/login', { state: { resetCompleted: true } })
        return
      }
      setErrors({
        form: FAILURE_MESSAGES[result.reason],
        violations: result.reason === 'password_policy' ? passwordRuleErrors(result.violations) : [],
      })
    } catch {
      setErrors({ ...NO_ERRORS, form: GENERIC_ERROR })
    }
    setSubmitting(false)
  }

  return (
    <section>
      <h1>Reset password</h1>
      <form onSubmit={submit} noValidate>
        <Field
          name="newPassword"
          label="New password"
          type="password"
          autoComplete="new-password"
          value={newPassword}
          errors={errors.violations}
          onChange={setNewPassword}
        />
        {errors.form && <p role="alert">{errors.form}</p>}
        <button type="submit" disabled={submitting}>
          Reset password
        </button>
      </form>
      <p>
        <Link to="/login">Back to login</Link>
      </p>
    </section>
  )
}
