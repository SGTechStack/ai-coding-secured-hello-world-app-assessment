import { useEffect, useState } from 'react';
import { api } from '../api.js';

export default function Home() {
  const [greeting, setGreeting] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    api.hello().then(setGreeting).catch((e) => setError(e.message));
  }, []);

  // Rendered as a React text node, so the value is escaped.
  return (
    <div className="hero">
      <h1 className={error ? 'error' : undefined}>{error || greeting}</h1>
      {!error && <p>You are signed in with a secure server-side session.</p>}
    </div>
  );
}
