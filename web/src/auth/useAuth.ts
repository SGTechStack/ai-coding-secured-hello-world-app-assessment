import { useContext } from "react";
import { AuthContext, type AuthState } from "./auth-context";

export function useAuth(): AuthState {
  const state = useContext(AuthContext);
  if (state === null) {
    // A missing provider would otherwise show up as "cannot read property of null" somewhere far
    // from the cause.
    throw new Error("useAuth must be used inside an AuthProvider");
  }
  return state;
}
