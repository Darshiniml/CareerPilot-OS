import React from 'react';

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'outline' | 'pill' | 'danger';
  children: React.ReactNode;
  as?: any;
}

export const Button: React.FC<ButtonProps> = ({
  variant = 'primary',
  children,
  className = '',
  as: Component = 'button',
  ...props
}) => {
  const baseStyles = 'px-4 py-2.5 rounded-xl text-sm font-semibold transition-all duration-200 select-none focus:outline-none flex items-center justify-center gap-2 disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer active:scale-95';
  
  const variants = {
    primary: 'bg-gradient-to-r from-indigo-500 via-purple-500 to-indigo-600 hover:from-indigo-400 hover:to-indigo-500 text-white shadow-lg shadow-indigo-500/25 border border-indigo-400/30 hover:shadow-indigo-500/40',
    secondary: 'bg-slate-800 hover:bg-slate-700 text-slate-100 border border-white/10 hover:border-white/20 shadow-md',
    outline: 'bg-transparent text-slate-200 hover:bg-slate-800/60 border border-slate-700 hover:border-slate-500',
    danger: 'bg-rose-500/20 hover:bg-rose-500/30 text-rose-300 border border-rose-500/30 shadow-lg shadow-rose-500/10',
    pill: 'bg-gradient-to-r from-indigo-500 to-purple-600 text-white rounded-full px-6 py-2.5 shadow-lg shadow-indigo-500/25',
  };

  return (
    <Component
      className={`${baseStyles} ${variants[variant]} ${className}`}
      {...props}
    >
      {children}
    </Component>
  );
};
