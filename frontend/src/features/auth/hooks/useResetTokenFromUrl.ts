import { useEffect, useState } from "react";

import { clearResetTokenFromUrl, hasResetTokenInUrl, readResetTokenFromUrl } from "../lib/resetToken";

/**
 * Captures the password reset token from the URL fragment on first render,
 * then strips it from the address bar as a one-shot read.
 *
 * The state is seeded from a lazy initialiser so the URL is sampled once per
 * mount rather than on every render, and always before the effect below
 * erases it.
 *
 * This belongs in an effect rather than the render body: mutating browser
 * history is a side effect, and the render phase can be re-entered or
 * abandoned by the scheduler.
 */
export function useResetTokenFromUrl() {
  const [resetToken, setResetToken] = useState<string | null>(readResetTokenFromUrl);

  useEffect(() => {
    if (hasResetTokenInUrl()) {
      clearResetTokenFromUrl();
    }
  }, []);

  return [resetToken, setResetToken] as const;
}
