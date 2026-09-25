import { ApiError } from "../api/client";

/** For a request that got no usable answer: a network error, a 5xx, or an unexpected refusal. */
export const UNAVAILABLE =
  "Unable to connect to the server. Please try again later.";

/** For a `429`: this network is throttled, which says nothing about what the user typed. */
export const THROTTLED = "Too many attempts. Please try again later.";

type BannerOptions = {
  /** What a `401` means on this form; without it a `401` is `UNAVAILABLE`. */
  unauthorized?: string;
  /**
   * Show the API's own message for any other `4xx` (the API writes those to be shown); otherwise
   * such a refusal is `UNAVAILABLE`.
   */
  showRejections?: boolean;
};

/** A form's banner text for a failed submission that no field of the form can show. */
export function bannerMessage(
  error: Error,
  { unauthorized, showRejections = false }: BannerOptions = {},
): string {
  if (!(error instanceof ApiError)) return UNAVAILABLE;
  if (error.kind === "throttled") return THROTTLED;
  if (error.kind === "unauthorized" && unauthorized) return unauthorized;
  if (error.kind === "rejected" && showRejections) return error.message;
  return UNAVAILABLE;
}
