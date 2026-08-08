import React from 'react';
import { useAuthStore } from '../../store/authStore';
import { Bell, ChevronDown } from 'lucide-react';

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
        <h1 className="text-subheading font-bold text-jet-black tracking-tight">{title}</h1>
        {description && <p className="text-caption text-slate mt-0.5">{description}</p>}
      </div>

      <div className="flex items-center gap-6">
        {/* Notifications Icon */}
        <button className="text-charcoal hover:text-jet-black p-1.5 rounded-full hover:bg-mist-gray transition-all relative">
          <Bell className="w-4 h-4" />
          <span className="absolute top-1.5 right-1.5 w-1.5 h-1.5 bg-jet-black rounded-full" />
        </button>

        {/* User Badge */}
        {user && (
          <div className="flex items-center gap-3 pl-4 border-l border-iron-gray/15 select-none">
            <div className="w-8 h-8 rounded-full bg-mist-gray flex items-center justify-center text-caption font-bold text-jet-black border border-iron-gray/10">
              {getInitials()}
            </div>
            <div className="text-left hidden sm:block">
              <p className="text-body-sm font-medium text-jet-black leading-none">
                {user.firstName} {user.lastName}
              </p>
              <p className="text-caption text-slate mt-1 leading-none">
                {user.email}
              </p>
            </div>
            <ChevronDown className="w-3.5 h-3.5 text-slate hidden sm:block" />
          </div>
        )}
      </div>
    </header>
  );
};
