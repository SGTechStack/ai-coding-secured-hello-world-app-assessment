import { useState } from "react";

import { useAbortEffect } from "@/common/hooks/useAbortEffect";
import { isAbortError } from "@/common/api/http";
import { fetchGreeting } from "../api";

export type GreetingState =
  | { kind: "loading" }
  | { kind: "success"; greeting: string }
  | { kind: "error"; message: string };

/**
 * Fetches the protected greeting once on mount.
 *
 * Empty deps, not keyed on the session: the request's only input is the
 * session cookie, and the caller unmounts on logout, so a new session always
 * arrives as a fresh mount.
 */
export function useGreeting(): GreetingState {
  const [greeting, setGreeting] = useState<GreetingState>({ kind: "loading" });

  useAbortEffect((signal) => {
    fetchGreeting(signal)
      .then((text) => setGreeting({ kind: "success", greeting: text }))
      .catch((error: unknown) => {
        if (isAbortError(error)) return;
        setGreeting({
          kind: "error",
          message: error instanceof Error ? error.message : "Could not load greeting",
        });
      });
  }, []);

  return greeting;
}
