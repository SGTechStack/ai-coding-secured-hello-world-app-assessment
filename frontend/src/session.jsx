import { createContext, useCallback, useContext, useEffect, useState } from "react";
import { api } from "./api";

const SessionContext = createContext(null);

export function SessionProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    api("/api/auth/me")
      .then((account) => {
        if (active) setUser(account);
      })
      .catch(() => {
        if (active) setUser(null);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  const login = useCallback(async (username, password) => {
    const account = await api("/api/auth/login", {
      method: "POST",
      body: { username, password },
    });
    setUser(account);
    return account;
  }, []);

  const register = useCallback(async (username, email, password) => {
    return api("/api/auth/register", {
      method: "POST",
      body: { username, email, password },
    });
  }, []);

  const logout = useCallback(async () => {
    await api("/api/auth/logout", { method: "POST" });
    setUser(null);
  }, []);

  return (
    <SessionContext.Provider value={{ user, loading, login, register, logout }}>
      {children}
    </SessionContext.Provider>
  );
}

export function useSession() {
  const value = useContext(SessionContext);
  if (!value) {
    throw new Error("useSession must be used inside SessionProvider");
  }
  return value;
}
