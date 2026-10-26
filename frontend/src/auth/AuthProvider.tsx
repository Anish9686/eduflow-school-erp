import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { PropsWithChildren } from 'react';
import { apiFetch, ApiRequestError } from '../api/client';

const TOKEN_KEY = 'erp-portal-access-token';
const EXPIRES_KEY = 'erp-portal-access-expires';

export type CurrentUser = { id: number; username: string; fullName: string; roles: string[]; permissions: string[] };
type AuthState = { user: CurrentUser | null; status: 'loading' | 'authenticated' | 'unauthenticated'; login: (username: string, password: string) => Promise<void>; logout: () => Promise<void>; hasPermission: (permission: string) => boolean };
const AuthContext = createContext<AuthState | null>(null);

async function fetchMe(token: string): Promise<CurrentUser | null> {
  const response = await apiFetch('/api/v1/me', { headers: { Authorization: `Bearer ${token}` } });
  if (!response.ok) return null;
  return response.json() as Promise<CurrentUser>;
}

export function AuthProvider({ children }: PropsWithChildren) {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [status, setStatus] = useState<AuthState['status']>('loading');

  useEffect(() => {
    const handleUnauthorized = () => {
      try { window.sessionStorage.removeItem(TOKEN_KEY); window.sessionStorage.removeItem(EXPIRES_KEY); } catch { /* Storage is optional. */ }
      setUser(null);
      setStatus('unauthenticated');
    };
    window.addEventListener('erp:unauthorized', handleUnauthorized);
    let active = true;
    let token: string | null = null;
    try {
      token = window.sessionStorage.getItem(TOKEN_KEY);
      const expires = Number(window.sessionStorage.getItem(EXPIRES_KEY));
      if (!token || !expires || expires <= Date.now()) token = null;
    } catch { token = null; }
    if (!token) {
      try { window.sessionStorage.removeItem(TOKEN_KEY); window.sessionStorage.removeItem(EXPIRES_KEY); } catch { /* Storage is optional. */ }
      setStatus('unauthenticated');
      return () => { active = false; window.removeEventListener('erp:unauthorized', handleUnauthorized); };
    }
    void fetchMe(token).then(current => {
      if (!active) return;
      if (current) { setUser(current); setStatus('authenticated'); }
      else { handleUnauthorized(); }
    }).catch(error => {
      if (!active) return;
      if (error instanceof ApiRequestError && error.status === 401) handleUnauthorized();
      else setStatus('unauthenticated');
    });
    return () => { active = false; window.removeEventListener('erp:unauthorized', handleUnauthorized); };
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    let response: Response;
    try {
      response = await apiFetch('/api/v1/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, password }) });
    } catch (error) {
      if (error instanceof ApiRequestError && error.status === 401) throw new Error('The username or password is incorrect.');
      if (error instanceof ApiRequestError && error.status >= 500) throw new Error('Sign in is temporarily unavailable. Please try again.');
      throw error;
    }
    const result = await response.json() as { accessToken: string; expiresAt: string };
    const current = await fetchMe(result.accessToken);
    if (!current) throw new Error('Your session could not be verified. Please try again.');
    try { window.sessionStorage.setItem(TOKEN_KEY, result.accessToken); window.sessionStorage.setItem(EXPIRES_KEY, String(Date.parse(result.expiresAt))); } catch { throw new Error('This browser could not start a secure session.'); }
    window.dispatchEvent(new Event('erp:authenticated'));
    setUser(current); setStatus('authenticated');
  }, []);

  const logout = useCallback(async () => {
    const token = window.sessionStorage.getItem(TOKEN_KEY);
    try { if (token) await apiFetch('/api/v1/auth/logout', { method: 'POST' }); }
    finally { window.dispatchEvent(new Event('erp:unauthorized')); window.sessionStorage.removeItem(TOKEN_KEY); window.sessionStorage.removeItem(EXPIRES_KEY); setUser(null); setStatus('unauthenticated'); }
  }, []);

  const value = useMemo(() => ({ user, status, login, logout, hasPermission: (permission: string) => Boolean(user?.permissions.includes(permission)) }), [user, status, login, logout]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error('useAuth must be used within AuthProvider');
  return context;
}
