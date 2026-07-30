import { createContext, useContext, useState, useCallback, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';

interface AuthState {
  token: string | null;
  username: string | null;
  role: string | null;
}

interface AuthContextType extends AuthState {
  login: (token: string, username: string, role: string) => void;
  logout: () => void;
  isAuthenticated: boolean;
}

const AuthContext = createContext<AuthContextType | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const [state, setState] = useState<AuthState>({
    token: localStorage.getItem('token'),
    username: localStorage.getItem('username'),
    role: localStorage.getItem('role'),
  });

  const loginFn = useCallback((token: string, username: string, role: string) => {
    localStorage.setItem('token', token);
    localStorage.setItem('username', username);
    localStorage.setItem('role', role);
    setState({ token, username, role });
    navigate('/');
  }, [navigate]);

  const logout = useCallback(() => {
    localStorage.removeItem('token');
    localStorage.removeItem('username');
    localStorage.removeItem('role');
    setState({ token: null, username: null, role: null });
    navigate('/login');
  }, [navigate]);

  return (
    <AuthContext.Provider
      value={{
        ...state,
        login: loginFn,
        logout,
        isAuthenticated: !!state.token,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
