export type Role = 'USER' | 'ADMIN';

/** Safe projection of an account — never contains passwordHash (Story 48). */
export interface UserResponse {
  id: string;
  username: string;
  email: string;
  role: Role;
  enabled: boolean;
  createdAt: string;
}

/** Returned by POST /api/auth/login on success. */
export interface AuthResponse {
  username: string;
  role: string;
}

/** RFC 9457 ProblemDetail — every error response from the backend. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
}
