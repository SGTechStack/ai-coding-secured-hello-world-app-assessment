import { ApiError } from "../api/client";

/**
 * Turns whatever a request threw into something worth showing a person.
 *
 * The network case is the one that matters. A backend that is not running produces a `TypeError:
 * Failed to fetch`, and so does a CORS misconfiguration — showing either verbatim tells the user
 * nothing and sends a developer looking in the wrong place.
 */
export function toMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiError) {
    return error.message;
  }
  if (error instanceof TypeError) {
    return "Could not reach the server. Check that the API is running and try again.";
  }
  return fallback;
}

export function fieldErrorsOf(error: unknown): Record<string, string> {
  return error instanceof ApiError ? error.fieldErrors : {};
}
