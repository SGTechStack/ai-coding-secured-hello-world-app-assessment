/** The API's vocabulary, mirrored. Kept in one file so a backend change has one place to land. */

export type Role = "USER" | "ADMIN";

export type Session =
  | { authenticated: false }
  | { authenticated: true; username: string; role: Role };

export interface AccountSummary {
  id: string;
  username: string;
  email: string;
  role: Role;
  enabled: boolean;
  locked: boolean;
  createdAt: string;
}

export interface MessageResponse {
  message: string;
}

/**
 * The single error shape the API returns.
 *
 * Branch on `code`, never on `message`. The codes are a contract; the messages are prose that can be
 * reworded at any time, and a UI that pattern-matches on them breaks silently when someone does.
 */
export interface ApiErrorBody {
  code: string;
  message: string;
  fieldErrors?: Record<string, string>;
}
