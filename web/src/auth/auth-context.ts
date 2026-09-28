import { createContext } from "react";
import type { Role, Session } from "../api/types";

export interface AuthState {
  /** Undefined only while the first session check is in flight. */
  session: Session | undefined;
  isLoading: boolean;
  username: string | null;
  role: Role | null;
  isAuthenticated: boolean;
  isAdmin: boolean;
  logIn: (input: { username: string; password: string }) => Promise<void>;
  logOut: () => Promise<void>;
}

/**
 * Split from the provider so that fast refresh keeps working and so consumers import a type-only
 * module rather than pulling the provider's dependencies along with it.
 */
export const AuthContext = createContext<AuthState | null>(null);
