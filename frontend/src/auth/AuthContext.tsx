import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';
import * as api from '../services/api';
import { clearToken, getToken, setToken } from '../services/authStorage';
import { LogEvent, logger } from '../services/logger';
import type { AuthUser } from '../types';

type AuthStatus = 'loading' | 'authenticated' | 'anonymous';

interface AuthContextValue {
  status: AuthStatus;
  user: AuthUser | null;
  login: (email: string, password: string) => Promise<void>;
  register: (name: string, email: string, password: string) => Promise<AuthUser>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('loading');
  const [user, setUser] = useState<AuthUser | null>(null);

  const logout = useCallback(() => {
    clearToken();
    setUser(null);
    setStatus('anonymous');
    logger.info({ event: LogEvent.LOGOUT });
  }, []);

  // 401 vindo de qualquer chamada -> desloga
  useEffect(() => {
    api.onUnauthorized(() => {
      logger.warn({ event: LogEvent.AUTH_SESSION_EXPIRED });
      setUser(null);
      setStatus('anonymous');
    });
  }, []);

  // hidrata a sessao no mount
  useEffect(() => {
    let cancelled = false;
    if (!getToken()) {
      setStatus('anonymous');
      return;
    }
    (async () => {
      try {
        const current = await api.me();
        if (!cancelled) {
          setUser(current);
          setStatus('authenticated');
        }
      } catch {
        if (!cancelled) {
          clearToken();
          setStatus('anonymous');
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    try {
      const { accessToken } = await api.login(email, password);
      setToken(accessToken);
      const current = await api.me();
      setUser(current);
      setStatus('authenticated');
      logger.info({ event: LogEvent.LOGIN_SUCCESS });
    } catch (err) {
      logger.warn({
        event: LogEvent.LOGIN_FAILED,
        code: err instanceof api.ApiError ? err.code : 'UNKNOWN',
      });
      throw err;
    }
  }, []);

  const register = useCallback(
    (name: string, email: string, password: string) => api.register(name, email, password),
    [],
  );

  return (
    <AuthContext.Provider value={{ status, user, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth precisa estar dentro de <AuthProvider>');
  return ctx;
}
