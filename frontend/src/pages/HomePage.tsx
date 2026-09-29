import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { Notice } from '../components/Notice';
import { api } from '../lib/api';

export function HomePage() {
  const { user } = useAuth();
  const [greeting, setGreeting] = useState('');
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    api
      .get<string>('/hello')
      .then((value) => {
        if (active) setGreeting(value);
      })
      .catch((caught: unknown) => {
        if (active)
          setError(caught instanceof Error ? caught.message : 'Unable to load your greeting.');
      });
    return () => {
      active = false;
    };
  }, []);

  return (
    <section className="workspace">
      <span className="eyebrow">A SPACE OF YOUR OWN</span>
      <div className="greeting-card">
        <span className="status-pill">
          <span className="small-dot" /> You’re signed in
        </span>
        <Notice message={error} error />
        <h1>{greeting || (error ? 'Welcome.' : 'Getting your hello…')}</h1>
        <p>You made it. Take a breath, settle in, and make yourself at home.</p>
        <span className="greeting-star" aria-hidden="true">
          ✳
        </span>
      </div>
      <div className="account-grid">
        <div className="detail-card">
          <span className="eyebrow">YOUR ACCOUNT</span>
          <h2>{user?.username}</h2>
          <p>{user?.email}</p>
          <span className="role-label">{user?.role === 'ADMIN' ? 'Administrator' : 'Member'}</span>
        </div>
        <div className="detail-card">
          <span className="eyebrow">NEXT STEPS</span>
          <h2>Keep your space yours.</h2>
          <p>Need a fresh password? We can send a reset link to your email.</p>
          <Link to="/forgot-password">
            Reset password <span aria-hidden="true">↗</span>
          </Link>
        </div>
      </div>
      {user?.role === 'ADMIN' && (
        <Link className="admin-callout" to="/admin">
          <div>
            <span className="eyebrow">ADMINISTRATION</span>
            <h2>A welcome for everyone.</h2>
            <p>Review members and manage access to the workspace.</p>
          </div>
          <span>Manage users ↗</span>
        </Link>
      )}
    </section>
  );
}
