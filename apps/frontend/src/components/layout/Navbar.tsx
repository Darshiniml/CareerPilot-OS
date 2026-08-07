import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '../../store/authStore';

export const Navbar: React.FC = () => {
  const { user, clearAuth } = useAuthStore();
  const navigate = useNavigate();

  const handleLogout = () => {
    clearAuth();
    navigate('/login');
  };

  return (
    <nav className="bg-zinc-900/80 backdrop-blur-md border-b border-zinc-800 px-6 py-4 flex items-center justify-between sticky top-0 z-50">
      <div className="flex items-center gap-3">
        <span className="text-2xl">✈️</span>
        <span className="text-xl font-bold bg-gradient-to-r from-indigo-400 to-emerald-400 bg-clip-text text-transparent">
          CareerPilot OS
        </span>
      </div>

      <div className="flex items-center gap-6">
        {user && (
          <div className="text-right">
            <p className="text-zinc-200 text-sm font-semibold">{user.firstName} {user.lastName}</p>
            <p className="text-zinc-500 text-xs">{user.email}</p>
          </div>
        )}
        <button
          onClick={handleLogout}
          className="bg-zinc-800 hover:bg-zinc-700 text-zinc-300 hover:text-white px-4 py-2 rounded-lg text-sm font-medium transition-all border border-zinc-700/50"
        >
          Logout
        </button>
      </div>
    </nav>
  );
};
