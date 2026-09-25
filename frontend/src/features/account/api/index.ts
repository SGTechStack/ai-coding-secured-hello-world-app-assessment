import { API_BASE_URL, CONFIRM_PASSWORD_HEADER, csrfHeader, parseErrorResponse } from "@/common/api/http";

/**
 * Calls the protected greeting endpoint. Throws {@link ApiError} with a
 * 401-flavoured message if there is no valid session.
 */
export async function fetchGreeting(signal?: AbortSignal): Promise<string> {
  const response = await fetch(`${API_BASE_URL}/api/hello`, {
    method: "GET",
    credentials: "include",
    signal,
  });

  if (!response.ok) {
    return parseErrorResponse(response);
  }

  return response.text();
}

/**
 * Downloads everything the backend holds about the signed-in account.
 *
 * Returns the parsed document so the caller can both show a summary and offer it
 * as a file. The server also sets `Content-Disposition`, but a `fetch` ignores
 * that — saving the file is the client's job here, done in
 * {@link triggerAccountDataDownload}.
 */
export async function exportMyAccountData(): Promise<unknown> {
  const response = await fetch(`${API_BASE_URL}/api/account/export`, {
    method: "GET",
    credentials: "include",
  });

  if (!response.ok) {
    return parseErrorResponse(response);
  }

  return response.json() as Promise<unknown>;
}

/**
 * Saves an exported document to the user's disk.
 *
 * The object URL is revoked immediately after the click. Without that the blob —
 * a full copy of the user's personal data — stays resident in the tab for as long
 * as the page is open.
 */
export function triggerAccountDataDownload(data: unknown, filename = "my-account-data.json"): void {
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
  const url = URL.createObjectURL(blob);

  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.click();

  URL.revokeObjectURL(url);
}

/**
 * Erases the signed-in account and its data. Irreversible, so the password is
 * required.
 *
 * Throws {@link ApiError} for a missing or wrong confirmation (403), or when the
 * caller is the last enabled admin (409) — the system cannot be left with nobody
 * able to administer it.
 */
export async function eraseMyAccount(confirmationPassword: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/account`, {
    method: "DELETE",
    credentials: "include",
    headers: {
      ...(await csrfHeader()),
      [CONFIRM_PASSWORD_HEADER]: confirmationPassword,
    },
  });

  if (!response.ok) {
    return parseErrorResponse(response);
  }
}
