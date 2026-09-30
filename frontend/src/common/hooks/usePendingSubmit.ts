import { useEffect, useState } from 'react';

/** A submit stays visibly pending at least this long, so fast responses never flash or reveal timing. */
const MINIMUM_PENDING_MS = 400;

export const delay = (ms: number) => new Promise<void>((resolve) => window.setTimeout(resolve, ms));

/** Cycles 0 → 1 → 2 every MINIMUM_PENDING_MS while active; back to 0 once inactive, ready for the next submit. */
function useLoadingFrame(active: boolean): number {
  const [frame, setFrame] = useState(0);
  useEffect(() => {
    if (!active) return;
    const interval = window.setInterval(() => setFrame((current) => (current + 1) % 3), MINIMUM_PENDING_MS);
    return () => {
      window.clearInterval(interval);
      setFrame(0);
    };
  }, [active]);
  return frame;
}

/**
 * Pending state for a form submit. `run` marks the form pending, performs the request, and resolves no sooner than
 * MINIMUM_PENDING_MS after it started, with the failure mapped by `toRejection` or null. The form stays pending until
 * `stop`.
 */
export function usePendingSubmit() {
  const [pending, setPending] = useState(false);
  const frame = useLoadingFrame(pending);

  const run = async <R>(request: () => Promise<unknown>, toRejection: (error: unknown) => R): Promise<R | null> => {
    setPending(true);
    const startedAt = Date.now();
    let rejection: R | null = null;
    try {
      await request();
    } catch (error) {
      rejection = toRejection(error);
    }
    await delay(Math.max(0, MINIMUM_PENDING_MS - (Date.now() - startedAt)));
    return rejection;
  };

  return { pending, frame, run, stop: () => setPending(false) };
}
