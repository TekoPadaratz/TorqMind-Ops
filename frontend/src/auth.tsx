import React, { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { apiLogin, apiLoginTotp, LoginResponse, Session } from './api';

type LoginStep = { totpRequired: true; challenge: string } | { totpRequired: false };

type AuthContextValue = {
  session: Session | null;
  login: (username: string, password: string) => Promise<LoginStep>;
  loginTotp: (challenge: string, code: string) => Promise<void>;
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
      if (result.totpRequired) {
        return { totpRequired: true as const, challenge: result.challenge ?? '' };
      }
      persist(toSession(result));
      return { totpRequired: false as const };
    },
    async loginTotp(challenge: string, code: string) {
      persist(await apiLoginTotp(challenge, code));
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

  function toSession(r: LoginResponse): Session {
    return {
      token: r.token as string,
      userId: r.userId as string,
      username: r.username as string,
      fullName: r.fullName as string,
      role: r.role as string,
      roleLabel: r.roleLabel,
      companyId: r.companyId ?? null,
      branchId: r.branchId ?? null
    };
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth deve ser usado dentro de AuthProvider');
  return ctx;
}
