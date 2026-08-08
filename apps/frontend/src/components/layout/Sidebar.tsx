import React from 'react';
import { NavLink } from 'react-router-dom';
import { useAuthStore } from '../../store/authStore';
import {
  LayoutDashboard,
  User,
  FileText,
  Briefcase,
  Target,
  Send,
  Video,
  GraduationCap,
  Sparkles,
  BarChart3,
  Settings,
  Cpu,
  LogOut
} from 'lucide-react';

export const Sidebar: React.FC = () => {
  const clearAuth = useAuthStore((state) => state.clearAuth);

  const menuItems = [
    { name: 'Dashboard', path: '/dashboard', icon: LayoutDashboard },
    { name: 'Profile', path: '/profile', icon: User },
    { name: 'Resumes', path: '/resumes', icon: FileText },
    { name: 'Jobs', path: '/jobs', icon: Briefcase },
    { name: 'AI Matching', path: '/matching', icon: Target },
    { name: 'Applications', path: '/applications', icon: Send },
    { name: 'Interviews', path: '/interviews', icon: Video },
    { name: 'Learning', path: '/learning', icon: GraduationCap },
    { name: 'Career Copilot', path: '/copilot', icon: Sparkles },
    { name: 'Automation Agents', path: '/agents', icon: Cpu },
    { name: 'Analytics', path: '/analytics', icon: BarChart3 },
    { name: 'Settings', path: '/settings', icon: Settings },
  ];

  return (
    <aside className="w-64 bg-paper-white border-r border-iron-gray/20 flex flex-col h-screen sticky top-0 sidebar">
      {/* Brand logo */}
      <div className="h-16 flex items-center px-6 border-b border-iron-gray/10">
        <span className="text-xl font-bold tracking-tight text-jet-black flex items-center gap-2 select-none">
          ✈ CareerPilot OS
        </span>
      </div>

      {/* Nav List */}
      <nav className="flex-1 px-4 py-6 overflow-y-auto space-y-1">
        {menuItems.map((item) => (
          <NavLink
            key={item.name}
            to={item.path}
            className={({ isActive }) =>
              `flex items-center gap-3 px-3 py-2.5 rounded-lg text-body-sm font-medium transition-all select-none ${
                isActive
                  ? 'bg-mist-gray text-jet-black border-l-2 border-jet-black'
                  : 'text-slate hover:bg-mist-gray/50 hover:text-jet-black'
              }`
            }
          >
            <item.icon className="w-4 h-4" />
            {item.name}
          </NavLink>
        ))}
      </nav>

      {/* Footer / Logout */}
      <div className="p-4 border-t border-iron-gray/10">
        <button
          onClick={() => clearAuth()}
          className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-body-sm font-medium text-slate hover:bg-red-500/5 hover:text-red-600 transition-all select-none"
        >
          <LogOut className="w-4 h-4" />
          Logout
        </button>
      </div>
    </aside>
  );
};
