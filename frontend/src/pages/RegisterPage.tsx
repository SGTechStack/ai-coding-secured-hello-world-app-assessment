import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { apiRequest, type ApiResult } from '../api/client'
import { Field, type FieldError } from '../components/Field'
import { passwordRuleErrors } from '../components/passwordRules'

type FieldName = 'username' | 'email' | 'password'

/** Messages for the input fields the API names in `fields` on a `validation` error. */
const FIELD_MESSAGES: Record<FieldName, string> = {
  username: 'Use 3 to 32 letters and digits.',
  email: 'Enter a valid email address of at most 254 characters.',
  password: 'Enter a password.',
}

type Errors = {
  fields: Partial<Record<FieldName, string>>
  violations: string[]
  form?: string
}

const NO_ERRORS: Errors = { fields: {}, violations: [] }

/** Turns an API error into what the form shows. */
function errorsFor(result: Extract<ApiResult<unknown>, { ok: false }>): Errors {
  const problem = result.problem
  if (result.status === 429) {
    return { ...NO_ERRORS, form: 'Too many attempts. Please try again later.' }
  }
  switch (problem?.code) {
    case 'password_policy':
      // The alert announces the failure to screen readers; the rules are listed under the field.
      return {
        ...NO_ERRORS,
        violations: problem.violations ?? [],
        form: 'The password does not meet the password policy.',
      }
    case 'validation': {
      const fields: Errors['fields'] = {}
      for (const name of problem.fields ?? []) {
        if (name in FIELD_MESSAGES) fields[name as FieldName] = FIELD_MESSAGES[name as FieldName]
      }
      return { fields, violations: [], form: 'Please correct the highlighted fields.' }
    }
    case 'user_exist':
      return { ...NO_ERRORS, form: 'An Account with that username or email already exists.' }
    default:
      return { ...NO_ERRORS, form: 'Something went wrong. Please try again later.' }
  }
}

export function RegisterPage() {
  const navigate = useNavigate()
  const [values, setValues] = useState<Record<FieldName, string>>({ username: '', email: '', password: '' })
  const [errors, setErrors] = useState<Errors>(NO_ERRORS)
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setErrors(NO_ERRORS)
    try {
      const result = await apiRequest('/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(values),
      })
      if (result.ok) {
        navigate('/login', { state: { registered: true } })
        return
      }
      setErrors(errorsFor(result))
    } catch {
      setErrors({ ...NO_ERRORS, form: 'Something went wrong. Please try again later.' })
    }
    setSubmitting(false)
  }

  const passwordErrors: FieldError[] = passwordRuleErrors(errors.violations)
  if (errors.fields.password) passwordErrors.unshift({ key: 'field', message: errors.fields.password })

  return (
    <section>
      <h1>Register</h1>
      <form onSubmit={submit} noValidate>
        <Field
          name="username"
          label="Username"
          sensitivity="Sensitive Normal"
          autoComplete="username"
          value={values.username}
          errors={fieldErrors(errors.fields.username)}
          onChange={(username) => setValues({ ...values, username })}
        />
        <Field
          name="email"
          label="Email"
          sensitivity="Sensitive Normal"
          type="email"
          autoComplete="email"
          value={values.email}
          errors={fieldErrors(errors.fields.email)}
          onChange={(email) => setValues({ ...values, email })}
        />
        <Field
          name="password"
          label="Password"
          sensitivity="Sensitive High"
          type="password"
          autoComplete="new-password"
          value={values.password}
          errors={passwordErrors}
          onChange={(password) => setValues({ ...values, password })}
        />
        {errors.form && <p role="alert">{errors.form}</p>}
        <button type="submit" disabled={submitting}>
          Register
        </button>
      </form>
      <p>
        Already registered? <Link to="/login">Log in</Link>
      </p>
    </section>
  )
}

function fieldErrors(message: string | undefined): FieldError[] {
  return message ? [{ key: 'field', message }] : []
}
