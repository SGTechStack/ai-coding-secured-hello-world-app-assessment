import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { api, ApiError, ErrorCode } from '../api/client';
import { PasswordRules } from './passwordRules';
import { throttledMessage } from './throttle';

type Field = 'username' | 'email' | 'password';

export function RegisterPage() {
  const navigate = useNavigate();
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<Field, string[]>>>({});
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setSubmitting(true);
    setError(null);
    setFieldErrors({});
    try {
      await api.register({
        username: String(form.get('username')),
        email: String(form.get('email')),
        password: String(form.get('password')),
      });
      // Registration never logs the Visitor in.
      navigate('/login', { state: { registered: true } });
    } catch (e) {
      if (e instanceof ApiError && e.code === ErrorCode.validationFailed && e.problem.errors) {
        // A field can break several rules at once; show every one.
        const errors: Partial<Record<Field, string[]>> = {};
        for (const { field, message } of e.problem.errors) {
          (errors[field as Field] ??= []).push(message);
        }
        setFieldErrors(errors);
      } else if (e instanceof ApiError && e.code === ErrorCode.userExist) {
        setError('That username or email is already registered.');
      } else if (e instanceof ApiError && e.code === ErrorCode.serviceUnavailable) {
        // The breach check couldn't be reached, and no password is accepted unchecked.
        setError('Passwords can’t be checked right now. Please try again in a few minutes.');
      } else {
        setError(throttledMessage(e) ?? 'Registration failed. Please try again.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  const field = (name: Field, label: string, type: string, autoComplete: string, describedBy?: string) => (
    <label>
      {label}
      <input
        name={name}
        type={type}
        autoComplete={autoComplete}
        required
        aria-invalid={Boolean(fieldErrors[name])}
        aria-describedby={describedBy}
      />
      {fieldErrors[name]?.map((message) => (
        <span key={message} role="alert">
          {message}
        </span>
      ))}
    </label>
  );

  return (
    <main>
      <h1>Register</h1>
      <form onSubmit={onSubmit}>
        {field('username', 'Username', 'text', 'username')}
        <p>3 to 32 letters, digits, dots, underscores or hyphens.</p>
        {field('email', 'Email', 'email', 'email')}
        {field('password', 'Password', 'password', 'new-password', 'password-rules')}
        <PasswordRules id="password-rules" />
        {error && <p role="alert">{error}</p>}
        <button type="submit" disabled={submitting}>
          Register
        </button>
      </form>
      <p>
        Already registered? <Link to="/login">Log in</Link>
      </p>
    </main>
  );
}
