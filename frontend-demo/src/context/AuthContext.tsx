import { createContext, useContext, useState, useCallback, useEffect, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { me, type MeInfo } from '../api/auth';

interface AuthState {
  token: string | null;
  username: string | null;
  roles: string[];
  permissions: string[];
  deptId: number | null;
  deptName?: string;
  userId: number | null;
}

interface AuthContextType extends AuthState {
  login: (token: string, username: string) => Promise<void>;
  logout: () => void;
  isAuthenticated: boolean;
  hasPermission: (code: string) => boolean;
  refreshMe: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | null>(null);

function readStored(): AuthState {
  const token = localStorage.getItem('token');
  try {
    const saved = localStorage.getItem('auth');
    if (saved) {
      const parsed = JSON.parse(saved);
      return { token, ...parsed };
    }
  } catch { /* ignore */ }
  return { token, username: null, roles: [], permissions: [], deptId: null, userId: null };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const [state, setState] = useState<AuthState>(readStored);


  const applyMe = useCallback(async (info: MeInfo) => {
    setState(prev => {
      const next = { ...prev, userId: info.userId, username: info.username, roles: info.roles, permissions: info.permissions, deptId: info.deptId ?? null, deptName: info.deptName };
      localStorage.setItem('token', prev.token || '');
      const { token: _t, ...rest } = next;
      localStorage.setItem('auth', JSON.stringify(rest));
      return next;
    });
  }, []);

  const refreshMe = useCallback(async () => {
    try {
      const info = await me();
      await applyMe(info);
    } catch { /* 401 由拦截器处理 */ }
  }, [applyMe]);

  useEffect(() => {
    if (state.token && state.permissions.length === 0) {
      refreshMe();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const login = useCallback(async (token: string, username: string) => {
    localStorage.setItem('token', token);
    setState({ token, username, roles: [], permissions: [], deptId: null, userId: null });
    try {
      const info = await me();
      await applyMe(info);
    } catch { /* ignore */ }
    navigate('/');
  }, [navigate, applyMe]);

  const logout = useCallback(() => {
    localStorage.removeItem('token');
    localStorage.removeItem('auth');
    setState({ token: null, username: null, roles: [], permissions: [], deptId: null, userId: null });
    navigate('/login');
  }, [navigate]);

  const hasPermission = useCallback((code: string) => state.permissions.includes(code), [state.permissions]);

  return (
    <AuthContext.Provider value={{ ...state, login, logout, isAuthenticated: !!state.token, hasPermission, refreshMe }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}