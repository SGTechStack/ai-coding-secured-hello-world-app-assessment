import { useEffect, useState } from 'react';
import { ApiError } from '../api/client';
import { getHello } from '../api/hello';
import { useAuth } from '../contexts/AuthContext';

export function HelloPage() {
  const { user } = useAuth();
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getHello()
      .then(setMessage)
      .catch((err: unknown) => {
        setError(err instanceof ApiError ? err.detail : 'Failed to load greeting.');
      });
  }, []);

  if (error) {
    return (
      <div role="alert" className="form-banner form-banner-error" style={{ maxWidth: 520 }}>
        <span aria-hidden="true">✕ </span>{error}
      </div>
    );
  }

  if (!message) {
    return (
      <div className="loading-page" aria-label="Loading">
        <div className="loading-spinner" aria-hidden="true" />
        Loading…
      </div>
    );
  }

  return (
    <div className="card hello-card">
      <h1 className="hello-greeting">{message}</h1>
      <p style={{ color: 'var(--color-text-muted)', fontSize: 'var(--font-size-sm)' }}>
        You are successfully authenticated.
      </p>
      <div className="hello-meta">
        <span className={`badge ${user?.role === 'ADMIN' ? 'badge-admin' : 'badge-user'}`}>
          {user?.role ?? 'USER'}
        </span>
        <span style={{ color: 'var(--color-text-muted)', fontSize: 'var(--font-size-sm)' }}>
          {user?.email}
        </span>
      </div>
    </div>
  );
}
