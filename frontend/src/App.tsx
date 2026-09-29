import { useEffect, useState } from 'react';
import { fetchHealth } from './api/client';

type ConnectionState =
  | { kind: 'loading' }
  | { kind: 'connected'; status: string; time: string }
  | { kind: 'error'; message: string };

export default function App() {
  const [state, setState] = useState<ConnectionState>({ kind: 'loading' });

  useEffect(() => {
    let cancelled = false;
    fetchHealth()
      .then((health) => {
        if (!cancelled) {
          setState({ kind: 'connected', status: health.status, time: health.time });
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          const message = error instanceof Error ? error.message : 'Unknown error';
          setState({ kind: 'error', message });
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <main>
      <h1>Secured Hello World</h1>
      <section aria-live="polite">
        {state.kind === 'loading' && <p>Checking backend connectivity…</p>}
        {state.kind === 'connected' && (
          <p>
            Backend reachable — status <strong>{state.status}</strong> at {state.time}
          </p>
        )}
        {state.kind === 'error' && (
          <p role="alert">Backend unreachable: {state.message}</p>
        )}
      </section>
    </main>
  );
}
