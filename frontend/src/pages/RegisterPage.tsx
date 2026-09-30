import { useState, type ChangeEvent, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { register } from '../api/authApi';
import { messageFor } from '../api/errors';
import {
  LIMITS,
  validateEmail,
  validateFirstName,
  validateNewPassword,
  validateUsername,
} from '../validation';
import './RegisterPage.css';

interface FieldErrors {
  username?: string;
  email?: string;
  firstName?: string;
  password?: string;
}

interface FormState {
  username: string;
  email: string;
  firstName: string;
  password: string;
}

const INITIAL_FORM: FormState = { username: '', email: '', firstName: '', password: '' };

export default function RegisterPage() {
  const [form, setForm] = useState<FormState>(INITIAL_FORM);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [banner, setBanner] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const navigate = useNavigate();

  function handleChange(field: keyof FormState) {
    return (event: ChangeEvent<HTMLInputElement>) => {
      setForm((previous) => ({ ...previous, [field]: event.target.value }));
      setFieldErrors((previous) => (previous[field] ? { ...previous, [field]: undefined } : previous));
      setBanner(null);
    };
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const candidate: FieldErrors = {
      username: validateUsername(form.username),
      email: validateEmail(form.email),
      firstName: validateFirstName(form.firstName),
      password: validateNewPassword(form.password),
    };
    const errors = Object.fromEntries(
      Object.entries(candidate).filter(([, message]) => message !== undefined),
    ) as FieldErrors;

    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }

    setFieldErrors({});
    setBanner(null);
    setIsSubmitting(true);

    try {
      await register(form);
      navigate('/login', { replace: true, state: { notice: 'registered' } });
    } catch (error) {
      setBanner(messageFor(error));
      setIsSubmitting(false);
    }
  }

  return (
    <main className="register-page">
      <form className="register-form" onSubmit={handleSubmit} noValidate>
        <div className="register-heading">
          <h1>Create an account</h1>
          <p className="register-subtitle">Register to access the protected app</p>
        </div>

        {banner && (
          <p role="alert" className="register-banner">
            {banner}
          </p>
        )}

        <div className="form-field">
          <label htmlFor="username">Username</label>
          <input
            id="username"
            name="username"
            type="text"
            autoComplete="username"
            maxLength={LIMITS.usernameMax}
            value={form.username}
            disabled={isSubmitting}
            onChange={handleChange('username')}
            aria-invalid={Boolean(fieldErrors.username)}
            aria-describedby={fieldErrors.username ? 'username-error' : undefined}
          />
          {fieldErrors.username && (
            <p id="username-error" className="field-error">
              {fieldErrors.username}
            </p>
          )}
        </div>

        <div className="form-field">
          <label htmlFor="email">Email</label>
          <input
            id="email"
            name="email"
            type="email"
            autoComplete="email"
            maxLength={LIMITS.emailMax}
            value={form.email}
            disabled={isSubmitting}
            onChange={handleChange('email')}
            aria-invalid={Boolean(fieldErrors.email)}
            aria-describedby={fieldErrors.email ? 'email-error' : undefined}
          />
          {fieldErrors.email && (
            <p id="email-error" className="field-error">
              {fieldErrors.email}
            </p>
          )}
        </div>

        <div className="form-field">
          <label htmlFor="firstName">First name</label>
          <input
            id="firstName"
            name="firstName"
            type="text"
            autoComplete="given-name"
            maxLength={LIMITS.firstNameMax}
            value={form.firstName}
            disabled={isSubmitting}
            onChange={handleChange('firstName')}
            aria-invalid={Boolean(fieldErrors.firstName)}
            aria-describedby={fieldErrors.firstName ? 'firstName-error' : undefined}
          />
          {fieldErrors.firstName && (
            <p id="firstName-error" className="field-error">
              {fieldErrors.firstName}
            </p>
          )}
        </div>

        <div className="form-field">
          <label htmlFor="password">Password</label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete="new-password"
            maxLength={LIMITS.passwordInputMax}
            value={form.password}
            disabled={isSubmitting}
            onChange={handleChange('password')}
            aria-invalid={Boolean(fieldErrors.password)}
            aria-describedby={fieldErrors.password ? 'password-error' : undefined}
          />
          {fieldErrors.password && (
            <p id="password-error" className="field-error">
              {fieldErrors.password}
            </p>
          )}
        </div>

        <button type="submit" disabled={isSubmitting}>
          {isSubmitting ? 'Creating account...' : 'Create account'}
        </button>

        <p className="register-footer">
          Already have an account? <Link to="/login">Log in</Link>
        </p>
      </form>
    </main>
  );
}
