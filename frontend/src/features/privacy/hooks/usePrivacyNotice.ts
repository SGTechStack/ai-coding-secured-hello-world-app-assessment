import { useState } from "react";

import { useAbortEffect } from "@/common/hooks/useAbortEffect";
import { isAbortError } from "@/common/api/http";
import { fetchPrivacyNotice, type PrivacyNotice } from "../api";

/**
 * Fetches the privacy notice once on mount. Resolves to `null` both while
 * loading and if the fetch fails — callers render nothing in either case (see
 * {@link PrivacyNoticeSummary}), so there is no separate error state to carry.
 */
export function usePrivacyNotice(): PrivacyNotice | null {
  const [notice, setNotice] = useState<PrivacyNotice | null>(null);

  useAbortEffect((signal) => {
    fetchPrivacyNotice(signal)
      .then(setNotice)
      .catch((error: unknown) => {
        if (isAbortError(error)) return;
        setNotice(null);
      });
  }, []);

  return notice;
}
