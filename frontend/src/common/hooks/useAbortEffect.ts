import { useEffect } from "react";

/**
 * Runs an effect that owns an {@link AbortController}, aborting it on
 * cleanup.
 *
 * Real cancellation rather than an `isMounted` flag: aborting also tears
 * down the in-flight request instead of merely ignoring its response, which
 * matters under StrictMode's deliberate double-invocation of effects in
 * development.
 *
 * `deps` is forwarded to the underlying `useEffect` as-is, so the same rules
 * apply — pass a stable, exhaustive dependency list.
 */
export function useAbortEffect(effect: (signal: AbortSignal) => void, deps: React.DependencyList) {
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => {
    const controller = new AbortController();
    effect(controller.signal);
    return () => controller.abort();
  }, deps);
}
