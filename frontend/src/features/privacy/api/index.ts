import { API_BASE_URL, parseErrorResponse } from "@/common/api/http";

export interface PrivacyNotice {
  controller: string;
  contact: string;
  lawfulBasis: string;
  dataCollected: string[];
  retention: string[];
  rights: string[];
  lastUpdated: string;
}

/**
 * Fetches the privacy notice. Unauthenticated, because the person deciding
 * whether to hand over an email address has not registered yet.
 */
export async function fetchPrivacyNotice(signal?: AbortSignal): Promise<PrivacyNotice> {
  const response = await fetch(`${API_BASE_URL}/api/privacy-notice`, {
    method: "GET",
    credentials: "include",
    signal,
  });

  if (!response.ok) {
    return parseErrorResponse(response);
  }

  return response.json() as Promise<PrivacyNotice>;
}
