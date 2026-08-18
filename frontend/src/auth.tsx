import React, { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { apiLogin, Session } from './api';

type AuthContextValue = {
  session: Session | null;
  login: (username: string, password: string) => Promise<void>;
  replaceSession: (next: Session) => void;
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

  useEffect(() => {
    const onUnauthorized = () => setSession(null);
    window.addEventListener('torqmind:unauthorized', onUnauthorized);
    return () => window.removeEventListener('torqmind:unauthorized', onUnauthorized);
  }, []);

  const value = useMemo<AuthContextValue>(() => ({
    session,
    async login(username: string, password: string) {
      const result = await apiLogin(username, password);
      persist(result);
    },
    replaceSession(next: Session) {
      persist(next);
    },
    logout() {
      localStorage.removeItem('torqmind.token');
      localStorage.removeItem('torqmind.session');
      setSession(null);
    }
  }), [session]);

  function persist(next: Session) {
    localStorage.setItem('torqmind.token', next.token);
    localStorage.setItem('torqmind.session', JSON.stringify(next));
    setSession(next);
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth deve ser usado dentro de AuthProvider');
  return ctx;
}
