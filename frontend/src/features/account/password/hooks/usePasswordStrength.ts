import { useEffect, useState } from 'react';
import { estimatePasswordStrength, type StrengthScore } from '../model/password-strength';

/** Guidance-only strength of `password`, or null until rated. A late score for an older password is never shown. */
export function usePasswordStrength(password: string, username: string, email: string): StrengthScore | null {
  const [rated, setRated] = useState<{ password: string; score: StrengthScore } | null>(null);
  useEffect(() => {
    if (!password) return;
    let current = true;
    estimatePasswordStrength(password, [username, email])
      .then((score) => {
        if (current) setRated({ password, score });
      })
      .catch(() => {
        if (current) setRated(null);
      });
    return () => {
      current = false;
    };
  }, [password, username, email]);
  return rated && rated.password === password ? rated.score : null;
}
