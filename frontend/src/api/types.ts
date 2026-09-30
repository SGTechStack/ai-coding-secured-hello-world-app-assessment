export type Role = 'USER' | 'ADMIN';

export interface SessionUser {
  authenticated: boolean;
  username: string | null;
  role: Role | null;
}

export const ANONYMOUS: SessionUser = { authenticated: false, username: null, role: null };

export interface UserSummary {
  id: string;
  username: string;
  email: string;
  role: Role;
  enabled: boolean;
  createdAt: string;
}

export interface MessageResponse {
  message: string;
}

export interface HelloResponse {
  message: string;
}

export interface RegisterInput {
  username: string;
  email: string;
  password: string;
}

export interface LoginInput {
  username: string;
  password: string;
}
