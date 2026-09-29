import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from './AuthContext';
import { api, ApiError } from '../lib/api';
import { Notice } from '../components/Notice';

type Mode = 'login' | 'register' | 'forgot' | 'reset';
const content = {
  login: {
    title: 'Welcome back.',
    description: 'Sign in to your own little corner of the world.',
    action: 'Sign in',
  },
  register: {
    title: 'Make yourself at home.',
    description: 'A few details, and you’re ready to begin.',
    action: 'Create account',
  },
  forgot: {
    title: 'Let’s get you back in.',
    description: 'Enter your email and we’ll help you reset your password.',
    action: 'Send reset link',
  },
  reset: {
    title: 'A fresh start.',
    description: 'Choose a new password to keep your account secure.',
    action: 'Update password',
  },
};

export function AuthPage({ mode }: { mode: Mode }) {
  const auth = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [error, setError] = useState('');
  const [fields, setFields] = useState<Record<string, string>>({});
  const [message, setMessage] = useState(
    (location.state as { message?: string } | null)?.message || '',
  );
  const [busy, setBusy] = useState(false);
  const [token] = useState(() => new URLSearchParams(location.hash.slice(1)).get('token') || '');
  const newPassword = mode === 'register' || mode === 'reset';

  useEffect(() => {
    if (mode === 'reset' && location.hash) {
      // Keep the bearer secret in memory; remove it from browser history immediately.
      navigate(location.pathname, { replace: true });
    }
  }, [mode, location.hash, location.pathname, navigate]);

  if (auth.user && (mode === 'login' || mode === 'register')) return <Navigate to="/app" replace />;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');
    setFields({});
    setMessage('');
    const data = new FormData(event.currentTarget);
    const username = String(data.get('username') || '');
    const email = String(data.get('email') || '');
    const password = String(data.get('password') || '');
    if (newPassword && password !== data.get('confirm')) {
      setError('Passwords do not match.');
      return;
    }
    if (newPassword && new TextEncoder().encode(password).length > 72) {
      setError(
        'Password is too long. Use at most 72 UTF-8 bytes (some characters use more than one byte).',
      );
      return;
    }
    setBusy(true);
    try {
      if (mode === 'login') {
        await auth.signIn(username, password);
        navigate('/app', { replace: true });
      } else if (mode === 'register') {
        await api.post('/auth/register', { username, email, password });
        navigate('/login', { state: { message: 'Your account is ready. Sign in to say hello.' } });
      } else if (mode === 'forgot') {
        const result = await api.post<{ message: string }>('/auth/password-reset/request', {
          email,
        });
        setMessage(result.message);
      } else {
        const result = await api.post<{ message: string }>('/auth/password-reset/confirm', {
          token,
          password,
        });
        window.dispatchEvent(new Event('session-expired'));
        navigate('/login', { state: { message: result.message } });
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : 'Please try again.');
      if (caught instanceof ApiError) setFields(caught.fields);
    } finally {
      setBusy(false);
    }
  }

  function field(
    name: string,
    label: string,
    type: string,
    autoComplete: string,
    extra: object = {},
  ) {
    return (
      <div className="field">
        <label htmlFor={name}>{label}</label>
        <input
          id={name}
          name={name}
          type={type}
          autoComplete={autoComplete}
          required
          aria-invalid={!!fields[name]}
          aria-describedby={fields[name] ? `${name}-error` : undefined}
          {...extra}
        />
        {fields[name] && (
          <span id={`${name}-error`} className="field-error">
            {fields[name]}
          </span>
        )}
      </div>
    );
  }

  return (
    <div className="auth-layout">
      <aside className="welcome-panel" aria-label="About Hello">
        <span className="eyebrow">GOOD TO HAVE YOU HERE</span>
        <h1>
          A little hello.
          <br />A secure beginning.
        </h1>
        <p>
          Your space starts with a simple sign in.
          <br />
          We’ll take care of the welcome.
        </p>
        <div className="hello-art" aria-hidden="true">
          <span>h</span>
          <span>e</span>
          <span>l</span>
          <span>l</span>
          <span>o</span>
          <i>✳</i>
        </div>
        <div className="panel-foot">
          <span className="small-dot" /> A thoughtful start to your day.
        </div>
      </aside>
      <section className="auth-form-panel" aria-labelledby="form-title">
        <div className="form-content">
          <span className="eyebrow">{mode === 'login' ? 'YOUR ACCOUNT' : 'LET’S BEGIN'}</span>
          <h2 id="form-title">{content[mode].title}</h2>
          <p className="muted">{content[mode].description}</p>
          <Notice message={message} />
          <Notice
            message={
              error ||
              (mode === 'reset' && !token
                ? 'This reset link is incomplete. Request a new link below.'
                : '')
            }
            error
          />
          <form onSubmit={submit} aria-busy={busy}>
            <fieldset disabled={busy || (mode === 'reset' && !token)}>
              {(mode === 'login' || mode === 'register') &&
                field('username', 'Username', 'text', 'username', {
                  maxLength: 50,
                  ...(mode === 'register' ? { minLength: 3, pattern: '[a-zA-Z0-9_]{3,50}' } : {}),
                })}
              {(mode === 'register' || mode === 'forgot') &&
                field('email', 'Email address', 'email', 'email', { maxLength: 254 })}
              {mode !== 'forgot' &&
                field(
                  'password',
                  'Password',
                  'password',
                  newPassword ? 'new-password' : 'current-password',
                  {
                    maxLength: 72,
                    ...(newPassword ? { minLength: 12, 'aria-describedby': 'password-hint' } : {}),
                  },
                )}
              {newPassword && (
                <p id="password-hint" className="field-hint">
                  At least 12 characters. Choose something unique to you.
                </p>
              )}
              {newPassword &&
                field('confirm', 'Confirm password', 'password', 'new-password', {
                  minLength: 12,
                  maxLength: 72,
                })}
              {mode === 'login' && (
                <div className="forgot-link">
                  <Link to="/forgot-password">Forgot password?</Link>
                </div>
              )}
              <button className="button primary full-width" type="submit">
                {busy ? 'Please wait…' : content[mode].action}
                <span aria-hidden="true">↗</span>
              </button>
            </fieldset>
          </form>
          <p className="form-switch">
            {mode === 'login' ? (
              <>
                New around here? <Link to="/register">Create an account</Link>
              </>
            ) : (
              <Link to="/login">← Back to sign in</Link>
            )}
          </p>
          {mode === 'reset' && (
            <p className="form-switch">
              <Link to="/forgot-password">Request a new reset link</Link>
            </p>
          )}
          <div className="privacy-note">
            <span aria-hidden="true">◇</span> Your account. Your own space.
          </div>
        </div>
      </section>
    </div>
  );
}
