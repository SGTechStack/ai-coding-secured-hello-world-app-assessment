import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { changePassword, type PasswordChangeResult } from '../api/auth'
import { useAuth } from '../auth/useAuth'
import { Field, type FieldError } from '../components/Field'
import { LogoutButton } from '../components/LogoutButton'
import { passwordRuleErrors } from '../components/passwordRules'

type Failure = Extract<PasswordChangeResult, { ok: false }>

const GENERIC_ERROR = 'Something went wrong. Please try again later.'

const FAILURE_MESSAGES: Record<Failure['reason'], string> = {
  current_password_invalid: 'The current password is incorrect.',
  password_policy: 'The new password does not meet the password policy.',
  password_history: 'The new password was used recently. Choose a password other than your last 3.',
  validation: 'Enter your current password and a new password.',
  error: GENERIC_ERROR,
}

type Errors = { form?: string; violations: FieldError[] }

const NO_ERRORS: Errors = { violations: [] }

/**
 * Password Change for the logged-in Account holder. Success ends every Session, this one included,
 * so the holder goes to the login page with a message and logs in with the new password.
 *
 * When the Account's password must be changed, this is the only screen it can reach: the way back is
 * replaced by a logout button, and a notice says why (stories 108 and 110).
 */
export function PasswordChangePage() {
  const auth = useAuth()
  const required = auth.state.kind === 'authenticated' && auth.state.account.passwordChangeRequired
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [errors, setErrors] = useState<Errors>(NO_ERRORS)
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setErrors(NO_ERRORS)
    try {
      const result = await changePassword(currentPassword, newPassword)
      if (result.ok) {
        // The route guard sends the now-anonymous caller to the login screen, which shows the message.
        auth.passwordChanged()
        return
      }
      setErrors({
        form: FAILURE_MESSAGES[result.reason],
        violations: result.reason === 'password_policy' ? passwordRuleErrors(result.violations) : [],
      })
    } catch {
      setErrors({ ...NO_ERRORS, form: GENERIC_ERROR })
    }
    // Only the current password is cleared; the new one stays so the holder can fix policy errors.
    setCurrentPassword('')
    setSubmitting(false)
  }

  return (
    <section>
      <h1>Change password</h1>
      {required && (
        <p role="status">
          Your password must be changed before you can do anything else. Choose a new password, then log in again.
        </p>
      )}
      <form onSubmit={submit} noValidate>
        <Field
          name="currentPassword"
          label="Current password"
          sensitivity="Sensitive High"
          type="password"
          autoComplete="current-password"
          value={currentPassword}
          onChange={setCurrentPassword}
        />
        <Field
          name="newPassword"
          label="New password"
          sensitivity="Sensitive High"
          type="password"
          autoComplete="new-password"
          value={newPassword}
          errors={errors.violations}
          onChange={setNewPassword}
        />
        {errors.form && <p role="alert">{errors.form}</p>}
        <button type="submit" disabled={submitting}>
          Change password
        </button>
      </form>
      {required ? (
        <LogoutButton />
      ) : (
        <p>
          <Link to="/">Back</Link>
        </p>
      )}
    </section>
  )
}
