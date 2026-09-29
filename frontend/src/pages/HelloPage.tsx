import { useEffect, useState } from 'react';
import { getHello } from '../api/hello';
import { ApiError } from '../api/client';

export function HelloPage() {
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getHello()
      .then(setMessage)
      .catch((err: unknown) => {
        if (err instanceof ApiError) {
          setError(err.detail);
        } else {
          setError('Failed to load greeting.');
        }
      });
  }, []);

  if (error) return <p role="alert" style={{ color: 'red' }}>{error}</p>;
  if (!message) return <p>Loading…</p>;

  return (
    <div>
      <h1>{message}</h1>
      <p>You are authenticated.</p>
    </div>
  );
}
