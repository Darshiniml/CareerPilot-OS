import React from 'react';
import { useAuthStore } from '../../store/authStore';
import { Bell, Sparkles } from 'lucide-react';

interface TopHeaderProps {
  title: string;
  description?: string;
}

export const TopHeader: React.FC<TopHeaderProps> = ({ title, description }) => {
  const user = useAuthStore((state) => state.user);

  const getInitials = () => {
    if (!user) return 'U';
    return `${user.firstName?.charAt(0) || ''}${user.lastName?.charAt(0) || ''}`.toUpperCase() || 'U';
  };

  return (
    <header className="top-header">
      <div>
        <h1 className="text-lg font-bold text-white tracking-tight font-heading flex items-center gap-2">
          {title}
        </h1>
        {description && <p className="text-xs text-slate-400 mt-0.5">{description}</p>}
      </div>

      <div className="flex items-center gap-4">
        {/* Quick System Badge */}
        <div className="hidden md:flex items-center gap-2 px-3 py-1.5 rounded-full bg-indigo-500/10 border border-indigo-500/20 text-indigo-300 text-xs font-medium">
          <Sparkles className="w-3.5 h-3.5 text-indigo-400 animate-pulse" />
          <span>AI Engine Active</span>
        </div>

        {/* Notifications Icon */}
        <button className="text-slate-400 hover:text-white p-2 rounded-xl bg-slate-800/40 hover:bg-slate-800 border border-white/5 transition-all relative">
          <Bell className="w-4 h-4" />
          <span className="absolute top-1.5 right-1.5 w-2 h-2 bg-indigo-400 rounded-full ring-2 ring-slate-900" />
        </button>

        {/* User Badge */}
        {user && (
          <div className="flex items-center gap-3 pl-3 border-l border-white/10 select-none">
            <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-indigo-500 to-purple-600 flex items-center justify-center text-xs font-bold text-white shadow-md shadow-indigo-500/20 border border-white/20">
              {getInitials()}
            </div>
            <div className="text-left hidden sm:block">
              <p className="text-xs font-bold text-slate-200 leading-tight">
                {user.firstName} {user.lastName}
              </p>
              <p className="text-[11px] text-slate-400 leading-tight mt-0.5">
                {user.email}
              </p>
            </div>
          </div>
        )}
      </div>
    </header>
  );
};
