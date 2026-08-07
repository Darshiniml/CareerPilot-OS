import { create } from 'zustand';

export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  roles: string[];
}

interface AuthState {
  user: User | null;
  accessToken: String | null;
  refreshToken: String | null;
  isAuthenticated: boolean;
  setAuth: (user: User, accessToken: string, refreshToken: string) => void;
  clearAuth: () => void;
}

export const useAuthStore = create<AuthState>((set) => {
  const storedUser = localStorage.getItem('cp_user');
  const storedAccess = localStorage.getItem('cp_access');
  const storedRefresh = localStorage.getItem('cp_refresh');

  return {
    user: storedUser ? JSON.parse(storedUser) : null,
    accessToken: storedAccess,
    refreshToken: storedRefresh,
    isAuthenticated: !!storedAccess,
    setAuth: (user, accessToken, refreshToken) => {
      localStorage.setItem('cp_user', JSON.stringify(user));
      localStorage.setItem('cp_access', accessToken);
      localStorage.setItem('cp_refresh', refreshToken);
      set({ user, accessToken, refreshToken, isAuthenticated: true });
    },
    clearAuth: () => {
      localStorage.removeItem('cp_user');
      localStorage.removeItem('cp_access');
      localStorage.removeItem('cp_refresh');
      set({ user: null, accessToken: null, refreshToken: null, isAuthenticated: false });
    },
  };
});
