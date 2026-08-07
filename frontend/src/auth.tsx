import React, { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { apiLogin, Session } from './api';

type AuthContextValue = {
  session: Session | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
};

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [session, setSession] = useState<Session | null>(null);

  useEffect(() => {
    const raw = localStorage.getItem('torqmind.session');
    if (raw) {
      try {
        setSession(JSON.parse(raw));
      } catch {
        localStorage.removeItem('torqmind.session');
      }
    }
  }, []);

  const value = useMemo<AuthContextValue>(() => ({
    session,
    async login(username: string, password: string) {
      const result = await apiLogin(username, password);
      localStorage.setItem('torqmind.token', result.token);
      localStorage.setItem('torqmind.session', JSON.stringify(result));
      setSession(result);
    },
    logout() {
      localStorage.removeItem('torqmind.token');
      localStorage.removeItem('torqmind.session');
      setSession(null);
    }
  }), [session]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth deve ser usado dentro de AuthProvider');
  return ctx;
}
