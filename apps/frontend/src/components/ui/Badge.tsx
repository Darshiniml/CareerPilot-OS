import React from 'react';

interface BadgeProps {
  children: React.ReactNode;
  variant?: 'neutral' | 'active' | 'success' | 'warning' | 'danger';
}

export const Badge: React.FC<BadgeProps> = ({
  children,
  variant = 'neutral',
}) => {
  const styles = {
    neutral: 'bg-mist-gray text-slate border border-iron-gray/10',
    active: 'bg-faded-teal/20 text-jet-black border border-faded-teal/40',
    success: 'bg-emerald-500/10 text-emerald-700 border border-emerald-500/20',
    warning: 'bg-amber-500/10 text-amber-700 border border-amber-500/20',
    danger: 'bg-red-500/10 text-red-700 border border-red-500/20',
  };

  return (
    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-caption font-medium select-none ${styles[variant]}`}>
      {children}
    </span>
  );
};
